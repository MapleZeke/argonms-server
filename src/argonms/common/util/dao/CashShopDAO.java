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
import java.util.logging.Logger;

/**
 * Data Access Object for cash shop operations (purchases, gifts, best items).
 *
 * <p>All public methods are stateless, either accept a {@link Connection} for
 * transaction participation or obtain their own from the pool, and throw
 * {@link DataAccessException} on failure.
 */
public final class CashShopDAO {
	private static final Logger LOG = Logger.getLogger(CashShopDAO.class.getName());

	private CashShopDAO() {
	}

	/**
	 * Loads purchase properties for a specific cash item.
	 *
	 * @param uniqueId the unique item ID
	 * @return the purchase properties record, or {@code null} if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static PurchaseProperties loadPurchaseProperties(long uniqueId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement(
				"SELECT `purchaseracctid`,`gifterchrname`,`serialnumber` "
				+ "FROM `cashshoppurchases` WHERE `uniqueid` = ?")) {
			ps.setLong(1, uniqueId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new PurchaseProperties(
						rs.getInt("purchaseracctid"),
						rs.getString("gifterchrname"),
						rs.getInt("serialnumber")
					);
				}
				return null;
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not load purchase properties for uniqueId " + uniqueId, ex);
		}
	}

	/**
	 * Loads purchase properties within an existing connection.
	 *
	 * @param con      the connection (caller manages lifecycle)
	 * @param uniqueId the unique item ID
	 * @return the purchase properties record, or {@code null} if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static PurchaseProperties loadPurchaseProperties(Connection con, long uniqueId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `purchaseracctid`,`gifterchrname`,`serialnumber` "
				+ "FROM `cashshoppurchases` WHERE `uniqueid` = ?")) {
			ps.setLong(1, uniqueId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new PurchaseProperties(
						rs.getInt("purchaseracctid"),
						rs.getString("gifterchrname"),
						rs.getInt("serialnumber")
					);
				}
				return null;
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not load purchase properties for uniqueId " + uniqueId, ex);
		}
	}

	/**
	 * Updates purchase properties for a specific cash item.
	 *
	 * @param uniqueId           the unique item ID
	 * @param purchaserAccountId the purchaser's account ID
	 * @param gifterName         the gifter character name (null if self-purchase)
	 * @param serialNumber       the commodity serial number
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updatePurchaseProperties(long uniqueId, int purchaserAccountId,
			String gifterName, int serialNumber) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement(
				"UPDATE `cashshoppurchases` SET `purchaseracctid` = ?, "
				+ "`gifterchrname` = ?, `serialnumber` = ? WHERE `uniqueid` = ?")) {
			ps.setInt(1, purchaserAccountId);
			ps.setString(2, gifterName);
			ps.setInt(3, serialNumber);
			ps.setLong(4, uniqueId);
			ps.executeUpdate();
		} catch (SQLException ex) {
			throw new DataAccessException("Could not update purchase properties for uniqueId " + uniqueId, ex);
		}
	}

	/**
	 * Loads gift notifications for the given account, deleting them after read.
	 * Uses table-level locking on {@code cashitemgiftnotes} to prevent races.
	 *
	 * @param con       the connection (caller manages lifecycle)
	 * @param accountId the recipient account ID
	 * @param handler   callback to handle each gift notification
	 * @throws DataAccessException if a database error occurs
	 */
	public static void consumeGiftNotifications(Connection con, int accountId,
			GiftNotificationHandler handler) {
		boolean locked = false;
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
						handler.onGiftNotification(rs.getLong(1), rs.getString(2));
					}
				}
			}

			try (PreparedStatement ps = con.prepareStatement(
					"DELETE FROM `cashitemgiftnotes` WHERE `recipientacctid` = ?")) {
				ps.setInt(1, accountId);
				ps.executeUpdate();
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not consume gift notifications for account " + accountId, ex);
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
	 * Inserts gift note entries (used when gifting to offline players).
	 * Acquires and releases a table lock on {@code cashitemgiftnotes}.
	 *
	 * @param con             the connection (caller manages lifecycle)
	 * @param recipientAcctId the recipient account ID
	 * @param message         the gift message
	 * @param uniqueIds       the unique IDs of the gifted items
	 * @throws DataAccessException if a database error occurs
	 */
	public static void insertGiftNotes(Connection con, int recipientAcctId, String message, long[] uniqueIds) {
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
				for (long uniqueId : uniqueIds) {
					ps.setLong(1, uniqueId);
					ps.addBatch();
				}
				ps.executeBatch();
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not insert gift notes for account " + recipientAcctId, ex);
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
	 * Gets the next available position in the cash shop staging inventory for
	 * an account.
	 *
	 * @param con       the connection (caller manages lifecycle)
	 * @param accountId the account ID
	 * @param cashShopInventoryType the cash shop inventory type byte value
	 * @return the next position (max + 1, or 1 if empty)
	 * @throws DataAccessException if a database error occurs
	 */
	public static short getNextPosition(Connection con, int accountId, byte cashShopInventoryType) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT MAX(`position`) FROM `inventoryitems` WHERE `accountid` = ? AND `inventorytype` = ?")) {
			ps.setInt(1, accountId);
			ps.setByte(2, cashShopInventoryType);
			try (ResultSet rs = ps.executeQuery()) {
				return (short) ((rs.next() ? rs.getShort(1) : 0) + 1);
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not get next position for account " + accountId, ex);
		}
	}

	/**
	 * Retrieves the top 5 best-selling cash items by purchase count.
	 *
	 * @return array of up to 5 serial numbers (0-filled if fewer than 5 exist)
	 * @throws DataAccessException if a database error occurs
	 */
	public static int[] getBestItems() {
		int[] bestItems = new int[5];
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 PreparedStatement ps = con.prepareStatement(
				"SELECT `serialnumber` FROM `cashshoppurchases` WHERE `serialnumber` IS NOT NULL "
				+ "GROUP BY `serialnumber` ORDER BY COUNT(*) DESC LIMIT 5")) {
			try (ResultSet rs = ps.executeQuery()) {
				for (int i = 0; rs.next(); i++) {
					bestItems[i] = rs.getInt(1);
				}
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not get best cash items from database", ex);
		}
		return bestItems;
	}

	/**
	 * Finds character IDs belonging to the given account.
	 *
	 * @param con       the connection (caller manages lifecycle)
	 * @param accountId the account ID
	 * @return array of character IDs
	 * @throws DataAccessException if a database error occurs
	 */
	public static int[] findCharactersByAccount(Connection con, int accountId) {
		try (PreparedStatement ps = con.prepareStatement("SELECT `id` FROM `characters` WHERE `accountid` = ?")) {
			ps.setInt(1, accountId);
			try (ResultSet rs = ps.executeQuery()) {
				java.util.List<Integer> ids = new java.util.ArrayList<>();
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
				return ids.stream().mapToInt(Integer::intValue).toArray();
			}
		} catch (SQLException ex) {
			throw new DataAccessException("Could not find characters for account " + accountId, ex);
		}
	}

	// ---- Record types ----

	/**
	 * Cash item purchase properties.
	 */
	public record PurchaseProperties(int purchaserAccountId, String gifterName, int serialNumber) {}

	/**
	 * Handler callback for gift notification consumption.
	 */
	@FunctionalInterface
	public interface GiftNotificationHandler {
		void onGiftNotification(long uniqueId, String message);
	}
}
