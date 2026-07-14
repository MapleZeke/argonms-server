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

/**
 * Netty {@link ChannelInboundHandlerAdapter} that forms the final stage of the
 * external-client pipeline and is responsible for the
 * <strong>session lifecycle</strong> only — it does <em>not</em> decrypt or
 * frame packets.
 *
 * <h2>Pipeline contract (item 1 – protocol separation)</h2>
 * <pre>
 *   [wire bytes]
 *       ↓
 *   {@link MaplePacketDecoder} – frames &amp; decrypts inbound bytes → {@code byte[]}
 *       ↓
 *   {@link MaplePacketEncoder} – encrypts outbound {@code byte[]} → wire frames
 *       ↓
 *   MapleSessionHandler      – lifecycle bridge:
 *                               • {@link #channelActive}   → create {@link ClientSession},
 *                                 bind {@link MapleClientContext} attr, send v62 Hello
 *                               • {@link #channelRead}     → dispatch {@code byte[]} payload
 *                                 to {@link ClientPacketProcessor} on virtual thread
 *                               • {@link #channelInactive} → tear down session
 *                               • {@link #exceptionCaught} → log + close
 * </pre>
 *
 * <h2>Correlation IDs (item 6)</h2>
 * Every log record emitted by this handler is prefixed with the Netty channel's
 * short ID ({@code [channelId]}) obtained from
 * {@link io.netty.channel.ChannelId#asShortText()}.  This token can be used to
 * correlate network events, packet-processor events, and DB persistence events
 * across log files.
 *
 * <h2>Backpressure (item 3)</h2>
 * {@link #channelRead} passes the current executor queue depth to
 * {@link SessionMetrics#onPacketArrived}, which logs a {@code WARNING} when
 * the depth exceeds {@link SessionMetrics#BACKPRESSURE_THRESHOLD}.
 *
 * @param <T> the server-module client type (e.g. {@code LoginClient})
 */
public final class MapleSessionHandler<T extends RemoteClient> extends ChannelInboundHandlerAdapter {
	private static final Logger LOG = Logger.getLogger(MapleSessionHandler.class.getName());

	/** Channel attribute key for the bound {@link MapleClientContext}. */
	public static final AttributeKey<RemoteClient> CLIENT_KEY = AttributeKey.valueOf("ArgonMapleClient");

	/** Channel attribute key for the per-session {@link SessionMetrics}. */
	public static final AttributeKey<SessionMetrics> METRICS_KEY = AttributeKey.valueOf("ArgonSessionMetrics");

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
		var cid = channelId(ctx);
		ctx.channel().config().setAutoRead(false);

		var client = clientFactory.newInstance();
		var session = new ClientSession<>(ctx.channel(), client);
		client.setSession(session);
		session.activate();

		var metrics = SessionMetrics.onConnect();
		NettyClientListener.setSession(ctx.channel(), session);
		NettyClientListener.initializeCrypto(ctx.channel(), session.getRecvIv(), session.getSendIv());
		ctx.channel().attr(CLIENT_KEY).set(client);
		ctx.channel().attr(METRICS_KEY).set(metrics);

		LOG.log(Level.FINE, "[{0}] Channel active, sending handshake", cid);
		ctx.writeAndFlush(session.makeInitPacket(ctx.alloc())).addListener(future -> {
			if (future.isSuccess()) {
				LOG.log(Level.FINE, "[{0}] Handshake sent successfully", cid);
				ctx.channel().config().setAutoRead(true);
				ctx.read();
			} else {
				var reason = future.cause() != null ? future.cause().getMessage() : "Failed to send init packet";
				LOG.log(Level.WARNING, "[{0}] Handshake write failed: {1}", new Object[]{cid, reason});
				session.close(reason);
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

		var metrics = ctx.channel().attr(METRICS_KEY).get();
		session.touch();
		session.readEnqueued();
		if (metrics != null) {
			metrics.onPacketArrived(session.getQueuedReads());
		}

		var cid = channelId(ctx);
		packetExecutor.submit(() -> {
			try {
				packetProcessor.process(new LittleEndianByteArrayReader(payload), client);
			} catch (Throwable ex) {
				LOG.log(Level.WARNING, "[" + cid + "] Uncaught exception while processing packet from "
						+ session.getAccountName() + " (" + session.getAddress() + ")", ex);
			} finally {
				session.readDequeued();
			}
		});
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		var cid = channelId(ctx);
		var session = NettyClientListener.<T>getSession(ctx.channel());
		if (session != null) {
			LOG.log(Level.FINE, "[{0}] Channel inactive for {1} ({2}), closing session",
					new Object[]{cid, session.getAccountName(), session.getAddress()});
			session.close("EOF received");
		} else {
			LOG.log(Level.FINE, "[{0}] Channel inactive (no session)", cid);
		}
		var metrics = ctx.channel().attr(METRICS_KEY).get();
		if (metrics != null) {
			metrics.onDisconnect();
		}
		clearChannelState(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		var cid = channelId(ctx);
		var session = NettyClientListener.<T>getSession(ctx.channel());
		if (session != null) {
			var socketDrop = cause instanceof IOException;
			var level = socketDrop ? Level.FINE : Level.WARNING;
			LOG.log(level, "[" + cid + "] Connection failure for " + session.getAccountName()
					+ " (" + session.getAddress() + ")", cause);
			session.close(cause.getMessage());
		} else {
			LOG.log(Level.WARNING, "[" + cid + "] Exception before session initialization", cause);
		}
		clearChannelState(ctx);
		ctx.close();
	}

	private static void clearChannelState(ChannelHandlerContext ctx) {
		ctx.channel().attr(CLIENT_KEY).set(null);
		ctx.channel().attr(METRICS_KEY).set(null);
		ctx.channel().attr(NettyClientListener.SESSION_KEY).set(null);
		ctx.channel().attr(NettyClientListener.RECV_IV_KEY).set(null);
		ctx.channel().attr(NettyClientListener.SEND_IV_KEY).set(null);
	}

	/** Returns the Netty channel short ID used as a per-channel correlation token. */
	private static String channelId(ChannelHandlerContext ctx) {
		return ctx.channel().id().asShortText();
	}
}
