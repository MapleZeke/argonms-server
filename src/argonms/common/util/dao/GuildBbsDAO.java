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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for guild BBS (bulletin board system) operations.
 *
 * <p>Handles topic/reply CRUD and the atomic getAndIncrement for
 * generating topic/reply IDs.
 */
public final class GuildBbsDAO {
	private static final Logger LOG = Logger.getLogger(GuildBbsDAO.class.getName());

	private static final Set<String> BBS_TABLES = Set.of("guilds", "guildbbstopics");
	private static final Set<String> BBS_FIELDS = Set.of("nextbbstopicid", "nextreplyid");
	private static final Set<String> BBS_KEYS = Set.of("id", "guildid", "topicid");

	private GuildBbsDAO() {
	}

	/**
	 * BBS reply record.
	 */
	public record BbsReply(int replyId, int poster, long postTime, String content) {}

	/**
	 * BBS topic record.
	 */
	public record BbsTopic(int topicId, int poster, long postTime, String subject,
			String content, int icon, List<BbsReply> replies) {}

	/**
	 * Atomically reads and increments a counter field in the given table.
	 * Used to generate unique topic/reply IDs.
	 *
	 * @param con        the connection (caller manages)
	 * @param table      the table name (must be in allowlist)
	 * @param field      the counter field name (must be in allowlist)
	 * @param tableKey1  the first key column (must be in allowlist)
	 * @param tableKey2  the optional second key column (may be null)
	 * @param keyValue1  the value for the first key
	 * @param keyValue2  the value for the second key (ignored if tableKey2 is null)
	 * @return the current value before increment, or -1 on failure
	 */
	public static int getAndIncrement(Connection con, String table, String field,
			String tableKey1, String tableKey2, int keyValue1, int keyValue2) {
		int value = -1;
		String safeTable = requireIdentifier(table, BBS_TABLES, "guild BBS table");
		String safeField = requireIdentifier(field, BBS_FIELDS, "guild BBS field");
		String safeTableKey1 = requireIdentifier(tableKey1, BBS_KEYS, "guild BBS key");
		String safeTableKey2 = tableKey2 == null ? null : requireIdentifier(tableKey2, BBS_KEYS, "guild BBS key");
		String whereClause = "WHERE `" + safeTableKey1 + "` = ?";
		if (safeTableKey2 != null) {
			whereClause += " AND `" + safeTableKey2 + "` = ?";
		}

		int prevTransactionIsolation = Connection.TRANSACTION_REPEATABLE_READ;
		boolean prevAutoCommit = true;
		try {
			prevTransactionIsolation = con.getTransactionIsolation();
			prevAutoCommit = con.getAutoCommit();
			con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			con.setAutoCommit(false);
			try (PreparedStatement ps = con.prepareStatement(
					"SELECT `" + safeField + "` FROM `" + safeTable + "` " + whereClause + " FOR UPDATE")) {
				ps.setInt(1, keyValue1);
				if (safeTableKey2 != null) {
					ps.setInt(2, keyValue2);
				}
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						return value;
					}
					value = rs.getInt(1);
				}
			}
			try (PreparedStatement ps = con.prepareStatement(
					"UPDATE `" + safeTable + "` SET `" + safeField + "` = `" + safeField + "` + 1 " + whereClause)) {
				ps.setInt(1, keyValue1);
				if (safeTableKey2 != null) {
					ps.setInt(2, keyValue2);
				}
				ps.executeUpdate();
			}
			con.commit();
			return value;
		} catch (Throwable ex) {
			LOG.log(Level.WARNING, "Could not get new ID for guild BBS. Rolling back all changes...", ex);
			if (con != null) {
				try {
					con.rollback();
				} catch (SQLException ex2) {
					LOG.log(Level.WARNING, "Error rolling back getAndIncrement.", ex2);
				}
			}
			return value;
		} finally {
			if (con != null) {
				try {
					con.setAutoCommit(prevAutoCommit);
					con.setTransactionIsolation(prevTransactionIsolation);
				} catch (SQLException ex) {
					LOG.log(Level.WARNING, "Could not reset Connection config after getAndIncrement", ex);
				}
			}
		}
	}

	/**
	 * Inserts or updates a topic starter (also handles notice topics).
	 *
	 * @param con      the connection (caller manages)
	 * @param guildId  the guild ID
	 * @param topicId  the topic ID (0 for notice, positive for regular)
	 * @param posterId the poster character ID
	 * @param postTime the post timestamp
	 * @param subject  the topic subject
	 * @param content  the topic content
	 * @param icon     the topic icon
	 * @param isNotice true if this is a notice (topicId=0, uses ON DUPLICATE KEY UPDATE)
	 * @throws SQLException if a database error occurs
	 */
	public static void insertOrUpdateTopic(Connection con, int guildId, int topicId,
			int posterId, long postTime, String subject, String content, int icon,
			boolean isNotice) throws SQLException {
		String query = "INSERT INTO `guildbbstopics` (`guildid`,`topicid`,`poster`,`posttime`,`subject`,`content`,`icon`) VALUES (?,?,?,?,?,?,?)";
		if (isNotice) {
			query += " ON DUPLICATE KEY UPDATE `posttime` = ?, `subject` = ?, `content` = ?, `icon` = ?";
		}
		try (PreparedStatement ps = con.prepareStatement(query)) {
			ps.setInt(1, guildId);
			ps.setInt(2, topicId);
			ps.setInt(3, posterId);
			ps.setLong(4, postTime);
			ps.setString(5, subject);
			ps.setString(6, content);
			ps.setInt(7, icon);
			if (isNotice) {
				ps.setLong(8, postTime);
				ps.setString(9, subject);
				ps.setString(10, content);
				ps.setInt(11, icon);
			}
			ps.executeUpdate();
		}
	}

	/**
	 * Updates an existing topic (edit by original poster or guild master/junior master).
	 *
	 * @param con       the connection (caller manages)
	 * @param guildId   the guild ID
	 * @param topicId   the topic ID
	 * @param posterId  the poster's character ID
	 * @param postTime  the new post timestamp
	 * @param subject   the new subject
	 * @param content   the new content
	 * @param icon      the new icon
	 * @param isAdmin   true if the editor is guild master or junior master
	 * @return number of rows updated (0 means no permission or not found)
	 * @throws SQLException if a database error occurs
	 */
	public static int updateTopic(Connection con, int guildId, int topicId,
			int posterId, long postTime, String subject, String content, int icon,
			boolean isAdmin) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE `guildbbstopics` SET `posttime` = ?, `subject` = ?, `content` = ?, `icon` = ? "
				+ "WHERE `guildid` = ? AND `topicid` = ? AND (`poster` = ? OR ?)")) {
			ps.setLong(1, postTime);
			ps.setString(2, subject);
			ps.setString(3, content);
			ps.setInt(4, icon);
			ps.setInt(5, guildId);
			ps.setInt(6, topicId);
			ps.setInt(7, posterId);
			ps.setBoolean(8, isAdmin);
			return ps.executeUpdate();
		}
	}

	/**
	 * Deletes a topic (by original poster or guild master/junior master).
	 *
	 * @param con      the connection (caller manages)
	 * @param guildId  the guild ID
	 * @param topicId  the topic ID
	 * @param posterId the requesting player's character ID
	 * @param isAdmin  true if the player is guild master or junior master
	 * @return number of rows deleted
	 * @throws SQLException if a database error occurs
	 */
	public static int deleteTopic(Connection con, int guildId, int topicId,
			int posterId, boolean isAdmin) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE FROM `guildbbstopics` WHERE `guildid` = ? AND `topicid` = ? AND (`poster` = ? OR ?)")) {
			ps.setInt(1, guildId);
			ps.setInt(2, topicId);
			ps.setInt(3, posterId);
			ps.setBoolean(4, isAdmin);
			return ps.executeUpdate();
		}
	}

	/**
	 * Inserts a reply to a topic.
	 *
	 * @param con      the connection (caller manages)
	 * @param guildId  the guild ID
	 * @param topicId  the topic ID
	 * @param replyId  the reply ID
	 * @param posterId the poster's character ID
	 * @param postTime the post timestamp
	 * @param content  the reply content
	 * @throws SQLException if a database error occurs
	 */
	public static void insertReply(Connection con, int guildId, int topicId,
			int replyId, int posterId, long postTime, String content) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `guildbbsreplies` (`topicsid`,`replyid`,`poster`,`posttime`,`content`) "
				+ "SELECT `topicsid`,?,?,?,? FROM `guildbbstopics` WHERE `guildid` = ? AND `topicid` = ?")) {
			ps.setInt(1, replyId);
			ps.setInt(2, posterId);
			ps.setLong(3, postTime);
			ps.setString(4, content);
			ps.setInt(5, guildId);
			ps.setInt(6, topicId);
			ps.executeUpdate();
		}
	}

	/**
	 * Deletes a reply (by original poster or guild master/junior master).
	 *
	 * @param con      the connection (caller manages)
	 * @param guildId  the guild ID
	 * @param topicId  the topic ID
	 * @param replyId  the reply ID
	 * @param posterId the requesting player's character ID
	 * @param isAdmin  true if the player is guild master or junior master
	 * @return number of rows deleted
	 * @throws SQLException if a database error occurs
	 */
	public static int deleteReply(Connection con, int guildId, int topicId,
			int replyId, int posterId, boolean isAdmin) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE `r` FROM `guildbbsreplies` `r` LEFT JOIN `guildbbstopics` `t` ON `r`.`topicsid` = `t`.`topicsid` "
				+ "WHERE `guildid` = ? AND `topicid` = ? AND `replyid` = ? AND (`r`.`poster` = ? OR ?)")) {
			ps.setInt(1, guildId);
			ps.setInt(2, topicId);
			ps.setInt(3, replyId);
			ps.setInt(4, posterId);
			ps.setBoolean(5, isAdmin);
			return ps.executeUpdate();
		}
	}

	/**
	 * Loads a single topic with its replies.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @param topicId the topic ID
	 * @return the topic, or null if not found
	 * @throws SQLException if a database error occurs
	 */
	public static BbsTopic loadTopic(Connection con, int guildId, int topicId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `topicsid`,`poster`,`posttime`,`subject`,`content`,`icon` "
				+ "FROM `guildbbstopics` WHERE `guildid` = ? AND `topicid` = ?")) {
			ps.setInt(1, guildId);
			ps.setInt(2, topicId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return buildTopic(con, rs, topicId);
			}
		}
	}

	/**
	 * Loads a page of topics (excluding notice) with their replies.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @param page    the zero-based page number (10 topics per page)
	 * @return list of topics
	 * @throws SQLException if a database error occurs
	 */
	public static List<BbsTopic> loadTopics(Connection con, int guildId, int page) throws SQLException {
		List<BbsTopic> topics = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `topicsid`,`poster`,`posttime`,`subject`,`content`,`icon`,`topicid` "
				+ "FROM `guildbbstopics` WHERE `guildid` = ? AND `topicid` <> 0 ORDER BY `topicid` DESC LIMIT ?,10")) {
			ps.setInt(1, guildId);
			ps.setInt(2, page * 10);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					topics.add(buildTopic(con, rs, rs.getInt(7)));
				}
			}
		}
		return Collections.unmodifiableList(topics);
	}

	/**
	 * Counts total non-notice topics for a guild.
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @return the total number of topics (excluding notice)
	 * @throws SQLException if a database error occurs
	 */
	public static int countTopics(Connection con, int guildId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT COUNT(*) FROM `guildbbstopics` WHERE `guildid` = ? AND `topicid` <> 0")) {
			ps.setInt(1, guildId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}

	/**
	 * Loads replies for a topic (by guildId and topicId lookup).
	 *
	 * @param con     the connection (caller manages)
	 * @param guildId the guild ID
	 * @param topicId the topic ID
	 * @return list of replies
	 * @throws SQLException if a database error occurs
	 */
	public static List<BbsReply> loadReplies(Connection con, int guildId, int topicId) throws SQLException {
		List<BbsReply> replies = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `replyid`,`r`.`poster`,`r`.`posttime`,`r`.`content` FROM `guildbbsreplies` `r` "
				+ "LEFT JOIN `guildbbstopics` `t` ON `r`.`topicsid` = `t`.`topicsid` "
				+ "WHERE `guildid` = ? AND `topicid` = ?")) {
			ps.setInt(1, guildId);
			ps.setInt(2, topicId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					replies.add(new BbsReply(rs.getInt(1), rs.getInt(2), rs.getLong(3), rs.getString(4)));
				}
			}
		}
		return Collections.unmodifiableList(replies);
	}

	// ---- Private helpers ----

	private static BbsTopic buildTopic(Connection con, ResultSet rs, int topicId) throws SQLException {
		int topicsId = rs.getInt(1);
		int poster = rs.getInt(2);
		long postTime = rs.getLong(3);
		String subject = rs.getString(4);
		String content = rs.getString(5);
		int icon = rs.getInt(6);

		List<BbsReply> replies = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `replyid`,`poster`,`posttime`,`content` FROM `guildbbsreplies` WHERE `topicsid` = ?")) {
			ps.setInt(1, topicsId);
			try (ResultSet rrs = ps.executeQuery()) {
				while (rrs.next()) {
					replies.add(new BbsReply(rrs.getInt(1), rrs.getInt(2), rrs.getLong(3), rrs.getString(4)));
				}
			}
		}
		return new BbsTopic(topicId, poster, postTime, subject, content, icon, Collections.unmodifiableList(replies));
	}

	private static String requireIdentifier(String value, Set<String> allowedValues, String description) {
		if (!allowedValues.contains(value)) {
			throw new IllegalArgumentException("Unsupported " + description + ": " + value);
		}
		return value;
	}
}
