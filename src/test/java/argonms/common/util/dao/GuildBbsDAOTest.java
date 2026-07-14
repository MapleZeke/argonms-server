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
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link GuildBbsDAO} using an in-memory H2 database.
 */
class GuildBbsDAOTest {

	@BeforeEach
	void setUp() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testGuildBbs;DB_CLOSE_DELAY=-1;MODE=MYSQL");
		props.setProperty("user", "sa");
		props.setProperty("password", "");
		DatabaseManager.setProps(props, false, false);

		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `guilds` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `name` VARCHAR(50), "
				+ "`titles` VARCHAR(200) DEFAULT '', `capacity` TINYINT DEFAULT 10, "
				+ "`emblemBackground` SMALLINT DEFAULT 0, `emblemBackgroundColor` TINYINT DEFAULT 0, "
				+ "`emblemDesign` SMALLINT DEFAULT 0, `emblemDesignColor` TINYINT DEFAULT 0, "
				+ "`notice` VARCHAR(200) DEFAULT '', `gp` INT DEFAULT 0, "
				+ "`alliance` INT DEFAULT 0, `nextbbstopicid` INT DEFAULT 1)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `guildbbstopics` ("
				+ "`topicsid` INT AUTO_INCREMENT PRIMARY KEY, `guildid` INT, "
				+ "`topicid` INT, `poster` INT, `posttime` BIGINT, "
				+ "`subject` VARCHAR(50), `content` VARCHAR(600), `icon` INT, "
				+ "`nextreplyid` INT DEFAULT 0, "
				+ "UNIQUE(`guildid`,`topicid`))"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `guildbbsreplies` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `topicsid` INT, "
				+ "`replyid` INT, `poster` INT, `posttime` BIGINT, "
				+ "`content` VARCHAR(50))"
			);
			// Insert test guild
			con.createStatement().execute(
				"INSERT INTO `guilds` (`id`,`name`,`nextbbstopicid`) VALUES (1,'TestGuild',1)"
			);
		}
	}

	@AfterEach
	void tearDown() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute("DROP TABLE IF EXISTS `guildbbsreplies`");
			con.createStatement().execute("DROP TABLE IF EXISTS `guildbbstopics`");
			con.createStatement().execute("DROP TABLE IF EXISTS `guilds`");
		}
		DatabaseManager.closeAll();
	}

	@Test
	void insertAndLoadTopic() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			long now = System.currentTimeMillis();
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 1, 100, now, "Hello", "World", 0, false);

			GuildBbsDAO.BbsTopic topic = GuildBbsDAO.loadTopic(con, 1, 1);
			assertNotNull(topic);
			assertEquals(1, topic.topicId());
			assertEquals(100, topic.poster());
			assertEquals("Hello", topic.subject());
			assertEquals("World", topic.content());
			assertEquals(0, topic.icon());
			assertTrue(topic.replies().isEmpty());
		}
	}

	@Test
	void loadTopic_nonexistent_returnsNull() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			GuildBbsDAO.BbsTopic topic = GuildBbsDAO.loadTopic(con, 1, 999);
			assertNull(topic);
		}
	}

	@Test
	void updateTopic_byOriginalPoster_succeeds() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			long now = System.currentTimeMillis();
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 1, 100, now, "Original", "Content", 0, false);

			int rows = GuildBbsDAO.updateTopic(con, 1, 1, 100, now + 1000, "Updated", "NewContent", 1, false);
			assertEquals(1, rows);

			GuildBbsDAO.BbsTopic topic = GuildBbsDAO.loadTopic(con, 1, 1);
			assertEquals("Updated", topic.subject());
			assertEquals("NewContent", topic.content());
		}
	}

	@Test
	void updateTopic_byNonPosterNonAdmin_returnsZero() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			long now = System.currentTimeMillis();
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 1, 100, now, "Original", "Content", 0, false);

			int rows = GuildBbsDAO.updateTopic(con, 1, 1, 999, now + 1000, "Hack", "Hacked", 0, false);
			assertEquals(0, rows);
		}
	}

	@Test
	void deleteTopic_byAdmin_succeeds() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			long now = System.currentTimeMillis();
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 1, 100, now, "ToDelete", "Content", 0, false);

			int rows = GuildBbsDAO.deleteTopic(con, 1, 1, 999, true); // admin
			assertEquals(1, rows);

			assertNull(GuildBbsDAO.loadTopic(con, 1, 1));
		}
	}

	@Test
	void insertReplyAndLoadReplies() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			long now = System.currentTimeMillis();
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 1, 100, now, "Topic", "Content", 0, false);

			GuildBbsDAO.insertReply(con, 1, 1, 0, 200, now + 1000, "First reply");
			GuildBbsDAO.insertReply(con, 1, 1, 1, 201, now + 2000, "Second reply");

			List<GuildBbsDAO.BbsReply> replies = GuildBbsDAO.loadReplies(con, 1, 1);
			assertEquals(2, replies.size());
			assertEquals("First reply", replies.get(0).content());
			assertEquals("Second reply", replies.get(1).content());
		}
	}

	@Test
	void loadTopics_paginates() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			long now = System.currentTimeMillis();
			// Insert 3 topics
			for (int i = 1; i <= 3; i++) {
				GuildBbsDAO.insertOrUpdateTopic(con, 1, i, 100, now + i, "Topic " + i, "Content " + i, 0, false);
			}

			List<GuildBbsDAO.BbsTopic> page0 = GuildBbsDAO.loadTopics(con, 1, 0);
			assertEquals(3, page0.size());
			// Should be ordered by topicId DESC
			assertEquals(3, page0.get(0).topicId());
			assertEquals(2, page0.get(1).topicId());
			assertEquals(1, page0.get(2).topicId());
		}
	}

	@Test
	void countTopics_excludesNotice() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			long now = System.currentTimeMillis();
			// Insert notice (topicId=0)
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 0, 100, now, "Notice", "Content", 0, true);
			// Insert regular topics
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 1, 100, now, "Topic1", "Content1", 0, false);
			GuildBbsDAO.insertOrUpdateTopic(con, 1, 2, 100, now, "Topic2", "Content2", 0, false);

			int count = GuildBbsDAO.countTopics(con, 1);
			assertEquals(2, count);
		}
	}
}
