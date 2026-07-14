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

import argonms.common.net.external.CheatTracker;
import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for ban and infraction operations.
 *
 * <p>All public methods are stateless and either accept a {@link Connection}
 * for transaction participation or obtain their own from the pool.
 */
public final class BanDAO {
	private static final Logger LOG = Logger.getLogger(BanDAO.class.getName());

	private BanDAO() {
	}

	/**
	 * Result of computing ban status from active infractions.
	 */
	public record BanStatus(long banExpire, byte banReason) {}

	/**
	 * Loads ban status for the given account and IP address.
	 * Queries bans matching the account ID or IP, evaluates active infractions,
	 * and releases (deletes) bans whose infractions have all expired.
	 *
	 * @param con       the connection (caller manages)
	 * @param accountId the account ID
	 * @param ipAddress the IP address as a long
	 * @return the computed ban status
	 * @throws SQLException if a database error occurs
	 */
	public static BanStatus loadBanStatusFromIdAndIp(Connection con, int accountId, long ipAddress) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `banid`,`accountid` FROM `bans` WHERE `accountid` = ? OR `ip` = ?")) {
			ps.setInt(1, accountId);
			ps.setLong(2, ipAddress);
			try (ResultSet rs = ps.executeQuery()) {
				return computeBanStatus(con, rs);
			}
		}
	}

	/**
	 * Loads ban status for a specific ban ID.
	 *
	 * @param con   the connection (caller manages)
	 * @param banId the ban ID
	 * @return the computed ban status
	 * @throws SQLException if a database error occurs
	 */
	public static BanStatus loadBanStatusFromBanId(Connection con, int banId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `banid`,`accountid` FROM `bans` WHERE `banid` = ?")) {
			ps.setInt(1, banId);
			try (ResultSet rs = ps.executeQuery()) {
				return computeBanStatus(con, rs);
			}
		}
	}

	/**
	 * Creates a ban entry for the given account and IP.
	 *
	 * @param con       the connection (caller manages)
	 * @param accountId the account ID
	 * @param ipAddress the IP as a long
	 * @return the generated ban ID
	 * @throws SQLException if a database error occurs
	 */
	public static int insertBan(Connection con, int accountId, long ipAddress) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `bans` (`accountid`,`ip`) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, accountId);
			ps.setLong(2, ipAddress);
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				return rs.next() ? rs.getInt(1) : -1;
			}
		}
	}

	/**
	 * Inserts MAC ban entries for the given ban.
	 *
	 * @param con              the connection (caller manages)
	 * @param banId            the ban ID
	 * @param macListCombined  all MAC addresses concatenated (6 bytes each)
	 * @param excludeCheck     predicate to exclude certain MACs (may be null)
	 * @throws SQLException if a database error occurs
	 */
	public static void insertMacBans(Connection con, int banId, byte[] macListCombined,
			MacExcludeCheck excludeCheck) throws SQLException {
		if (macListCombined == null) {
			return;
		}
		int macCount = macListCombined.length / 6;
		byte[] macAddress = new byte[6];
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `macbans` (`banid`,`mac`) VALUES (?,?)")) {
			ps.setInt(1, banId);
			for (int i = 0; i < macCount; i++) {
				System.arraycopy(macListCombined, i * 6, macAddress, 0, 6);
				if (excludeCheck == null || !excludeCheck.shouldExclude(macAddress)) {
					ps.setBytes(2, macAddress.clone());
					ps.addBatch();
				}
			}
			ps.executeBatch();
		}
	}

	/**
	 * Loads recent MAC addresses for the given account.
	 *
	 * @param con       the connection (caller manages)
	 * @param accountId the account ID
	 * @return the combined MAC bytes, or null
	 * @throws SQLException if a database error occurs
	 */
	public static byte[] loadRecentMacs(Connection con, int accountId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `recentmacs` FROM `accounts` WHERE `id` = ?")) {
			ps.setInt(1, accountId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getBytes(1) : null;
			}
		}
	}

	/**
	 * Callback interface to check whether a MAC address should be excluded from banning.
	 */
	@FunctionalInterface
	public interface MacExcludeCheck {
		boolean shouldExclude(byte[] mac);
	}

	private static BanStatus computeBanStatus(Connection con, ResultSet rs) throws SQLException {
		EnumMap<CheatTracker.Infraction, Integer> infractionPoints = new EnumMap<>(CheatTracker.Infraction.class);
		int highestPoints = 0;
		CheatTracker.Infraction mainBanReason = null;
		long banExpire = 0;

		try (PreparedStatement ips = con.prepareStatement(
				"SELECT `expiredate`,`reason`,`severity` FROM `infractions` "
				+ "WHERE `accountid` = ? AND `pardoned` = 0 AND `expiredate` > (UNIX_TIMESTAMP() * 1000) "
				+ "ORDER BY `expiredate` DESC");
				PreparedStatement rbps = con.prepareStatement("DELETE FROM `bans` WHERE `banid` = ?")) {
			while (rs.next()) {
				boolean release = true;
				int totalPoints = 0;
				ips.setInt(1, rs.getInt(2));
				try (ResultSet irs = ips.executeQuery()) {
					while (irs.next()) {
						long infractionExpire = irs.getLong(1);
						CheatTracker.Infraction infractionReason = CheatTracker.Infraction.valueOf(irs.getByte(2));
						short severity = irs.getShort(3);

						if (release && (totalPoints += severity) >= CheatTracker.TOLERANCE) {
							banExpire = infractionExpire;
							release = false;
						}

						Integer runningPoints = infractionPoints.get(infractionReason);
						int updatedPoints = (runningPoints != null ? runningPoints.intValue() : 0) + severity;
						infractionPoints.put(infractionReason, Integer.valueOf(updatedPoints));
						if (updatedPoints > highestPoints) {
							highestPoints = updatedPoints;
							mainBanReason = infractionReason;
						}
					}
				}
				if (release) {
					rbps.setInt(1, rs.getInt(1));
					rbps.addBatch();
				}
			}
			rbps.executeBatch();
		}
		byte reason = mainBanReason == null ? 0 : mainBanReason.byteValue();
		return new BanStatus(banExpire, reason);
	}
}
