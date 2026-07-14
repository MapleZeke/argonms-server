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

package argonms.common.util.dao;

import argonms.common.character.BuddyListEntry;
import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Data Access Object for buddy list query/update operations that go beyond
 * the bulk-save handled by {@link CharacterDAO#replaceBuddies}.
 *
 * <p>Handles invite checks, offline invite insertion, and status updates
 * used during the buddy invite/accept/delete lifecycle.
 */
public final class BuddyDAO {
	private static final Logger LOG = Logger.getLogger(BuddyDAO.class.getName());

	private BuddyDAO() {
	}

	/**
	 * Checks whether a character's account is currently logged into the game server.
	 *
	 * @param playerId the character ID
	 * @return true if the account status is STATUS_INGAME
	 * @throws DataAccessException if a database error occurs
	 */
	public static boolean isAccountInGame(int playerId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement("SELECT `a`.`connected` "
						+ "FROM `accounts` `a` LEFT JOIN `characters` `c` ON `c`.`accountid` = `a`.`id` "
						+ "WHERE `c`.`id` = ?")) {
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return false;
				}
				return rs.getByte(1) == 3; // STATUS_INGAME
			}
		} catch (SQLException e) {
			throw new DataAccessException("Error checking online status for character " + playerId, e);
		}
	}

	/**
	 * Attempts to invite an offline player to a buddy list.
	 * Checks capacity and existing entries, then either inserts an invite or
	 * upgrades an existing half-open entry to mutual.
	 *
	 * @param con         the connection (caller manages)
	 * @param invitee     the character ID being invited
	 * @param inviter     the character ID doing the inviting
	 * @param inviterName the inviter's character name
	 * @return {@code Byte.MAX_VALUE} if invite inserted, {@code Byte.MIN_VALUE}
	 *         if entry upgraded to mutual, {@code -1} if invitee not found,
	 *         or a buddy list error code if their list is full
	 * @throws SQLException if a database error occurs
	 */
	public static byte inviteOfflinePlayer(Connection con, int invitee, int inviter,
			String inviterName, byte theirListFullCode) throws SQLException {
		boolean reAdd;
		try (PreparedStatement ps = con.prepareStatement("SELECT "
				+ "(`c`.`buddyslots` <= (SELECT COUNT(*) FROM `buddyentries` WHERE `owner` = `c`.`id`  AND `status` <> "
				+ BuddyListEntry.STATUS_INVITED + ")) AS `full`,"
				+ "EXISTS (SELECT * FROM `buddyentries` WHERE `owner` = `c`.`id` AND `buddy` = ?) AS `readd` "
				+ "FROM `characters` `c` WHERE `id` = ?")) {
			ps.setInt(1, inviter);
			ps.setInt(2, invitee);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return -1;
				}
				if (rs.getBoolean(1)) {
					return theirListFullCode;
				}
				reAdd = rs.getBoolean(2);
			}
		}

		if (!reAdd) {
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO `buddyentries` "
					+ "(`owner`,`buddy`,`buddyname`,`status`) VALUES (?,?,?," + BuddyListEntry.STATUS_INVITED + ")")) {
				ps.setInt(1, invitee);
				ps.setInt(2, inviter);
				ps.setString(3, inviterName);
				ps.executeUpdate();
				return Byte.MAX_VALUE;
			}
		} else {
			try (PreparedStatement ps = con.prepareStatement("UPDATE `buddyentries` SET `status` = "
					+ BuddyListEntry.STATUS_MUTUAL + " WHERE `owner` = ? AND `buddy` = ?")) {
				ps.setInt(1, invitee);
				ps.setInt(2, inviter);
				ps.executeUpdate();
				return Byte.MIN_VALUE;
			}
		}
	}

	/**
	 * Updates an offline player's buddy entry to mutual status (used when accepting an invite
	 * from a player who is offline).
	 *
	 * @param inviterId the character ID of the original inviter
	 * @param accepterId the character ID accepting the invite
	 * @throws DataAccessException if a database error occurs
	 */
	public static void setOfflineBuddyMutual(int inviterId, int accepterId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement("UPDATE `buddyentries` SET `status` = "
						+ BuddyListEntry.STATUS_MUTUAL + " WHERE `owner` = ? AND `buddy` = ?")) {
			ps.setInt(1, inviterId);
			ps.setInt(2, accepterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not accept buddy invite for inviter=" + inviterId, e);
		}
	}

	/**
	 * Updates or deletes an offline player's buddy entry when the relationship is removed.
	 *
	 * @param deletedId        the character whose entry is being modified
	 * @param deleterId        the character doing the deleting
	 * @param retractInvite    true to delete (retract invite), false to set half-open
	 * @throws DataAccessException if a database error occurs
	 */
	public static void removeOfflineBuddyEntry(int deletedId, int deleterId, boolean retractInvite) {
		String query = !retractInvite
				? "UPDATE `buddyentries` SET `status` = " + BuddyListEntry.STATUS_HALF_OPEN + " WHERE `owner` = ? AND `buddy` = ?"
				: "DELETE FROM `buddyentries` WHERE `owner` = ? AND `buddy` = ?";
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(query)) {
			ps.setInt(1, deletedId);
			ps.setInt(2, deleterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not remove buddy entry for deleted=" + deletedId, e);
		}
	}
}
