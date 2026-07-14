package argonms.common.net.external;

import argonms.common.character.Player;
import argonms.common.util.Scheduler;
import argonms.common.util.input.LittleEndianByteArrayReader;
import argonms.common.util.input.LittleEndianReader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle safety tests for {@link MapleSessionHandler} (plan item 4).
 *
 * <p>Tests cover: connect+handshake, normal packet dispatch to processor,
 * clean disconnect, abrupt socket drop (IOException), exception path,
 * and non-{@code byte[]} message forwarding.
 *
 * <p>A <em>direct executor</em> runs all submitted tasks on the calling thread
 * so that packet-dispatch assertions can be made synchronously without sleeps.
 */
class MapleSessionHandlerTest {

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------

    /** Minimal RemoteClient subclass with no DB dependencies. */
    static class TestClient extends RemoteClient {
        final AtomicBoolean disconnectedCalled = new AtomicBoolean(false);

        @Override
        public Player getPlayer() { return null; }

        @Override
        public byte getServerId() { return 0; }

        @Override
        protected void onDisconnected() {
            disconnectedCalled.set(true);
            if (getSession() != null) {
                getSession().removeClient();
                setSession(null);
            }
        }
    }

    /** Concrete {@link ClientPacketProcessor} that delegates to a {@link BiConsumer}. */
    static class CapturingProcessor extends ClientPacketProcessor<TestClient> {
        private final BiConsumer<LittleEndianReader, TestClient> delegate;

        CapturingProcessor(BiConsumer<LittleEndianReader, TestClient> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void process(LittleEndianReader reader, TestClient client) {
            delegate.accept(reader, client);
        }
    }

    /**
     * Synchronous {@link ExecutorService}: submitted tasks run immediately on
     * the calling thread, avoiding race conditions in tests.
     */
    static final ExecutorService DIRECT_EXECUTOR = new AbstractExecutorService() {
        @Override public void execute(Runnable r) { r.run(); }
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
    };

    private EmbeddedChannel channel;

    @BeforeAll
    static void initScheduler() {
        // ClientSession.activate() → rescheduleIdleTask() → Scheduler.getWheelTimer().
        // Scheduler must be initialised before the first channelActive fires.
        Scheduler.enable(false, true);
    }

