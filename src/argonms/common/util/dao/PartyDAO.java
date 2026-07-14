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

import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for party operations (membership, creation, deletion).
 *
 * <p>All public methods are stateless and either accept a {@link Connection}
 * for transaction participation or obtain their own from the pool.
 */
public final class PartyDAO {

	private PartyDAO() {
	}

	/**
	 * Immutable record holding party member data loaded from the database.
	 */
	public record PartyMemberRecord(int characterId, String name, short job, short level, boolean leader) {}

	/**
	 * Gets the maximum party ID currently in use for the given world.
	 *
	 * @param world the world ID
	 * @return the max party ID, or -1 if none found
	 * @throws DataAccessException if a database error occurs
	 */
	public static int getMaxPartyId(byte world) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT MAX(`partyid`) FROM `parties` WHERE `world` = ?")) {
			ps.setByte(1, world);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
			return -1;
		} catch (SQLException e) {
			throw new DataAccessException("Could not get max party ID for world " + world, e);
		}
	}

	/**
	 * Deletes party entries for multiple characters (batch).
	 *
	 * @param con          the connection (caller manages)
	 * @param characterIds the character IDs to remove from parties
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deletePartyMembers(Connection con, Iterable<Integer> characterIds) {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE FROM `parties` WHERE `characterid` = ?")) {
			for (Integer characterId : characterIds) {
				ps.setInt(1, characterId.intValue());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Could not delete party members", e);
		}
	}

	/**
	 * Deletes the party entry for a single character.
	 *
	 * @param con         the connection (caller manages)
	 * @param characterId the character ID
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deletePartyMember(Connection con, int characterId) {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE FROM `parties` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not delete party member " + characterId, e);
		}
	}

	/**
	 * Loads all party members for a given party in a world.
	 *
	 * @param con     the connection (caller manages)
	 * @param world   the world ID
	 * @param partyId the party ID
	 * @return list of party member records
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<PartyMemberRecord> loadPartyMembers(Connection con, byte world, int partyId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `c`.`id`,`c`.`name`,`c`.`job`,`c`.`level`,`p`.`leader` "
				+ "FROM `parties` `p` LEFT JOIN `characters` `c` ON `c`.`id` = `p`.`characterid` "
				+ "WHERE `p`.`world` = ? AND `p`.`partyid` = ?")) {
			ps.setInt(1, world);
			ps.setInt(2, partyId);
			List<PartyMemberRecord> members = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					members.add(new PartyMemberRecord(
						rs.getInt(1), rs.getString(2), rs.getShort(3),
						rs.getShort(4), rs.getBoolean(5)
					));
				}
			}
			return members;
		} catch (SQLException e) {
			throw new DataAccessException("Could not load party " + partyId + " of world " + world, e);
		}
	}

	/**
	 * Gets the character IDs for a given world and account.
	 *
	 * @param con       the connection (caller manages)
	 * @param accountId the account ID
	 * @param world     the world ID
	 * @return list of character IDs
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<Integer> getCharacterIds(Connection con, int accountId, byte world) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `id` FROM `characters` WHERE `accountid` = ? AND `world` = ?")) {
			ps.setInt(1, accountId);
			ps.setInt(2, world);
			List<Integer> ids = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
			return ids;
		} catch (SQLException e) {
			throw new DataAccessException("Could not get character IDs for account " + accountId, e);
		}
	}

	/**
	 * Gets the worlds that an account has characters in.
	 *
	 * @param con       the connection (caller manages)
	 * @param accountId the account ID
	 * @return list of world IDs with characters
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<Byte> getCharacterWorlds(Connection con, int accountId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `world` FROM `characters` WHERE `accountid` = ?")) {
			ps.setInt(1, accountId);
			List<Byte> worlds = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					worlds.add(rs.getByte(1));
				}
			}
			return worlds;
		} catch (SQLException e) {
			throw new DataAccessException("Could not get character worlds for account " + accountId, e);
		}
	}

	/**
	 * Checks if a character name is already taken.
	 *
	 * @param con  the connection (caller manages)
	 * @param name the character name to check
	 * @return true if the name exists
	 * @throws DataAccessException if a database error occurs
	 */
	public static boolean characterNameExists(Connection con, String name) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `id` FROM `characters` WHERE `name` = ?")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not check character name " + name, e);
		}
	}
}
