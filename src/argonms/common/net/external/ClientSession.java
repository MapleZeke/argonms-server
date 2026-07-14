/*
 * ArgonMS MapleStory server emulator written in Java
 * Copyright (C) 2011-2013  GoldenKevin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package argonms.common.net.external;

import argonms.common.GlobalConstants;
import argonms.common.net.Session;
import argonms.common.util.Rng;
import argonms.common.util.Scheduler;
import argonms.common.util.output.LittleEndianByteArrayWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a Netty {@link Channel} and manages the lifecycle of a single
 * external client connection, including keep-alive (ping/pong), idle
 * detection, and session-level crypto IVs.
 */
public class ClientSession<T extends RemoteClient> implements Session {
	public static final byte CLIENT_DISTRIBUTION_JAPAN = 3;
	public static final byte CLIENT_DISTRIBUTION_TEST = 5;
	public static final byte CLIENT_DISTRIBUTION_SEA = 7;
	public static final byte CLIENT_DISTRIBUTION_GLOBAL = 8;
	public static final byte CLIENT_DISTRIBUTION_BRAZIL = 9;

	private static final Logger LOG = Logger.getLogger(ClientSession.class.getName());
	private static final int INIT_HEADER_LENGTH = 2;
	private static final int IDLE_TIME = 15000; //in milliseconds
	private static final int TIMEOUT = 15000; //in milliseconds

	private final Channel channel;
	private final AtomicBoolean closeEventsTriggered;
	private T client;
	private final AtomicInteger queuedReads;
	private volatile Runnable emptyReadQueueHandler;

	private final KeepAliveTask heartbeatTask;
	private final Runnable idleTask = this::startPingTask;
	private ScheduledFuture<?> idleTaskFuture;

	private byte[] recvIv;
	private byte[] sendIv;

	/* package-private */ ClientSession(Channel channel, T client) {
		this.channel = channel;
		this.closeEventsTriggered = new AtomicBoolean(false);
		this.heartbeatTask = new KeepAliveTask();
		this.queuedReads = new AtomicInteger(0);
		this.client = client;

		Random generator = Rng.getGenerator();
		recvIv = new byte[4];
		sendIv = new byte[4];
		generator.nextBytes(recvIv);
		generator.nextBytes(sendIv);
	}

	public T getClient() {
		return client;
	}

	public void removeClient() {
		this.client = null;
	}

	@Override
	public SocketAddress getAddress() {
		return channel.remoteAddress();
	}

	public String getAccountName() {
		return getClient() != null ? getClient().getAccountName() : null;
	}

	@Override
	public void send(byte[] message) {
		channel.writeAndFlush(message);
	}

	public void readEnqueued() {
		queuedReads.incrementAndGet();
	}

	public void readDequeued() {
		if (queuedReads.decrementAndGet() == 0 && emptyReadQueueHandler != null) {
			emptyReadQueueHandler.run();
		}
	}

	public int getQueuedReads() {
		return queuedReads.get();
	}

	public void setEmptyReadQueueHandler(Runnable runnable) {
		emptyReadQueueHandler = runnable;
	}

	public void receivedPong() {
		heartbeatTask.receivedPong();
	}

	private void startPingTask() {
		heartbeatTask.waitForPong();
		heartbeatTask.sendPing();
	}

	private void stopPingTask() {
		heartbeatTask.stop();
	}

	@Override
	public boolean close(String reason) {
		if (closeEventsTriggered.compareAndSet(false, true)) {
			channel.close();
			stopPingTask();
			if (idleTaskFuture != null) {
				idleTaskFuture.cancel(false);
			}

			LOG.log(Level.FINE, "Client {0} ({1}) disconnected: {2}", new Object[]{getAccountName(), getAddress(), reason});
			if (client != null) {
				client.disconnected();
			}
			return true;
		}
		return false;
	}

	/* package-private */ void sendInitPacket() {
		activate();
	}

	/* package-private */ ByteBuf makeInitPacket(ByteBufAllocator allocator) {
		byte[] packet = makeInitPacketBytes();
		ByteBuf buf = allocator.buffer(packet.length);
		buf.writeBytes(packet);
		return buf;
	}

	/* package-private */ void activate() {
		rescheduleIdleTask();
	}

	/* package-private */ void touch() {
		rescheduleIdleTask();
	}

	/* package-private */ byte[] getRecvIv() {
		return recvIv;
	}

	/* package-private */ byte[] getSendIv() {
		return sendIv;
	}

	/* package-private */ byte[] advanceRecvIv() {
		byte[] iv = recvIv;
		recvIv = ClientEncryption.nextIv(iv);
		return iv;
	}

	/* package-private */ byte[] advanceSendIv() {
		byte[] iv = sendIv;
		sendIv = ClientEncryption.nextIv(iv);
		return iv;
	}

	private byte[] makeInitPacketBytes() {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter(13);
		lew.writeShort(GlobalConstants.MAPLE_VERSION);
		lew.writeLengthPrefixedString("");
		lew.writeBytes(recvIv);
		lew.writeBytes(sendIv);
		lew.writeByte(CLIENT_DISTRIBUTION_GLOBAL);
		byte[] body = lew.getBytes();

		ByteBuffer buf = ByteBuffer.allocate(INIT_HEADER_LENGTH + body.length);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) body.length);
		buf.put(body);
		return buf.array();
	}

	private void rescheduleIdleTask() {
		if (idleTaskFuture != null) {
			idleTaskFuture.cancel(false);
		}
		idleTaskFuture = Scheduler.getWheelTimer().runAfterDelay(idleTask, IDLE_TIME);
	}

	private static byte[] pingMessage() {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter(2);
		lew.writeShort(ClientSendOps.PING);
		return lew.getBytes();
	}

	private class KeepAliveTask implements Runnable {
		private final AtomicReference<ScheduledFuture<?>> future;

		public KeepAliveTask() {
			future = new AtomicReference<>(null);
		}

		public void sendPing() {
			send(pingMessage());
		}

		public void waitForPong() {
			future.set(Scheduler.getWheelTimer().runAfterDelay(this, TIMEOUT));
		}

		@Override
		public void run() {
			close("Timed out after " + TIMEOUT + " milliseconds");
		}

		public void receivedPong() {
			stop();
		}

		public void stop() {
			ScheduledFuture<?> old = future.getAndSet(null);
			if (old != null) {
				old.cancel(false);
			}
		}
	}
}
