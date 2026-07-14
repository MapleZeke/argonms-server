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
