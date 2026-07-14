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

import argonms.common.util.input.LittleEndianReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Map-based opcode dispatch table that replaces the switch-statement routing in
 * each {@link ClientPacketProcessor} subclass.
 *
 * <h2>Purpose (item 7 – formalising packet-routing boundaries)</h2>
 * This class makes the packet-to-handler mapping an explicit, queryable data
 * structure instead of an implicit set of {@code case} labels. Benefits:
 * <ul>
 *   <li>Unknown opcodes are trivially detected (return value of
 *       {@link #dispatch}).</li>
 *   <li>No-op opcodes are explicitly declared via {@link #noOp}, separating
 *       intentional silencing from truly unknown opcodes.</li>
 *   <li>Handlers are {@link BiConsumer} method references, enabling per-state
 *       validator injection in a future milestone (e.g. reject
 *       {@code CHANGE_MAP} before {@code PLAYER_CONNECTED}).</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * The table is intended to be built once at class-load time via a
 * {@code static final} field, then used read-only from many threads.  The
 * underlying {@link HashMap} is safely published through the class-loading
 * mechanism and must not be mutated after the initialiser completes.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * private static final PacketDispatchTable<LoginClient> TABLE =
 *     new PacketDispatchTable<LoginClient>()
 *         .register(ClientRecvOps.LOGIN_PASSWORD, AuthHandler::handleLogin)
 *         .register(ClientRecvOps.PONG, (r, lc) -> lc.getSession().receivedPong())
 *         .noOp(ClientRecvOps.AES_IV_UPDATE_REQUEST);
 *
 * public void process(LittleEndianReader reader, LoginClient lc) {
 *     if (!TABLE.dispatch(reader, lc)) {
 *         LOG.log(Level.FINE, "Unhandled opcode, {0} bytes", reader.available() + 2);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the client-context type (e.g. {@code LoginClient})
 */
public final class PacketDispatchTable<T extends RemoteClient> {

	private final Map<Short, BiConsumer<LittleEndianReader, T>> handlers = new HashMap<>();

	/**
	 * Registers a handler for the given opcode.
	 *
	 * @param opcode  the 2-byte client-to-server opcode constant (see
	 *                {@link ClientRecvOps})
	 * @param handler the handler to invoke; receives the reader positioned
	 *                immediately after the opcode bytes and the client context
	 * @return {@code this} for fluent chaining
	 */
	public PacketDispatchTable<T> register(short opcode, BiConsumer<LittleEndianReader, T> handler) {
		handlers.put(opcode, Objects.requireNonNull(handler, "handler"));
		return this;
	}

	/**
	 * Registers an intentional no-op for the given opcode, suppressing the
	 * "unhandled packet" log that would otherwise fire for unregistered opcodes.
	 *
	 * @param opcode the opcode to silently discard
	 * @return {@code this} for fluent chaining
	 */
	public PacketDispatchTable<T> noOp(short opcode) {
		handlers.put(opcode, (r, c) -> {});
		return this;
	}

	/**
	 * Reads the 2-byte opcode from {@code reader} and dispatches to the
	 * registered handler.
	 *
	 * <p>When this method returns {@code false} the caller is responsible for
	 * emitting an "unhandled opcode" log.  Note that {@code reader.available()}
	 * at that point reflects the body size only; add {@code 2} to include the
	 * consumed opcode bytes in any length log.
	 *
	 * @param reader the packet reader positioned at the first opcode byte
	 * @param client the client context for this packet
	 * @return {@code true} if a registered handler (including no-ops) was
	 *         invoked; {@code false} if the opcode is unknown
	 */
	public boolean dispatch(LittleEndianReader reader, T client) {
		short opcode = reader.readShort();
		var handler = handlers.get(opcode);
		if (handler != null) {
			handler.accept(reader, client);
			return true;
		}
		return false;
	}
}
