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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MapleSessionHandler<T extends RemoteClient> extends ChannelInboundHandlerAdapter {
	private static final Logger LOG = Logger.getLogger(MapleSessionHandler.class.getName());

	public static final AttributeKey<RemoteClient> CLIENT_KEY = AttributeKey.valueOf("ArgonMapleClient");

	private final ClientPacketProcessor<T> packetProcessor;
	private final ClientListener.ClientFactory<T> clientFactory;
	private final ExecutorService packetExecutor;

	public MapleSessionHandler(ClientPacketProcessor<T> packetProcessor, ClientListener.ClientFactory<T> clientFactory) {
		this(packetProcessor, clientFactory, Executors.newVirtualThreadPerTaskExecutor());
	}

	public MapleSessionHandler(ClientPacketProcessor<T> packetProcessor, ClientListener.ClientFactory<T> clientFactory, ExecutorService packetExecutor) {
		this.packetProcessor = Objects.requireNonNull(packetProcessor, "packetProcessor");
		this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
		this.packetExecutor = Objects.requireNonNull(packetExecutor, "packetExecutor");
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.channel().config().setAutoRead(false);

		var client = clientFactory.newInstance();
		var session = new ClientSession<>(ctx.channel(), client);
		client.setSession(session);
		session.activate();

		NettyClientListener.setSession(ctx.channel(), session);
		NettyClientListener.initializeCrypto(ctx.channel(), session.getRecvIv(), session.getSendIv());
		ctx.channel().attr(CLIENT_KEY).set(client);

		ctx.writeAndFlush(session.makeInitPacket(ctx.alloc())).addListener(future -> {
			if (future.isSuccess()) {
				ctx.channel().config().setAutoRead(true);
				ctx.read();
			} else {
				session.close(future.cause() != null ? future.cause().getMessage() : "Failed to send init packet");
				clearChannelState(ctx);
			}
		});
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (!(msg instanceof byte[] payload)) {
			ctx.fireChannelRead(msg);
			return;
		}

		var session = NettyClientListener.<T>getSession(ctx.channel());
		if (session == null) {
			return;
		}

		var channelClient = ctx.channel().attr(CLIENT_KEY).get();
		var client = session.getClient();
		if (channelClient == null || client == null) {
			return;
		}

		session.touch();
		session.readEnqueued();
		packetExecutor.submit(() -> {
			try {
				packetProcessor.process(new LittleEndianByteArrayReader(payload), client);
			} catch (Throwable ex) {
				LOG.log(Level.WARNING, "Uncaught exception while processing packet from client " + session.getAccountName() + " (" + session.getAddress() + ")", ex);
			} finally {
				session.readDequeued();
			}
		});
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		var session = NettyClientListener.<T>getSession(ctx.channel());
		if (session != null) {
			session.close("EOF received");
		}
		clearChannelState(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		var session = NettyClientListener.<T>getSession(ctx.channel());
		if (session != null) {
			var socketDrop = cause instanceof IOException;
			var level = socketDrop ? Level.FINE : Level.WARNING;
			LOG.log(level, "Connection failure for client " + session.getAccountName() + " (" + session.getAddress() + ")", cause);
			session.close(cause.getMessage());
		} else {
			LOG.log(Level.WARNING, "Uncaught exception before session initialization", cause);
		}
		clearChannelState(ctx);
		ctx.close();
	}

	private static void clearChannelState(ChannelHandlerContext ctx) {
		ctx.channel().attr(CLIENT_KEY).set(null);
		ctx.channel().attr(NettyClientListener.SESSION_KEY).set(null);
		ctx.channel().attr(NettyClientListener.RECV_IV_KEY).set(null);
		ctx.channel().attr(NettyClientListener.SEND_IV_KEY).set(null);
	}
}
