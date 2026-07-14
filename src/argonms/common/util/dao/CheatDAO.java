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
import java.sql.Types;
import java.util.logging.Logger;

/**
 * Data Access Object for cheat tracking and infraction operations.
 *
 * <p>All public methods are stateless and either accept a {@link Connection}
 * for transaction participation or obtain their own from the pool.
 */
public final class CheatDAO {
	private static final Logger LOG = Logger.getLogger(CheatDAO.class.getName());

	private CheatDAO() {
	}

	/**
	 * Loads total active infraction points for an account.
	 *
	 * @param accountId the account ID
	 * @return the total severity points of non-expired, non-pardoned infractions
	 * @throws DataAccessException if a database error occurs
	 */
	public static int loadActiveInfractionPoints(int accountId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT `severity` FROM `infractions` "
						+ "WHERE `accountid` = ? AND `pardoned` = 0 AND `expiredate` > (UNIX_TIMESTAMP() * 1000)")) {
			ps.setInt(1, accountId);
			int total = 0;
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					total += rs.getShort(1);
				}
			}
			return total;
		} catch (SQLException e) {
			throw new DataAccessException("Could not load infraction points for account " + accountId, e);
		}
	}

	/**
	 * Inserts a new infraction record.
	 *
	 * @param accountId      the account ID
	 * @param characterId    the character ID, or -1 if not applicable
	 * @param receiveDate    timestamp when the infraction was received
	 * @param expireDate     timestamp when the infraction expires
	 * @param assignerType   the assigner type string (e.g. "gm warning", "machine detected")
	 * @param assignerName   the name of the assigner
	 * @param comment        the comment/reason text
	 * @param reason         the infraction reason byte
	 * @param severity       the severity points
	 * @throws DataAccessException if a database error occurs
	 */
	public static void insertInfraction(int accountId, int characterId, long receiveDate,
			long expireDate, String assignerType, String assignerName, String comment,
			byte reason, short severity) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"INSERT INTO `infractions` (`accountid`,`characterid`,`receivedate`,`expiredate`,"
						+ "`assignertype`,`assignername`,`assignercomment`,`reason`,`severity`) "
						+ "VALUES (?,?,?,?,?,?,?,?,?)")) {
			ps.setInt(1, accountId);
			if (characterId != -1) {
				ps.setInt(2, characterId);
			} else {
				ps.setNull(2, Types.INTEGER);
			}
			ps.setLong(3, receiveDate);
			ps.setLong(4, expireDate);
			ps.setString(5, assignerType);
			ps.setString(6, assignerName);
			ps.setString(7, comment);
			ps.setByte(8, reason);
			ps.setShort(9, severity);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not insert infraction for account " + accountId, e);
		}
	}

	/**
	 * Looks up account, character, and recent IP for an offline character by name.
	 *
	 * @param characterName the character name
	 * @return an OfflineCharacterInfo record, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static OfflineCharacterInfo lookupOfflineCharacter(String characterName) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT `a`.`id`,`c`.`id`,`a`.`recentip` "
						+ "FROM `characters` `c` LEFT JOIN `accounts` `a` ON `c`.`accountid` = `a`.`id` "
						+ "WHERE `c`.`name` = ?")) {
			ps.setString(1, characterName);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return new OfflineCharacterInfo(rs.getInt(1), rs.getInt(2), rs.getLong(3));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not look up offline character " + characterName, e);
		}
	}

	/**
	 * Immutable record of offline character data needed for cheat tracking.
	 */
	public record OfflineCharacterInfo(int accountId, int characterId, long recentIp) {}
}