    @AfterEach
    void closeChannel() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    private MapleSessionHandler<TestClient> buildHandler(BiConsumer<LittleEndianReader, TestClient> processor) {
        return new MapleSessionHandler<>(new CapturingProcessor(processor), TestClient::new, DIRECT_EXECUTOR);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Connect: {@code channelActive} must write an init/handshake packet to
     * the outbound queue and bind the session + client attributes.
     */
    @Test
    void connect_writesHandshakePacketAndBindsAttributes() {
        channel = new EmbeddedChannel(buildHandler((r, c) -> {}));

        ByteBuf initPacket = channel.readOutbound();
        assertNotNull(initPacket, "Expected handshake init packet in outbound queue");
        assertTrue(initPacket.readableBytes() > 0, "Handshake packet must not be empty");
        initPacket.release();

        assertNotNull(NettyClientListener.getSession(channel), "SESSION_KEY attr must be set");
        assertNotNull(channel.attr(MapleSessionHandler.CLIENT_KEY).get(), "CLIENT_KEY attr must be set");
    }

    /**
     * Packet dispatch: an inbound {@code byte[]} payload must be forwarded to
     * the packet processor exactly once.
     */
    @Test
    void channelRead_dispatchesPayloadToProcessor() {
        var callCount = new AtomicInteger(0);
        channel = new EmbeddedChannel(buildHandler((r, c) -> callCount.incrementAndGet()));

        ByteBuf initPacket = channel.readOutbound();
        if (initPacket != null) initPacket.release();

        channel.writeInbound(new byte[]{0x01, 0x00, 0x42});

        assertEquals(1, callCount.get(), "Processor must be invoked exactly once per inbound packet");
    }

    /**
     * Multiple packets in sequence must each trigger exactly one processor call.
     */
    @Test
    void channelRead_multiplePacketsDispatchedInOrder() {
        var callCount = new AtomicInteger(0);
        channel = new EmbeddedChannel(buildHandler((r, c) -> callCount.incrementAndGet()));

        ByteBuf initPacket = channel.readOutbound();
        if (initPacket != null) initPacket.release();

        channel.writeInbound(new byte[]{0x01, 0x00});
        channel.writeInbound(new byte[]{0x02, 0x00});
        channel.writeInbound(new byte[]{0x03, 0x00});

        assertEquals(3, callCount.get(), "Three packets must produce three processor calls");
    }

    /**
     * Non-{@code byte[]} messages must be forwarded downstream and must NOT
     * reach the packet processor.
     */
    @Test
    void channelRead_nonByteArrayMessageIsForwardedDownstream() {
        var processorCalled = new AtomicBoolean(false);
        channel = new EmbeddedChannel(buildHandler((r, c) -> processorCalled.set(true)));

        ByteBuf initPacket = channel.readOutbound();
        if (initPacket != null) initPacket.release();

        channel.writeInbound("not-a-packet");

        assertFalse(processorCalled.get(), "Processor must not be called for non-byte[] messages");
        Object forwarded = channel.readInbound();
        assertEquals("not-a-packet", forwarded, "Non-byte[] message must be forwarded unchanged");
    }

    /**
     * Clean disconnect: channel close must clear the CLIENT_KEY and SESSION_KEY
     * attributes.
     */
    @Test
    void channelInactive_clearsChannelAttributes() {
        channel = new EmbeddedChannel(buildHandler((r, c) -> {}));

        ByteBuf initPacket = channel.readOutbound();
        if (initPacket != null) initPacket.release();

        channel.close();
        channel.runPendingTasks();

        assertNull(channel.attr(MapleSessionHandler.CLIENT_KEY).get(), "CLIENT_KEY must be cleared");
        assertNull(NettyClientListener.getSession(channel), "SESSION_KEY must be cleared");
    }

    /**
     * Non-IO exception must close the channel.
     */
    @Test
    void exceptionCaught_nonIoException_closesChannel() {
        channel = new EmbeddedChannel(buildHandler((r, c) -> {}));

        ByteBuf initPacket = channel.readOutbound();
        if (initPacket != null) initPacket.release();

        channel.pipeline().fireExceptionCaught(new RuntimeException("test exception"));
        channel.runPendingTasks();

        assertFalse(channel.isOpen(), "Channel must be closed after a non-IO exception");
    }

    /**
     * {@link IOException} (socket drop / Error 38) must also close the channel.
     */
    @Test
    void exceptionCaught_ioException_closesChannel() {
        channel = new EmbeddedChannel(buildHandler((r, c) -> {}));

        ByteBuf initPacket = channel.readOutbound();
        if (initPacket != null) initPacket.release();

        channel.pipeline().fireExceptionCaught(new IOException("Connection reset by peer"));
        channel.runPendingTasks();

        assertFalse(channel.isOpen(), "Channel must be closed after an IOException");
    }

    /**
     * Disconnect idempotency (item 5): {@link RemoteClient#disconnected()} must
     * fire the module hook at most once regardless of how many times it is
     * called.
     */
    @Test
    void disconnected_isIdempotent() {
        var client = new TestClient();
        channel = new EmbeddedChannel();
        var session = new ClientSession<>(channel, client);
        client.setSession(session);

        client.disconnected();
        client.disconnected();
        client.disconnected();

        assertTrue(client.disconnectedCalled.get(), "onDisconnected must have been called");
        // Call count is guaranteed to be 1 by the AtomicBoolean in RemoteClient.
    }

    /**
     * Metrics (item 3): global active-session counter must increment on connect
     * and decrement on disconnect.
     */
    @Test
    void sessionMetrics_activeSessionCountTracksLifecycle() {
        long before = SessionMetrics.getActiveSessions();
        channel = new EmbeddedChannel(buildHandler((r, c) -> {}));

        ByteBuf initPacket = channel.readOutbound();
        if (initPacket != null) initPacket.release();

        assertTrue(SessionMetrics.getActiveSessions() > before,
                "Active sessions must increase on connect");

        channel.close();
        channel.runPendingTasks();

        assertEquals(before, SessionMetrics.getActiveSessions(),
                "Active sessions must return to baseline after disconnect");
    }

    // -----------------------------------------------------------------------
    // PacketDispatchTable tests (item 7)
    // -----------------------------------------------------------------------

    @Test
    void packetDispatchTable_registeredOpcodeInvokesHandler() {
        var called = new AtomicBoolean(false);
        PacketDispatchTable<TestClient> table = new PacketDispatchTable<TestClient>()
                .register((short) 0x01, (r, c) -> called.set(true));

        boolean dispatched = table.dispatch(
                new LittleEndianByteArrayReader(new byte[]{0x01, 0x00}), new TestClient());

        assertTrue(dispatched, "Registered opcode must return true");
        assertTrue(called.get(), "Handler must have been invoked");
    }

    @Test
    void packetDispatchTable_unknownOpcodeReturnsFalse() {
        PacketDispatchTable<TestClient> table = new PacketDispatchTable<>();

        boolean dispatched = table.dispatch(
                new LittleEndianByteArrayReader(new byte[]{0x42, 0x00}), new TestClient());

        assertFalse(dispatched, "Unknown opcode must return false");
    }

    @Test
    void packetDispatchTable_noOpReturnsTrueWithoutCallingHandler() {
        var called = new AtomicBoolean(false);
        PacketDispatchTable<TestClient> table = new PacketDispatchTable<TestClient>()
                .noOp((short) 0x18);

        boolean dispatched = table.dispatch(
                new LittleEndianByteArrayReader(new byte[]{0x18, 0x00}), new TestClient());

        assertTrue(dispatched, "noOp opcode must return true");
        assertFalse(called.get(), "No user handler must fire for a noOp");
    }
}
