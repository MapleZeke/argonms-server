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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty inbound handler that rate-limits packets per session (anti-flood).
 *
 * <p>Uses a sliding-window approach: if more than {@link #maxPacketsPerWindow}
 * packets are received within {@link #windowMillis} milliseconds, the
 * connection is closed.
 *
 * <p>This handler should be placed in the pipeline <em>after</em> the decoder
 * and <em>before</em> the session handler.
 */
public final class PacketRateLimiter extends ChannelInboundHandlerAdapter {
	private static final Logger LOG = Logger.getLogger(PacketRateLimiter.class.getName());

	private final int maxPacketsPerWindow;
	private final long windowMillis;

	private int packetCount;
	private long windowStart;

	/**
	 * Creates a rate limiter with the specified parameters.
	 *
	 * @param maxPacketsPerWindow maximum packets allowed per window
	 * @param windowMillis        the window duration in milliseconds
	 */
	public PacketRateLimiter(int maxPacketsPerWindow, long windowMillis) {
		this.maxPacketsPerWindow = maxPacketsPerWindow;
		this.windowMillis = windowMillis;
		this.windowStart = System.currentTimeMillis();
	}

	/**
	 * Creates a rate limiter with default settings: 200 packets per 1000ms.
	 */
	public PacketRateLimiter() {
		this(
			Integer.getInteger("argonms.net.rateLimit.maxPackets", 200),
			Long.getLong("argonms.net.rateLimit.windowMs", 1000L)
		);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		long now = System.currentTimeMillis();
		if (now - windowStart >= windowMillis) {
			// Reset window
			windowStart = now;
			packetCount = 0;
		}
		packetCount++;

		if (packetCount > maxPacketsPerWindow) {
			LOG.log(Level.WARNING, "[{0}] Rate limit exceeded ({1} packets in {2}ms), closing connection",
					new Object[]{ctx.channel().id().asShortText(), packetCount, windowMillis});
			ClientSession<?> session = NettyClientListener.getSession(ctx.channel());
			if (session != null) {
				session.close("Rate limit exceeded");
			} else {
				ctx.close();
			}
			return;
		}

		ctx.fireChannelRead(msg);
	}
}
