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

import argonms.common.character.Player;

/**
 * Uniform lifecycle contract for every external client connection context.
 *
 * <p>Every server module (login, game, shop) binds its session state to a
 * Netty {@link io.netty.channel.Channel} via
 * {@link MapleSessionHandler#CLIENT_KEY}. Cross-cutting concerns — telemetry,
 * logging, the disconnect pipeline — operate against this interface, avoiding
 * direct knowledge of module-specific client classes.
 *
 * <p>This interface is the foundation for the Milestone 4 unified
 * client-context abstraction. Current implementations are
 * {@code LoginClient}, {@code GameClient}, and {@code ShopClient}, all of
 * which extend {@link RemoteClient}.
 */
public interface MapleClientContext {

	/**
	 * Returns the account name if the client is authenticated, or {@code null}
	 * if the session has not yet completed login.
	 */
	String getAccountName();

	/**
	 * Returns the live {@link ClientSession} bound to this context, or
	 * {@code null} after the session has been torn down.
	 */
	ClientSession<?> getSession();

	/**
	 * Returns the currently active {@link Player} (character in-world), or
	 * {@code null} when the client is in login/character-select state.
	 */
	Player getPlayer();

	/**
	 * Called exactly once when the underlying channel closes.
	 *
	 * <p>Implementations must be idempotent because the
	 * {@link RemoteClient#disconnected()} template method guarantees
	 * at-most-once invocation via an {@code AtomicBoolean} guard even when
	 * multiple threads race to close the session.
	 */
	void disconnected();
}
