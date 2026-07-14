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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for account-level operations (login, authentication,
 * connection status updates).
 *
 * <p>All public methods are stateless and either accept a {@link Connection}
 * for transaction participation or obtain their own from the pool.
 */
public final class AccountDAO {
	private static final Logger LOG = Logger.getLogger(AccountDAO.class.getName());

	private AccountDAO() {
	}

	/**
	 * Retrieves the account's current connected status.
	 *
	 * @param accountId the account ID
	 * @return the connected status byte, or {@code -1} if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static byte getConnectedStatus(int accountId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement("SELECT `connected` FROM `accounts` WHERE `id` = ?")) {
			ps.setInt(1, accountId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getByte(1) : -1;
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not get connected status of account " + accountId, ex);
		}
	}

	/**
	 * Updates the account's connected status.
	 *
	 * @param accountId the account ID
	 * @param status    the new connection status byte
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateConnectedStatus(int accountId, byte status) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement("UPDATE `accounts` SET `connected` = ? WHERE `id` = ?")) {
			ps.setByte(1, status);
			ps.setInt(2, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Could not update connected status of account " + accountId, ex);
		}
	}

	/**
	 * Updates the account's connected status within an existing transaction.
	 *
	 * @param con       the connection to use (caller manages transaction)
	 * @param accountId the account ID
	 * @param status    the new connection status byte
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateConnectedStatus(Connection con, int accountId, byte status) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `accounts` SET `connected` = ? WHERE `id` = ?")) {
			ps.setByte(1, status);
			ps.setInt(2, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Could not update connected status of account " + accountId, ex);
		}
	}

	/**
	 * Updates storage info (slots and mesos) for the given account.
	 *
	 * @param con            the connection to use (caller manages transaction)
	 * @param accountId      the account ID
	 * @param storageSlots   the number of storage slots
	 * @param storageMesos   the amount of mesos in storage
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateStorage(Connection con, int accountId, short storageSlots, int storageMesos) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `accounts` SET `storageslots` = ?, `storagemesos` = ? WHERE `id` = ?")) {
			ps.setShort(1, storageSlots);
			ps.setInt(2, storageMesos);
			ps.setInt(3, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to save storage info for account " + accountId, ex);
		}
	}

	/**
	 * Loads account authentication data for login.
	 *
	 * @param accountName the account name
	 * @return the result set data as an {@link AuthRecord}, or {@code null} if
	 *         the account does not exist
	 * @throws DataAccessException if a database error occurs
	 */
	public static AuthRecord loadAuthRecord(String accountName) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement(
				"SELECT `id`,`password`,`salt`,`pin`,`gender`,`birthday`,"
				+ "`characters`,`connected`,`gm` FROM `accounts` WHERE `name` = ?")) {
			ps.setString(1, accountName);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return new AuthRecord(
					rs.getInt("id"),
					rs.getString("password"),
					rs.getString("salt"),
					rs.getString("pin"),
					rs.getByte("gender"),
					rs.getString("birthday"),
					rs.getByte("characters"),
					rs.getByte("connected"),
					rs.getByte("gm")
				);
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to load auth record for account " + accountName, ex);
		}
	}

	/**
	 * Updates account password and salt (e.g. after hash-upgrade).
	 *
	 * @param accountId the account ID
	 * @param password  the new hashed password
	 * @param salt      the new salt (may be null)
	 * @param status    the new connection status
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updatePasswordAndStatus(int accountId, String password, String salt, byte status) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement(
				"UPDATE `accounts` SET `connected` = ?, `password` = ?, `salt` = ? WHERE `id` = ?")) {
			ps.setByte(1, status);
			ps.setString(2, password);
			ps.setString(3, salt);
			ps.setInt(4, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to update password for account " + accountId, ex);
		}
	}

	/**
	 * Updates the account's gender.
	 *
	 * @param accountId the account ID
	 * @param gender    the gender byte
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateGender(int accountId, byte gender) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement("UPDATE `accounts` SET `gender` = ? WHERE `id` = ?")) {
			ps.setByte(1, gender);
			ps.setInt(2, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to update gender for account " + accountId, ex);
		}
	}

	/**
	 * Updates the account's PIN.
	 *
	 * @param accountId the account ID
	 * @param pin       the new PIN
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updatePin(int accountId, String pin) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement("UPDATE `accounts` SET `pin` = ? WHERE `id` = ?")) {
			ps.setString(1, pin);
			ps.setInt(2, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to update PIN for account " + accountId, ex);
		}
	}

	/**
	 * Updates the account's recent connection info (MAC addresses, IP).
	 *
	 * @param accountId  the account ID
	 * @param recentMacs the recent MAC addresses
	 * @param recentIp   the recent IP address
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateRecentConnection(int accountId, String recentMacs, String recentIp) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement(
				"UPDATE `accounts` SET `recentmacs` = ?, `recentip` = ? WHERE `id` = ?")) {
			ps.setString(1, recentMacs);
			ps.setString(2, recentIp);
			ps.setInt(3, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to update connection info for account " + accountId, ex);
		}
	}

	/**
	 * Updates the account's max characters field.
	 *
	 * @param con       the connection (caller manages)
	 * @param accountId the account ID
	 * @param maxChars  the new max characters value
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateMaxCharacters(Connection con, int accountId, byte maxChars) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `accounts` SET `characters` = ? WHERE `id` = ?")) {
			ps.setByte(1, maxChars);
			ps.setInt(2, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to update max characters for account " + accountId, ex);
		}
	}

	/**
	 * Upserts the cash shop balance for an account.
	 *
	 * @param con        the connection (caller manages)
	 * @param accountId  the account ID
	 * @param paypalNx   the PayPal NX balance
	 * @param maplePoints the maple points balance
	 * @param gamecardNx the game card NX balance
	 * @throws DataAccessException if a database error occurs
	 */
	public static void upsertCashBalance(Connection con, int accountId, int paypalNx,
			int maplePoints, int gamecardNx) {
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `cashshopbalance` (accountid, paypalnx, maplepoints, gamecardnx) "
				+ "VALUES (?, ?, ?, ?) "
				+ "ON DUPLICATE KEY UPDATE paypalnx=VALUES(paypalnx), maplepoints=VALUES(maplepoints), gamecardnx=VALUES(gamecardnx)")) {
			ps.setInt(1, accountId);
			ps.setInt(2, paypalNx);
			ps.setInt(3, maplePoints);
			ps.setInt(4, gamecardNx);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to upsert cash balance for account " + accountId, ex);
		}
	}

	/**
	 * Resets the connected status of all accounts to NOT_LOGGED_IN.
	 *
	 * @param con    the connection (caller manages)
	 * @param status the status to set for all accounts
	 * @throws DataAccessException if a database error occurs
	 */
	public static void resetAllConnectedStatus(Connection con, int status) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `accounts` SET `connected` = ?")) {
			ps.setInt(1, status);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Could not reset all account connection statuses", ex);
		}
	}

	/**
	 * Updates the account's recent MAC and IP info.
	 *
	 * @param con        the connection (caller manages)
	 * @param accountId  the account ID
	 * @param recentMacs the recent MAC addresses as binary
	 * @param recentIp   the recent IP address as long
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateRecentMacsAndIp(Connection con, int accountId, byte[] recentMacs, long recentIp) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `accounts` SET `recentmacs` = ?, `recentip` = ? WHERE `id` = ?")) {
			ps.setBytes(1, recentMacs);
			ps.setLong(2, recentIp);
			ps.setInt(3, accountId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Failed to update MAC/IP info for account " + accountId, ex);
		}
	}

	/**
	 * Checks if any of the given MACs are banned.
	 *
	 * @param con       the connection (caller manages)
	 * @param macBytes  array of 6-byte MAC addresses
	 * @return set of ban IDs associated with banned MACs
	 * @throws DataAccessException if a database error occurs
	 */
	public static java.util.Set<Integer> checkBannedMacs(Connection con, byte[][] macBytes) {
		StringBuilder query = new StringBuilder("SELECT `banid` FROM `macbans` WHERE `mac` IN (");
		for (int i = 0; i < macBytes.length; i++) {
			query.append("?, ");
		}
		if (macBytes.length > 0) {
			int length = query.length();
			query.replace(length - 2, length, ")");
		}
		try (PreparedStatement ps = con.prepareStatement(query.toString())) {
			for (int i = 0; i < macBytes.length; i++) {
				ps.setBytes(i + 1, macBytes[i]);
			}
			java.util.Set<Integer> banIds = new java.util.HashSet<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					banIds.add(Integer.valueOf(rs.getInt(1)));
				}
			}
			return banIds;
		} catch (SQLException ex) {
			throw new DataAccessException("Could not check banned MACs", ex);
		}
	}

	/**
	 * Deletes a character from the characters table.
	 *
	 * @param con         the connection (caller manages)
	 * @param characterId the character ID to delete
	 * @return the number of rows deleted
	 * @throws DataAccessException if a database error occurs
	 */
	public static int deleteCharacter(Connection con, int characterId) {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE FROM `characters` WHERE `id` = ?")) {
			ps.setInt(1, characterId);
			return ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Could not delete character " + characterId, ex);
		}
	}

	/**
	 * Immutable record holding authentication data loaded from the accounts table.
	 */
	public record AuthRecord(
		int id,
		String password,
		String salt,
		String pin,
		byte gender,
		String birthday,
		byte characters,
		byte connected,
		byte gm
	) {}
}
