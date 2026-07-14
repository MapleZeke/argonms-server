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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight, zero-dependency telemetry for external client sessions.
 *
 * <p>Tracks both per-session state and global aggregate counters using
 * lock-free JDK primitives ({@link AtomicLong}, {@link LongAdder}). No
 * external metrics framework is required; values are reported through the
 * standard JUL {@link Logger} when thresholds are crossed or sessions close.
 *
 * <h2>Backpressure detection (item 3)</h2>
 * {@link #onPacketArrived(int)} checks the current executor queue depth and
 * logs a {@code WARNING} when it exceeds {@link #BACKPRESSURE_THRESHOLD},
 * giving an early signal of EventLoop starvation before packets start
 * piling up.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In MapleSessionHandler.channelActive:
 * SessionMetrics metrics = SessionMetrics.onConnect();
 * ctx.channel().attr(METRICS_KEY).set(metrics);
 *
 * // In MapleSessionHandler.channelRead (before submitting to executor):
 * metrics.onPacketArrived(session.getQueuedReads());
 *
 * // In MapleSessionHandler.channelInactive:
 * metrics.onDisconnect();
 * }</pre>
 */
public final class SessionMetrics {

	/** Queue-depth threshold above which a backpressure warning is emitted. */
	public static final int BACKPRESSURE_THRESHOLD = 50;

	private static final Logger LOG = Logger.getLogger(SessionMetrics.class.getName());

	// -----------------------------------------------------------------------
	// Global counters — visible across all listener instances
	// -----------------------------------------------------------------------

	private static final AtomicLong ACTIVE_SESSIONS = new AtomicLong(0);
	private static final LongAdder TOTAL_PACKETS_RECEIVED = new LongAdder();

	// -----------------------------------------------------------------------
	// Per-session state
	// -----------------------------------------------------------------------

	private final long connectedAtNanos = System.nanoTime();
	private final LongAdder packetsReceived = new LongAdder();

	private SessionMetrics() {
		// Use SessionMetrics.onConnect() as the factory.
	}

	// -----------------------------------------------------------------------
	// Lifecycle hooks
	// -----------------------------------------------------------------------

	/**
	 * Called when a new channel becomes active. Increments the global active
	 * session count and returns a fresh per-session {@code SessionMetrics}
	 * instance.
	 *
	 * @return a new, initialised {@code SessionMetrics} bound to the channel
	 */
	public static SessionMetrics onConnect() {
		long active = ACTIVE_SESSIONS.incrementAndGet();
		LOG.log(Level.FINE, "Client connected; activeSessions={0}", active);
		return new SessionMetrics();
	}

	/**
	 * Called when the channel becomes inactive (clean disconnect or error).
	 * Decrements the global active session count and logs session statistics.
	 */
	public void onDisconnect() {
		long active = ACTIVE_SESSIONS.decrementAndGet();
		long lifetimeMs = (System.nanoTime() - connectedAtNanos) / 1_000_000L;
		LOG.log(Level.FINE, "Client disconnected; activeSessions={0} lifetimeMs={1} sessionPackets={2}",
				new Object[]{active, lifetimeMs, packetsReceived.sum()});
	}

	/**
	 * Called when a decrypted packet arrives in
	 * {@link MapleSessionHandler#channelRead}.
	 *
	 * <p>Increments global and per-session packet counters. When
	 * {@code currentQueueDepth} exceeds {@link #BACKPRESSURE_THRESHOLD}, a
	 * {@code WARNING} is logged to alert operators of potential EventLoop
	 * starvation.
	 *
	 * @param currentQueueDepth the number of packets currently queued for
	 *                          processing by the virtual-thread executor for
	 *                          this session (from
	 *                          {@link ClientSession#getQueuedReads()})
	 */
	public void onPacketArrived(int currentQueueDepth) {
		TOTAL_PACKETS_RECEIVED.increment();
		packetsReceived.increment();
		if (currentQueueDepth > BACKPRESSURE_THRESHOLD) {
			LOG.log(Level.WARNING,
					"Backpressure: session queue depth {0} exceeds threshold {1}",
					new Object[]{currentQueueDepth, BACKPRESSURE_THRESHOLD});
		}
	}

	// -----------------------------------------------------------------------
	// Accessors (useful for JMX / diagnostics endpoints)
	// -----------------------------------------------------------------------

	/** Returns the count of currently active sessions across all listeners. */
	public static long getActiveSessions() {
		return ACTIVE_SESSIONS.get();
	}

	/** Returns the total number of packets received since JVM startup. */
	public static long getTotalPacketsReceived() {
		return TOTAL_PACKETS_RECEIVED.sum();
	}

	/** Returns the number of packets received during this session's lifetime. */
	public long getSessionPacketsReceived() {
		return packetsReceived.sum();
	}
}
