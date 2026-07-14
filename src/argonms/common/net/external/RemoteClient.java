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
import argonms.common.net.SessionDataModel;
import argonms.common.util.dao.AccountDAO;
import argonms.common.util.dao.DataAccessException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for all external (game-client-facing) session contexts.
 *
 * <p>Implements {@link MapleClientContext} so that cross-cutting concerns
 * (telemetry, logging, disconnect pipeline) can operate on a common interface
 * without depending on module-specific subclasses.
 *
 * <h2>Disconnect pipeline (item 5 – idempotent state transitions)</h2>
 * {@link #disconnected()} is declared {@code final} and uses an
 * {@code AtomicBoolean} to guarantee that the module-specific cleanup hook
 * {@link #onDisconnected()} is called <em>at most once</em> per session
 * lifetime, even when multiple threads race to close the channel.
 */
public abstract class RemoteClient implements SessionDataModel, MapleClientContext {
	private static final Logger LOG = Logger.getLogger(RemoteClient.class.getName());
	public static final byte STATUS_NOTLOGGEDIN = 0;
	public static final byte STATUS_MIGRATION = 1;
	public static final byte STATUS_INLOGIN = 2;
	public static final byte STATUS_INGAME = 3;
	public static final byte STATUS_INSHOP = 4;

	private ClientSession<?> session;
	private int id;
	private String name;
	private byte world;
	private byte channel;
	private boolean serverTransition;
	private final AtomicBoolean disconnectedOnce = new AtomicBoolean(false);

	public int getAccountId() {
		return id;
	}

	public void setAccountId(int id) {
		this.id = id;
	}

	public String getAccountName() {
		return name;
	}

	public void setAccountName(String name) {
		this.name = name;
	}

	@Override
	public ClientSession<?> getSession() {
		return session;
	}

	protected void setSession(ClientSession<?> s) {
		this.session = s;
	}

	public byte getWorld() {
		return world;
	}

	public void setWorld(byte world) {
		this.world = world;
	}

	public byte getChannel() {
		return channel;
	}

	public void setChannel(byte channel) {
		this.channel = channel;
	}

	public void clientError(String message) {
		LOG.log(Level.WARNING, "Received error from client at {0}:\n{1}",
				new Object[]{getSession().getAddress(), message});
	}

	public void setMigratingHost() {
		serverTransition = true;
		updateState(STATUS_MIGRATION);
	}

	protected boolean isMigrating() {
		return serverTransition;
	}

	public void updateState(byte currentState) {
		try {
			AccountDAO.updateConnectedStatus(id, currentState);
		} catch (DataAccessException ex) {
			LOG.log(Level.WARNING, "Could not change connected status of account " + id, ex);
		}
	}

	public abstract Player getPlayer();

	public abstract byte getServerId();

	/**
	 * Called at most once when the underlying channel closes.
	 *
	 * <p>This method is {@code final}; an {@code AtomicBoolean} ensures the
	 * module-specific {@link #onDisconnected()} hook fires exactly once even
	 * when multiple threads race to close the channel.
	 *
	 * <p><strong>Do not call this method directly to force a close.</strong>
	 * Use {@link ClientSession#close(String)} instead.
	 */
	@Override
	public final void disconnected() {
		if (disconnectedOnce.compareAndSet(false, true)) {
			onDisconnected();
		}
	}

	/**
	 * Module-specific disconnect hook invoked exactly once per session.
	 *
	 * <p>Subclasses must implement this instead of overriding
	 * {@link #disconnected()}.  Typical responsibilities: cancel ongoing NPC
	 * conversations, flush character saves, remove the player from the active
	 * map/channel, and null out session references to allow GC.
	 */
	protected abstract void onDisconnected();
}
