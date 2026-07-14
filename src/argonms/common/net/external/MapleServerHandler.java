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

import argonms.common.util.input.LittleEndianByteArrayReader;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MapleServerHandler<T extends RemoteClient> extends SimpleChannelInboundHandler<byte[]> {
	private static final Logger LOG = Logger.getLogger(MapleServerHandler.class.getName());

	private final ClientPacketProcessor<T> packetProcessor;
	private final NettyClientListener.ClientFactory<T> clientFactory;
	private final ExecutorService workerThreadPool;

	public MapleServerHandler(ClientPacketProcessor<T> packetProcessor, NettyClientListener.ClientFactory<T> clientFactory, ExecutorService workerThreadPool) {
		super(false);
		this.packetProcessor = packetProcessor;
		this.clientFactory = clientFactory;
		this.workerThreadPool = workerThreadPool;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.channel().config().setAutoRead(false);
		T client = clientFactory.newInstance();
		ClientSession<T> session = new ClientSession<>(ctx.channel(), client);
		client.setSession(session);
		session.activate();
		NettyClientListener.setSession(ctx.channel(), session);
		NettyClientListener.initializeCrypto(ctx.channel(), session.getRecvIv(), session.getSendIv());
		ctx.writeAndFlush(session.makeInitPacket(ctx.alloc())).addListener(future -> {
			if (future.isSuccess()) {
				ctx.channel().config().setAutoRead(true);
				ctx.read();
			} else {
				session.close(future.cause() != null ? future.cause().getMessage() : "Failed to send init packet");
			}
		});
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
		ClientSession<T> session = NettyClientListener.getSession(ctx.channel());
		if (session == null || session.getClient() == null) {
			return;
		}

		session.touch();
		session.readEnqueued();
		workerThreadPool.submit(() -> {
			try {
				packetProcessor.process(new LittleEndianByteArrayReader(msg), session.getClient());
			} catch (Throwable ex) {
				LOG.log(Level.WARNING, "Uncaught exception while processing packet from client " + session.getAccountName() + " (" + session.getAddress() + ")", ex);
			} finally {
				session.readDequeued();
			}
		});
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		ClientSession<T> session = NettyClientListener.getSession(ctx.channel());
		if (session != null) {
			session.close("EOF received");
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		ClientSession<T> session = NettyClientListener.getSession(ctx.channel());
		if (session != null) {
			LOG.log(Level.WARNING, "Uncaught exception while handling client " + session.getAccountName() + " (" + session.getAddress() + ")", cause);
			session.close(cause.getMessage());
		} else {
			LOG.log(Level.WARNING, "Uncaught exception before session initialization", cause);
			ctx.close();
		}
	}
}
