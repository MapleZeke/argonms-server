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

import argonms.common.net.SessionCreator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NettyClientListener<T extends RemoteClient> implements SessionCreator {
	static final AttributeKey<ClientSession<?>> SESSION_KEY = AttributeKey.valueOf("argonms.external.session");
	static final AttributeKey<byte[]> RECV_IV_KEY = AttributeKey.valueOf("argonms.external.recvIv");
	static final AttributeKey<byte[]> SEND_IV_KEY = AttributeKey.valueOf("argonms.external.sendIv");

	private static final Logger LOG = Logger.getLogger(NettyClientListener.class.getName());
	private static final String VIRTUAL_THREADS_PROPERTY = "argonms.virtualThreads";

	private final ClientPacketProcessor<T> packetProcessor;
	private final ClientListener.ClientFactory<T> clientFactory;
	private final ExecutorService workerThreadPool;
	private final AtomicBoolean closeEventsTriggered;
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private Channel listener;

	public NettyClientListener(ClientPacketProcessor<T> packetProcessor, ClientListener.ClientFactory<T> clientFactory) {
		this.packetProcessor = packetProcessor;
		this.clientFactory = clientFactory;
		this.closeEventsTriggered = new AtomicBoolean(false);
		// This repository targets Java 21, so virtual threads are available by
		// default; setting argonms.virtualThreads=false falls back to the legacy
		// platform-thread worker pool when needed.
		boolean useVirtualThreads = Boolean.parseBoolean(System.getProperty(VIRTUAL_THREADS_PROPERTY, "true"));
		int workerThreads = workerThreadCount();
		this.workerThreadPool = useVirtualThreads ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(
				workerThreads,
				new ThreadFactory() {
					private final ThreadGroup group = Thread.currentThread().getThreadGroup();
					private final AtomicInteger threadNumber = new AtomicInteger(1);

					@Override
					public Thread newThread(Runnable r) {
						Thread t = new Thread(group, r, "external-worker-pool-thread-" + threadNumber.getAndIncrement(), 0);
						if (t.isDaemon()) {
							t.setDaemon(false);
						}
						if (t.getPriority() != Thread.NORM_PRIORITY) {
							t.setPriority(Thread.NORM_PRIORITY);
						}
						return t;
					}
				});
		LOG.log(Level.INFO, "External client listener using {0}", useVirtualThreads ? "virtual-thread worker executor" : "platform-thread worker pool");
	}

	public boolean bind(int port) {
		boolean useEpoll = Epoll.isAvailable();
		int workerThreads = workerThreadCount();
		bossGroup = useEpoll ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
		workerGroup = useEpoll ? new EpollEventLoopGroup(workerThreads) : new NioEventLoopGroup(workerThreads);
		try {
			ServerBootstrap bootstrap = new ServerBootstrap()
					.group(bossGroup, workerGroup)
					.channel(useEpoll ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
					.option(ChannelOption.SO_BACKLOG, 128)
					.childOption(ChannelOption.TCP_NODELAY, true)
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) {
							ch.pipeline().addLast(new MaplePacketDecoder());
							ch.pipeline().addLast(new MaplePacketEncoder());
							ch.pipeline().addLast(new MapleServerHandler<>(packetProcessor, clientFactory, workerThreadPool));
						}
					});
			listener = bootstrap.bind(port).syncUninterruptibly().channel();
			LOG.log(Level.INFO, "Listening on port {0}", port);
			return true;
		} catch (RuntimeException ex) {
			close(ex.getMessage(), ex);
			LOG.log(Level.SEVERE, "Could not bind on port " + port, ex);
			return false;
		}
	}

	public void close(String reason, Throwable reasonExc) {
		if (closeEventsTriggered.compareAndSet(false, true)) {
			if (listener != null) {
				listener.close().syncUninterruptibly();
			}
			if (bossGroup != null) {
				bossGroup.shutdownGracefully().syncUninterruptibly();
			}
			if (workerGroup != null) {
				workerGroup.shutdownGracefully().syncUninterruptibly();
			}
			workerThreadPool.shutdown();
			if (reasonExc == null) {
				LOG.log(Level.FINE, "External facing selector closed: {0}", reason);
			} else {
				LOG.log(Level.FINE, "External facing selector closed: " + reason, reasonExc);
			}
		}
	}

	@SuppressWarnings("unchecked")
	static <T extends RemoteClient> ClientSession<T> getSession(Channel channel) {
		return (ClientSession<T>) channel.attr(SESSION_KEY).get();
	}

	static void setSession(Channel channel, ClientSession<?> session) {
		channel.attr(SESSION_KEY).set(session);
	}

	static void initializeCrypto(Channel channel, byte[] recvIv, byte[] sendIv) {
		Objects.requireNonNull(recvIv, "recvIv");
		Objects.requireNonNull(sendIv, "sendIv");
		channel.attr(RECV_IV_KEY).set(Arrays.copyOf(recvIv, recvIv.length));
		channel.attr(SEND_IV_KEY).set(Arrays.copyOf(sendIv, sendIv.length));
	}

	static byte[] getRecvIv(Channel channel) {
		return channel.attr(RECV_IV_KEY).get();
	}

	static byte[] advanceRecvIv(Channel channel) {
		byte[] iv = channel.attr(RECV_IV_KEY).get();
		if (iv != null) {
			channel.attr(RECV_IV_KEY).set(ClientEncryption.nextIv(iv));
		}
		return iv;
	}

	static byte[] advanceSendIv(Channel channel) {
		byte[] iv = channel.attr(SEND_IV_KEY).get();
		if (iv != null) {
			channel.attr(SEND_IV_KEY).set(ClientEncryption.nextIv(iv));
		}
		return iv;
	}

	private static int workerThreadCount() {
		return Runtime.getRuntime().availableProcessors() * 2;
	}
}
