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
 * Data Access Object for coupon operations (loading, saving, redemption tracking).
 *
 * <p>All public methods are stateless and either accept a {@link Connection}
 * for transaction participation or obtain their own from the pool.
 */
public final class CouponDAO {

	private CouponDAO() {
	}

	/**
	 * Immutable record holding coupon data loaded from the database.
	 */
	public record CouponRecord(int maplePoints, int mesos, int remainingUses, long expireDate) {}

	/**
	 * Loads the base coupon data for the given code.
	 *
	 * @param con  the connection (caller manages)
	 * @param code the coupon code
	 * @return the coupon record, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static CouponRecord loadCoupon(Connection con, String code) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `maplepoints`,`mesos`,`remaininguses`,`expiredate` "
				+ "FROM `cashshopcoupons` WHERE `code` = ?")) {
			ps.setString(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new CouponRecord(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getLong(4));
				}
				return null;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load coupon " + code, e);
		}
	}

	/**
	 * Loads the account IDs that have already used the given coupon.
	 *
	 * @param con  the connection (caller manages)
	 * @param code the coupon code
	 * @return list of account IDs
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<Integer> loadCouponUsers(Connection con, String code) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `accountid` FROM `cashshopcouponusers` `u` "
				+ "LEFT JOIN `cashshopcoupons` `c` ON `u`.`couponentryid` = `c`.`entryid` "
				+ "WHERE `c`.`code` = ?")) {
			ps.setString(1, code);
			List<Integer> users = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					users.add(rs.getInt(1));
				}
			}
			return users;
		} catch (SQLException e) {
			throw new DataAccessException("Could not load coupon users for " + code, e);
		}
	}

	/**
	 * Loads the item serial numbers associated with the given coupon.
	 *
	 * @param con  the connection (caller manages)
	 * @param code the coupon code
	 * @return list of serial numbers
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<Integer> loadCouponItems(Connection con, String code) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `sn` FROM `cashshopcouponitems` `i` "
				+ "LEFT JOIN `cashshopcoupons` `c` ON `i`.`couponentryid` = `c`.`entryid` "
				+ "WHERE `c`.`code` = ?")) {
			ps.setString(1, code);
			List<Integer> items = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					items.add(rs.getInt(1));
				}
			}
			return items;
		} catch (SQLException e) {
			throw new DataAccessException("Could not load coupon items for " + code, e);
		}
	}

	/**
	 * Inserts or updates coupon base data.
	 *
	 * @param con          the connection (caller manages)
	 * @param code         the coupon code
	 * @param maplePoints  the maple points reward
	 * @param mesos        the mesos reward
	 * @param remainingUses remaining uses
	 * @param expireDate   expiration date
	 * @throws DataAccessException if a database error occurs
	 */
	public static void upsertCoupon(Connection con, String code, int maplePoints,
			int mesos, int remainingUses, long expireDate) {
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `cashshopcoupons` (`code`,`maplepoints`,`mesos`,`remaininguses`,`expiredate`) "
				+ "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE "
				+ "`maplepoints` = ?, `mesos` = ?, `remaininguses` = ?, `expiredate` = ?")) {
			ps.setString(1, code);
			ps.setInt(2, maplePoints);
			ps.setInt(3, mesos);
			ps.setInt(4, remainingUses);
			ps.setLong(5, expireDate);
			ps.setInt(6, maplePoints);
			ps.setInt(7, mesos);
			ps.setInt(8, remainingUses);
			ps.setLong(9, expireDate);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not upsert coupon " + code, e);
		}
	}

	/**
	 * Replaces the users for a coupon (deletes all existing users and inserts new ones).
	 *
	 * @param con      the connection (caller manages)
	 * @param code     the coupon code
	 * @param userIds  the account IDs that have used the coupon
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceCouponUsers(Connection con, String code, Iterable<Integer> userIds) {
		try {
			try (PreparedStatement ps = con.prepareStatement(
					"DELETE `u`.* FROM `cashshopcouponusers` `u` "
					+ "LEFT JOIN `cashshopcoupons` `c` ON `u`.`couponentryid` = `c`.`entryid` "
					+ "WHERE `c`.`code` = ?")) {
				ps.setString(1, code);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement(
					"INSERT INTO `cashshopcouponusers` (`couponentryid`,`accountid`) "
					+ "SELECT `entryid`,? FROM `cashshopcoupons` WHERE `code` = ?")) {
				ps.setString(2, code);
				for (Integer accountId : userIds) {
					ps.setInt(1, accountId.intValue());
					ps.addBatch();
				}
				ps.executeBatch();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not replace coupon users for " + code, e);
		}
	}

	/**
	 * Replaces the items for a coupon (deletes all existing items and inserts new ones).
	 *
	 * @param con           the connection (caller manages)
	 * @param code          the coupon code
	 * @param serialNumbers the item serial numbers
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceCouponItems(Connection con, String code, Iterable<Integer> serialNumbers) {
		try {
			try (PreparedStatement ps = con.prepareStatement(
					"DELETE `i`.* FROM `cashshopcouponitems` `i` "
					+ "LEFT JOIN `cashshopcoupons` `c` ON `i`.`couponentryid` = `c`.`entryid` "
					+ "WHERE `c`.`code` = ?")) {
				ps.setString(1, code);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement(
					"INSERT INTO `cashshopcouponitems` (`couponentryid`,`sn`) "
					+ "SELECT `entryid`,? FROM `cashshopcoupons` WHERE `code` = ?")) {
				ps.setString(2, code);
				for (Integer sn : serialNumbers) {
					ps.setInt(1, sn.intValue());
					ps.addBatch();
				}
				ps.executeBatch();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not replace coupon items for " + code, e);
		}
	}
}
