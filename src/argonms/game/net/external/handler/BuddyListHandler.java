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

package argonms.game.net.external.handler;

import argonms.common.character.BuddyListEntry;
import argonms.common.net.external.RemoteClient;
import argonms.common.net.internal.ChannelSynchronizationOps;
import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import argonms.common.util.collections.Pair;
import argonms.common.util.dao.BuddyDAO;
import argonms.common.util.dao.CharacterDAO;
import argonms.common.util.dao.DataAccessException;
import argonms.common.util.input.LittleEndianReader;
import argonms.game.GameServer;
import argonms.game.character.BuddyList;
import argonms.game.character.GameCharacter;
import argonms.game.net.external.GameClient;
import argonms.game.net.external.GamePackets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BuddyListHandler {
	private static final Logger LOG = Logger.getLogger(BuddyListHandler.class.getName());

	public static final byte INVITE = 0x01;
	public static final byte ACCEPT = 0x02;
	public static final byte DELETE = 0x03;

	public static final byte FIRST = 0x07;
	public static final byte INVITE_RECEIVED = 0x09;
	public static final byte ADD = 0x0A;
	public static final byte YOUR_LIST_FULL = 0x0B;
	public static final byte THEIR_LIST_FULL = 0x0C;
	public static final byte ALREADY_ON_LIST = 0x0D;
	public static final byte NO_GM_INVITES = 0x0E;
	public static final byte NONEXISTENT = 0x0F;
	public static final byte REMOVE = 0x12;
	public static final byte BUDDY_LOGGED_IN = 0x14;
	public static final byte CAPACITY_CHANGE = 0x15;

	private static boolean accountLoggedIn(int playerId) {
		try {
			return BuddyDAO.isAccountInGame(playerId);
		} catch (DataAccessException e) {
			LOG.log(Level.WARNING, "Error checking if character " + playerId + " is online", e);
			return false;
		}
	}

	private static byte inviteOfflinePlayer(Connection con, int invitee, int inviter, String inviterName) throws SQLException {
		return BuddyDAO.inviteOfflinePlayer(con, invitee, inviter, inviterName, THEIR_LIST_FULL);
	}

	private static void processSendInvite(String invitee, GameClient client) {
		GameCharacter p = client.getPlayer();
		BuddyList bList = p.getBuddyList();
		if (bList.isFull()) {
			client.getSession().send(GamePackets.writeSimpleBuddyListMessage(YOUR_LIST_FULL));
			return;
		}
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.CharacterLookup lookup = CharacterDAO.lookupByName(con, invitee);
			if (lookup == null || lookup.world() != client.getWorld()) {
				client.getSession().send(GamePackets.writeSimpleBuddyListMessage(NONEXISTENT));
				return;
			}
			if (lookup.gm() > p.getPrivilegeLevel()) {
				client.getSession().send(GamePackets.writeSimpleBuddyListMessage(NO_GM_INVITES));
				return;
			}
			int inviteeId = lookup.characterId();
			if (bList.getBuddy(inviteeId) != null || bList.isInInvites(inviteeId)) {
				client.getSession().send(GamePackets.writeSimpleBuddyListMessage(ALREADY_ON_LIST));
				return;
			}
			switch (lookup.connected()) {
				case RemoteClient.STATUS_INGAME: {
					Pair<Byte, Byte> channelAndResult = GameServer.getChannel(client.getChannel()).getCrossServerInterface().sendBuddyInvite(p, inviteeId);
					byte result = channelAndResult.right.byteValue();
					if (result == Byte.MAX_VALUE) {
						bList.addBuddy(new BuddyListEntry(inviteeId, lookup.name(), BuddyListEntry.STATUS_HALF_OPEN));
						client.getSession().send(GamePackets.writeBuddyList(ADD, bList));
						break;
					} else if (result == Byte.MIN_VALUE) {
						bList.addBuddy(new BuddyListEntry(inviteeId, lookup.name(), BuddyListEntry.STATUS_MUTUAL, channelAndResult.left.byteValue()));
						client.getSession().send(GamePackets.writeBuddyList(ADD, bList));
						break;
					} else if (result != -1) {
						client.getSession().send(GamePackets.writeSimpleBuddyListMessage(result));
						break;
					}
					//apparently they are offline...
					//intentional fallthrough to inviteOfflinePlayer
				}
				default: {
					byte result = inviteOfflinePlayer(con, inviteeId, p.getId(), p.getName());
					if (result == Byte.MAX_VALUE) {
						bList.addBuddy(new BuddyListEntry(inviteeId, lookup.name(), BuddyListEntry.STATUS_HALF_OPEN));
						client.getSession().send(GamePackets.writeBuddyList(ADD, bList));
					} else if (result == Byte.MIN_VALUE) {
						bList.addBuddy(new BuddyListEntry(inviteeId, lookup.name(), BuddyListEntry.STATUS_MUTUAL));
						client.getSession().send(GamePackets.writeBuddyList(ADD, bList));
					} else if (result != -1) {
						client.getSession().send(GamePackets.writeSimpleBuddyListMessage(result));
					}
					break;
				}
			}
		} catch (SQLException e) {
			LOG.log(Level.WARNING, "Error inviting " + invitee + " to buddy list of " + client.getPlayer().getName(), e);
		}
	}

	private static void processAcceptInvite(int inviterId, GameClient client) {
		GameCharacter p = client.getPlayer();
		BuddyList bList = p.getBuddyList();
		if (!bList.isInInvites(inviterId)) {
			client.getSession().send(GamePackets.writeSimpleBuddyListMessage(NONEXISTENT));
			return;
		}
		String name = bList.removeInvite(inviterId);
		if (bList.isFull()) {
			client.getSession().send(GamePackets.writeSimpleBuddyListMessage(YOUR_LIST_FULL));
			return;
		}
		bList.addBuddy(new BuddyListEntry(inviterId, name, BuddyListEntry.STATUS_MUTUAL));
		client.getSession().send(GamePackets.writeBuddyList(ADD, bList));

		//if (!accountLoggedIn(inviterId)) we are absolutely certain inviter is
		//offline.
		//otherwise, channel scan in sendBuddyAccepted because we have no idea
		//whether he is online, and if so, in what channel. sendBuddyInviteAccepted
		//will return false if he could not be found on any channel.
		//if we conclude he is offline, update his entry directly on the database
		if (!accountLoggedIn(inviterId) || !GameServer.getChannel(client.getChannel()).getCrossServerInterface().sendBuddyInviteAccepted(p, inviterId)) {
			//if inviter is concluded to be offline, attempt to make his entry MUTUAL on database
			try {
				BuddyDAO.setOfflineBuddyMutual(inviterId, p.getId());
			} catch (DataAccessException e) {
				LOG.log(Level.WARNING, "Could not accept buddy invite", e);
			}
		}
	}

	private static void processDeleteEntry(int deletedId, GameClient client) {
		GameCharacter p = client.getPlayer();
		BuddyList bList = p.getBuddyList();
		if (bList.isInInvites(deletedId)) {
			bList.removeInvite(deletedId);
			return;
		}
		BuddyListEntry removed = bList.removeBuddy(deletedId);
		if (removed == null) {
			client.getSession().send(GamePackets.writeSimpleBuddyListMessage(NONEXISTENT));
			return;
		}
		byte channel = removed.getChannel();
		//either we sent an invite and the other user has not responded yet,
		//or the other user deleted us from his/her own buddy list already.
		//doesn't hurt to try to retract the invite even if it's the second case
		//(note, removed.getStatus() == STATUS_MUTUAL is equivalent to !tryRetractInvite)
		boolean tryRetractInvite = removed.getStatus() == BuddyListEntry.STATUS_HALF_OPEN;
		client.getSession().send(GamePackets.writeBuddyList(REMOVE, bList));

		//if (channel == BuddyListEntry.OFFLINE_CHANNEL && removed.getStatus() == STATUS_MUTUAL),
		//we are absolutely certain deleted buddy is offline - STATUS_MUTUAL entries always have accurate channels
		//otherwise, channel scan in sendBuddyDeleted because we have no idea
		//whether he is online, and if so, in what channel. sendBuddyInviteRetracted
		//will return false if he could not be found on any channel.
		//if we conclude he is offline, update his entry or delete invite to him
		//directly on the database
		if (channel != BuddyListEntry.OFFLINE_CHANNEL) {
			//if entry's channel is not OFFLINE_CHANNEL, it must be STATUS_MUTUAL
			assert !tryRetractInvite;
			GameServer.getChannel(client.getChannel()).getCrossServerInterface().sendBuddyDeleted(p, deletedId, channel);
		//sendBuddyInviteRetracted will attempt to remove our invite to buddy if he is found logged into a channel (no effect if tryRetractInvite is true and is the second case)
		} else if (!tryRetractInvite || !GameServer.getChannel(client.getChannel()).getCrossServerInterface().sendBuddyInviteRetracted(p, deletedId)) {
			//if buddy is concluded to be offline, attempt to remove invite to him on database (no effect if tryRetractInvite is true and is the second case) or make his entry HALF_OPEN
			assert !accountLoggedIn(deletedId) || GameServer.getChannel(client.getChannel()).getCrossServerInterface().scanChannelOfPlayer(removed.getName(), false) <= ChannelSynchronizationOps.CHANNEL_CASH_SHOP;
			try {
				BuddyDAO.removeOfflineBuddyEntry(deletedId, p.getId(), tryRetractInvite);
			} catch (DataAccessException e) {
				LOG.log(Level.WARNING, "Could not delete buddy entry", e);
			}
		}
	}

	public static void handleListModification(LittleEndianReader packet, GameClient gc) {
		switch (packet.readByte()) {
			case INVITE:
				processSendInvite(packet.readLengthPrefixedString(), gc);
				break;
			case ACCEPT:
				processAcceptInvite(packet.readInt(), gc);
				break;
			case DELETE:
				processDeleteEntry(packet.readInt(), gc);
				break;
		}
	}

	private BuddyListHandler() {
		//uninstantiable...
	}
}
