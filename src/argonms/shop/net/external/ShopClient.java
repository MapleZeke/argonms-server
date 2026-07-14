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

package argonms.shop.net.external;

import argonms.common.ServerType;
import argonms.common.net.external.RemoteClient;
import argonms.common.util.dao.AccountDAO;
import argonms.common.util.dao.DataAccessException;
import argonms.shop.ShopServer;
import argonms.shop.character.ShopCharacter;
import java.util.logging.Level;
import java.util.logging.Logger;

//world and channel are needed to redirect player to game server when they exit
//world is easy (in characters db table), but how do we get channel...?
public class ShopClient extends RemoteClient {
	private static final Logger LOG = Logger.getLogger(ShopClient.class.getName());

	private ShopCharacter player;

	public byte getOnlineState() {
		try {
			return AccountDAO.getConnectedStatus(getAccountId());
		} catch (DataAccessException ex) {
			LOG.log(Level.WARNING, "Could not get connected status of account " + getAccountId(), ex);
			return -1;
		}
	}

	public void setPlayer(ShopCharacter p) {
		this.player = p;
	}

	@Override
	public ShopCharacter getPlayer() {
		return player;
	}

	@Override
	public byte getServerId() {
		return ServerType.SHOP;
	}

	private void dissociate() {
		if (player != null) {
			if (!isMigrating()) {
				player.prepareLogOff();
			}
			ShopServer.getInstance().removePlayer(player);
			player = null;
		}
		getSession().removeClient();
		setSession(null);
	}

	@Override
	public void onDisconnected() {
		if (getSession().getQueuedReads() == 0) {
			dissociate();
		} else {
			getSession().setEmptyReadQueueHandler(() ->
				dissociate());
		}
		if (!isMigrating()) {
			updateState(STATUS_NOTLOGGEDIN);
		}
	}
}
