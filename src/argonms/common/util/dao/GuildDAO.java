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
import java.util.logging.Logger;

/**
 * Data Access Object for guild operations (membership, properties, deletion).
 *
 * <p>All public methods are stateless and either accept a {@link Connection}
 * for transaction participation or obtain their own from the pool.
 */
public final class GuildDAO {
	private static final Logger LOG = Logger.getLogger(GuildDAO.class.getName());

	private GuildDAO() {
	}

	/**
	 * Immutable record of guild metadata loaded from the guilds table.
	 */
	public record GuildInfo(
		String name, String titles, byte capacity,
		short emblemBackground, byte emblemBackgroundColor,
		short emblemDesign, byte emblemDesignColor,
		String notice, int gp, int alliance
	) {}

	/**
	 * Immutable record of a guild member loaded from the database join.
	 */
	public record GuildMemberInfo(
		int characterId, String name, short job, short level,
		byte rank, byte signature, byte allianceRank
	) {}

	/**
	 * Loads guild metadata by guild ID.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @return the guild info, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static GuildInfo loadGuildInfo(Connection con, int guildId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `name`,`titles`,`capacity`,`emblemBackground`,`emblemBackgroundColor`,"
				+ "`emblemDesign`,`emblemDesignColor`,`notice`,`gp`,`alliance` "
				+ "FROM `guilds` WHERE `id` = ?")) {
			ps.setInt(1, guildId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return new GuildInfo(
					rs.getString(1), rs.getString(2), rs.getByte(3),
					rs.getShort(4), rs.getByte(5), rs.getShort(6), rs.getByte(7),
					rs.getString(8), rs.getInt(9), rs.getInt(10)
				);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load guild " + guildId, e);
		}
	}

	/**
	 * Loads all members of a guild.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @return the list of guild members
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<GuildMemberInfo> loadGuildMembers(Connection con, int guildId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `c`.`id`,`c`.`name`,`c`.`job`,`c`.`level`,`g`.`rank`,`g`.`signature`,`g`.`alliancerank` "
				+ "FROM `guildmembers` `g` LEFT JOIN `characters` `c` ON `c`.`id` = `g`.`characterid` "
				+ "WHERE `g`.`guildid` = ?")) {
			ps.setInt(1, guildId);
			List<GuildMemberInfo> members = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					members.add(new GuildMemberInfo(
						rs.getInt(1), rs.getString(2), rs.getShort(3), rs.getShort(4),
						rs.getByte(5), rs.getByte(6), rs.getByte(7)
					));
				}
			}
			return members;
		} catch (SQLException e) {
			throw new DataAccessException("Could not load guild members for guild " + guildId, e);
		}
	}

	/**
	 * Deletes a guild member entry.
	 *
	 * @param characterId the character ID to remove
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deleteGuildMember(int characterId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"DELETE FROM `guildmembers` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not delete guild member " + characterId, e);
		}
	}

	/**
	 * Deletes a guild member entry within an existing connection.
	 *
	 * @param con         the connection (caller manages)
	 * @param characterId the character ID to remove
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deleteGuildMember(Connection con, int characterId) {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE FROM `guildmembers` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not delete guild member " + characterId, e);
		}
	}

	/**
	 * Updates guild capacity.
	 *
	 * @param con      the connection (caller manages)
	 * @param guildId  the guild ID
	 * @param capacity the new capacity
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateCapacity(Connection con, int guildId, byte capacity) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `guilds` SET `capacity` = ? WHERE `id` = ?")) {
			ps.setByte(1, capacity);
			ps.setInt(2, guildId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not update guild capacity for guild " + guildId, e);
		}
	}

	/**
	 * Updates guild emblem.
	 *
	 * @param con             the connection (caller manages)
	 * @param guildId         the guild ID
	 * @param background      the emblem background
	 * @param backgroundColor the emblem background color
	 * @param design          the emblem design
	 * @param designColor     the emblem design color
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateEmblem(Connection con, int guildId, short background,
			byte backgroundColor, short design, byte designColor) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `guilds` SET `emblemBackground` = ?, `emblemBackgroundColor` = ?, "
				+ "`emblemDesign` = ?, `emblemDesignColor` = ? WHERE `id` = ?")) {
			ps.setShort(1, background);
			ps.setByte(2, backgroundColor);
			ps.setShort(3, design);
			ps.setByte(4, designColor);
			ps.setInt(5, guildId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not update guild emblem for guild " + guildId, e);
		}
	}

	/**
	 * Updates guild rank titles.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @param titles  the rank titles (comma-separated)
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateTitles(Connection con, int guildId, String titles) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `guilds` SET `titles` = ? WHERE `id` = ?")) {
			ps.setString(1, titles);
			ps.setInt(2, guildId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not update guild titles for guild " + guildId, e);
		}
	}

	/**
	 * Updates a guild member's rank.
	 *
	 * @param con         the connection (caller manages)
	 * @param guildId     the guild ID
	 * @param characterId the character ID
	 * @param rank        the new rank
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateMemberRank(Connection con, int guildId, int characterId, byte rank) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `guildmembers` SET `rank` = ? WHERE `guildid` = ? AND `characterid` = ?")) {
			ps.setByte(1, rank);
			ps.setInt(2, guildId);
			ps.setInt(3, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not update member rank for character " + characterId, e);
		}
	}

	/**
	 * Updates guild notice.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @param notice  the new notice text
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateNotice(Connection con, int guildId, String notice) {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `guilds` SET `notice` = ? WHERE `id` = ?")) {
			ps.setString(1, notice);
			ps.setInt(2, guildId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not update guild notice for guild " + guildId, e);
		}
	}

	/**
	 * Deletes a guild.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deleteGuild(Connection con, int guildId) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `guilds` WHERE `id` = ?")) {
			ps.setInt(1, guildId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not delete guild " + guildId, e);
		}
	}

	/**
	 * Checks whether a character is a guild master.
	 *
	 * @param characterId the character ID
	 * @return true if the character is rank 1 in any guild
	 * @throws DataAccessException if a database error occurs
	 */
	public static boolean isGuildMaster(int characterId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT EXISTS(SELECT 1 FROM `guildmembers` WHERE `characterid` = ? AND `rank` = 1 LIMIT 1)")) {
			ps.setInt(1, characterId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getBoolean(1);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not check guild master status for character " + characterId, e);
		}
	}
}
