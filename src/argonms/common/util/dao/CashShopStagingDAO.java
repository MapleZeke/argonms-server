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
 * Data Access Object for cash shop staging/purchase operations.
 *
 * <p>All public methods are stateless and throw {@link DataAccessException} on failure.
 */
public final class CashShopStagingDAO {

	private CashShopStagingDAO() {
	}

	/**
	 * Updates cash purchase properties (purchaser account, gifter name, serial number)
	 * for a given unique ID.
	 *
	 * @param uniqueId           the unique item ID
	 * @param purchaserAccountId the purchaser's account ID
	 * @param gifterName         the gifter's character name (null if self-purchase)
	 * @param serialNumber       the commodity serial number
	 * @throws DataAccessException if a database error occurs
	 */
	public static void attachPurchaseProperties(long uniqueId, int purchaserAccountId,
			String gifterName, int serialNumber) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement("UPDATE `cashshoppurchases` SET "
						+ "`purchaseracctid` = ?, `gifterchrname` = ?, `serialnumber` = ? "
						+ "WHERE `uniqueid` = ?")) {
			ps.setInt(1, purchaserAccountId);
			ps.setString(2, gifterName);
			ps.setInt(3, serialNumber);
			ps.setLong(4, uniqueId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to attach cash purchase properties for unique ID " + uniqueId, e);
		}
	}

	/**
	 * Loads purchase properties for a list of unique IDs.
	 *
	 * @param con       the connection (caller manages)
	 * @param uniqueIds the unique IDs to look up
	 * @return list of purchase property records found
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<PurchasePropertyRecord> loadPurchaseProperties(Connection con, Iterable<Long> uniqueIds) {
		List<PurchasePropertyRecord> results = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `uniqueid`,`purchaseracctid`,`gifterchrname`,`serialnumber` "
				+ "FROM `cashshoppurchases` WHERE `uniqueid` = ?")) {
			for (Long uniqueId : uniqueIds) {
				ps.setLong(1, uniqueId.longValue());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						results.add(new PurchasePropertyRecord(
							rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getInt(4)
						));
					}
				}
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to load cash purchase properties", e);
		}
		return results;
	}

	/**
	 * Loads gift notifications for an account and deletes them (with table lock).
	 *
	 * @param con       the connection (caller manages)
	 * @param accountId the recipient account ID
	 * @return list of gift notification records
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<GiftNotificationRecord> loadAndDeleteGiftNotes(Connection con, int accountId) {
		boolean locked = false;
		List<GiftNotificationRecord> results = new ArrayList<>();
		try {
			try (PreparedStatement ps = con.prepareStatement("LOCK TABLE `cashitemgiftnotes` WRITE")) {
				ps.executeUpdate();
				locked = true;
			}

			try (PreparedStatement ps = con.prepareStatement(
					"SELECT `uniqueid`,`message` FROM `cashitemgiftnotes` WHERE `recipientacctid` = ?")) {
				ps.setInt(1, accountId);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						results.add(new GiftNotificationRecord(rs.getLong(1), rs.getString(2)));
					}
				}
			}

			try (PreparedStatement ps = con.prepareStatement(
					"DELETE FROM `cashitemgiftnotes` WHERE `recipientacctid` = ?")) {
				ps.setInt(1, accountId);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to load gift notifications for account " + accountId, e);
		} finally {
			if (locked) {
				try (PreparedStatement ps = con.prepareStatement("UNLOCK TABLE")) {
					ps.executeUpdate();
				} catch (SQLException e) {
					throw new DataAccessException("Could not unlock cashitemgiftnotes table", e);
				}
			}
		}
		return results;
	}

	/**
	 * Inserts gift notification records.
	 *
	 * @param con             the connection (caller manages)
	 * @param recipientAcctId the recipient account ID
	 * @param uniqueIds       the unique IDs of the gifted items
	 * @param message         the gift message
	 * @throws DataAccessException if a database error occurs
	 */
	public static void insertGiftNotes(Connection con, int recipientAcctId, Iterable<Long> uniqueIds, String message) {
		boolean locked = false;
		try {
			try (PreparedStatement ps = con.prepareStatement("LOCK TABLE `cashitemgiftnotes` WRITE")) {
				ps.executeUpdate();
				locked = true;
			}

			try (PreparedStatement ps = con.prepareStatement(
					"INSERT INTO `cashitemgiftnotes` (`uniqueid`,`recipientacctid`,`message`) VALUES (?,?,?)")) {
				ps.setInt(2, recipientAcctId);
				ps.setString(3, message);
				for (Long uniqueId : uniqueIds) {
					ps.setLong(1, uniqueId.longValue());
					ps.addBatch();
				}
				ps.executeBatch();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to insert gift notes for account " + recipientAcctId, e);
		} finally {
			if (locked) {
				try (PreparedStatement ps = con.prepareStatement("UNLOCK TABLE")) {
					ps.executeUpdate();
				} catch (SQLException e) {
					throw new DataAccessException("Could not unlock cashitemgiftnotes table", e);
				}
			}
		}
	}

	/**
	 * Retrieves the top 5 best-selling cash items by purchase count.
	 *
	 * @return array of up to 5 serial numbers
	 * @throws DataAccessException if a database error occurs
	 */
	public static int[] getBestItems() {
		int[] bestItems = new int[5];
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT `serialnumber` FROM `cashshoppurchases` "
						+ "WHERE `serialnumber` IS NOT NULL "
						+ "GROUP BY `serialnumber` ORDER BY COUNT(*) DESC LIMIT 5")) {
			try (ResultSet rs = ps.executeQuery()) {
				for (int i = 0; rs.next(); i++) {
					bestItems[i] = rs.getInt(1);
				}
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to get best cash items", e);
		}
		return bestItems;
	}

	/**
	 * Finds the next available position for cash shop items for an account.
	 *
	 * @param con       the connection
	 * @param accountId the account ID
	 * @param cashShopType the cash shop inventory type byte value
	 * @return the next available position
	 * @throws DataAccessException if a database error occurs
	 */
	public static short getNextPosition(Connection con, int accountId, byte cashShopType) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT MAX(`position`) FROM `inventoryitems` WHERE `accountid` = ? AND `inventorytype` = " + cashShopType)) {
			ps.setInt(1, accountId);
			try (ResultSet rs = ps.executeQuery()) {
				return (short) ((rs.next() ? rs.getShort(1) : 0) + 1);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to get next cash shop position for account " + accountId, e);
		}
	}

	/**
	 * Finds character IDs for a given account.
	 *
	 * @param con       the connection
	 * @param accountId the account ID
	 * @return list of character IDs
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<Integer> getCharacterIdsForAccount(Connection con, int accountId) {
		List<Integer> ids = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement("SELECT `id` FROM `characters` WHERE `accountid` = ?")) {
			ps.setInt(1, accountId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to find characters for account " + accountId, e);
		}
		return ids;
	}

	// ---- Record types ----

	/**
	 * Cash purchase property record.
	 */
	public record PurchasePropertyRecord(long uniqueId, int purchaserAccountId, String gifterName, int serialNumber) {}

	/**
	 * Gift notification record.
	 */
	public record GiftNotificationRecord(long uniqueId, String message) {}
}
