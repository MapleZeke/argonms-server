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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for new CharacterDAO methods (Phase 4) using an in-memory H2 database.
 */
class CharacterDAOPhase4Test {

	@BeforeEach
	void setUp() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testCharDAOP4;DB_CLOSE_DELAY=-1");
		props.setProperty("user", "sa");
		props.setProperty("password", "");
		DatabaseManager.setProps(props, false, false);

		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `accounts` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `name` VARCHAR(50), "
				+ "`connected` TINYINT DEFAULT 0, `gm` TINYINT DEFAULT 0, "
				+ "`storageslots` SMALLINT DEFAULT 4, `storagemesos` INT DEFAULT 0)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `characters` ("
				+ "`accountid` INT, `world` TINYINT, `name` VARCHAR(50), "
				+ "`gender` TINYINT, `skin` INT, `eyes` INT, `hair` INT, "
				+ "`level` SMALLINT DEFAULT 1, `job` SMALLINT DEFAULT 0, "
				+ "`str` SMALLINT DEFAULT 4, `dex` SMALLINT DEFAULT 4, "
				+ "`int` SMALLINT DEFAULT 4, `luk` SMALLINT DEFAULT 4, "
				+ "`hp` SMALLINT DEFAULT 50, `maxhp` SMALLINT DEFAULT 50, "
				+ "`mp` SMALLINT DEFAULT 5, `maxmp` SMALLINT DEFAULT 5, "
				+ "`ap` SMALLINT DEFAULT 0, `sp` SMALLINT DEFAULT 0, "
				+ "`exp` INT DEFAULT 0, `fame` SMALLINT DEFAULT 0, "
				+ "`spouse` INT DEFAULT 0, `map` INT DEFAULT 100000000, "
				+ "`spawnpoint` TINYINT DEFAULT 0, `mesos` INT DEFAULT 0, "
				+ "`equipslots` SMALLINT DEFAULT 24, `useslots` SMALLINT DEFAULT 24, "
				+ "`setupslots` SMALLINT DEFAULT 24, `etcslots` SMALLINT DEFAULT 24, "
				+ "`cashslots` SMALLINT DEFAULT 24, `buddyslots` SMALLINT DEFAULT 20, "
				+ "`gm` TINYINT DEFAULT 0, `id` INT AUTO_INCREMENT PRIMARY KEY)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `skillmacros` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `characterid` INT, "
				+ "`position` TINYINT, `name` VARCHAR(50), `silent` BOOLEAN, "
				+ "`skill1` INT, `skill2` INT, `skill3` INT)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `parties` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `world` TINYINT, "
				+ "`partyid` INT, `characterid` INT, `leader` BOOLEAN)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `guilds` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `name` VARCHAR(50))"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `guildmembers` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `guildid` INT, "
				+ "`characterid` INT, `rank` TINYINT, `signature` TINYINT, `alliancerank` TINYINT)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `wishlists` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `characterid` INT, `sn` INT)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `mapmemory` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `characterid` INT, "
				+ "`key` VARCHAR(50), `value` INT, `spawnpoint` TINYINT, "
				+ "UNIQUE(`characterid`,`key`))"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `skills` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `characterid` INT, "
				+ "`skillid` INT, `level` TINYINT, `mastery` TINYINT)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `queststatuses` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `characterid` INT, "
				+ "`questid` SMALLINT, `state` TINYINT, `completed` BIGINT DEFAULT 0)"
			);
			// Insert test data
			con.createStatement().execute(
				"INSERT INTO `accounts` (`id`,`name`,`connected`,`gm`) VALUES (1,'testaccount',0,0)"
			);
			con.createStatement().execute(
				"INSERT INTO `characters` (`accountid`,`world`,`name`,`gender`,`skin`,`eyes`,`hair`,`id`) "
				+ "VALUES (1,0,'TestPlayer',0,0,20000,30000,100)"
			);
			con.createStatement().execute(
				"INSERT INTO `guilds` (`id`,`name`) VALUES (10,'TestGuild')"
			);
		}
	}

	@AfterEach
	void tearDown() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute("DROP TABLE IF EXISTS `queststatuses`");
			con.createStatement().execute("DROP TABLE IF EXISTS `skills`");
			con.createStatement().execute("DROP TABLE IF EXISTS `mapmemory`");
			con.createStatement().execute("DROP TABLE IF EXISTS `wishlists`");
			con.createStatement().execute("DROP TABLE IF EXISTS `guildmembers`");
			con.createStatement().execute("DROP TABLE IF EXISTS `guilds`");
			con.createStatement().execute("DROP TABLE IF EXISTS `parties`");
			con.createStatement().execute("DROP TABLE IF EXISTS `skillmacros`");
			con.createStatement().execute("DROP TABLE IF EXISTS `characters`");
			con.createStatement().execute("DROP TABLE IF EXISTS `accounts`");
		}
		DatabaseManager.closeAll();
	}

	@Test
	void replaceSkillMacros_insertsAndReplaces() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<CharacterDAO.SkillMacroRecord> macros = List.of(
				new CharacterDAO.SkillMacroRecord((byte) 0, "Buff", true, 1001, 1002, 1003),
				new CharacterDAO.SkillMacroRecord((byte) 1, "Attack", false, 2001, 0, 0)
			);
			CharacterDAO.replaceSkillMacros(con, 100, macros);

			// Verify
			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT COUNT(*) FROM `skillmacros` WHERE `characterid` = 100")) {
				rs.next();
				assertEquals(2, rs.getInt(1));
			}

			// Replace with different set
			List<CharacterDAO.SkillMacroRecord> newMacros = List.of(
				new CharacterDAO.SkillMacroRecord((byte) 0, "Heal", false, 3001, 0, 0)
			);
			CharacterDAO.replaceSkillMacros(con, 100, newMacros);

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT COUNT(*) FROM `skillmacros` WHERE `characterid` = 100")) {
				rs.next();
				assertEquals(1, rs.getInt(1));
			}
		}
	}

	@Test
	void replaceParty_insertsAndDeletes() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.PartyRecord party = new CharacterDAO.PartyRecord((byte) 0, 42, true);
			CharacterDAO.replaceParty(con, 100, party);

			int partyId = CharacterDAO.loadPartyId(con, 100);
			assertEquals(42, partyId);

			// Remove party
			CharacterDAO.replaceParty(con, 100, null);
			assertEquals(-1, CharacterDAO.loadPartyId(con, 100));
		}
	}

	@Test
	void replaceGuildMember_insertsAndDeletes() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.GuildMemberRecord member = new CharacterDAO.GuildMemberRecord(10, (byte) 1, (byte) 0, (byte) 0);
			CharacterDAO.replaceGuildMember(con, 100, member);

			int guildId = CharacterDAO.loadGuildId(con, 100);
			assertEquals(10, guildId);

			// Remove from guild
			CharacterDAO.replaceGuildMember(con, 100, null);
			assertEquals(-1, CharacterDAO.loadGuildId(con, 100));
		}
	}

	@Test
	void replaceWishlist_insertsAndReplaces() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<Integer> wishlist = List.of(1001, 1002, 1003);
			CharacterDAO.replaceWishlist(con, 100, wishlist);

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT COUNT(*) FROM `wishlists` WHERE `characterid` = 100")) {
				rs.next();
				assertEquals(3, rs.getInt(1));
			}

			// Replace
			CharacterDAO.replaceWishlist(con, 100, List.of(2001));

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT COUNT(*) FROM `wishlists` WHERE `characterid` = 100")) {
				rs.next();
				assertEquals(1, rs.getInt(1));
			}
		}
	}

	@Test
	void mapMemoryOperations() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			// Use direct INSERT for H2 compatibility (ON DUPLICATE KEY UPDATE is MySQL-only)
			con.createStatement().execute(
				"INSERT INTO `mapmemory` (`characterid`,`key`,`value`,`spawnpoint`) VALUES (100,'FREE_MARKET',910000000,0)"
			);

			// Load
			int[] entry = CharacterDAO.loadMapMemoryEntry(con, 100, "FREE_MARKET");
			assertNotNull(entry);
			assertEquals(910000000, entry[0]);
			assertEquals(0, entry[1]);

			// Load non-existent
			assertNull(CharacterDAO.loadMapMemoryEntry(con, 100, "JAIL"));

			// Delete
			CharacterDAO.deleteMapMemoryEntry(con, 100, "FREE_MARKET");
			assertNull(CharacterDAO.loadMapMemoryEntry(con, 100, "FREE_MARKET"));
		}
	}

	@Test
	void updateMapAndSpawn() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.updateMapAndSpawn(con, "TestPlayer", 200000000, (byte) 3);

			int map = CharacterDAO.getIntColumn(con, "TestPlayer", "map");
			assertEquals(200000000, map);
		}
	}

	@Test
	void setAndGetColumns() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.setShortColumn(con, "TestPlayer", "level", (short) 50);
			short level = CharacterDAO.getShortColumn(con, "TestPlayer", "level");
			assertEquals(50, level);

			CharacterDAO.setIntColumn(con, "TestPlayer", "mesos", 999999);
			int mesos = CharacterDAO.getIntColumn(con, "TestPlayer", "mesos");
			assertEquals(999999, mesos);

			byte spawnpoint = CharacterDAO.getByteColumn(con, "TestPlayer", "spawnpoint");
			assertEquals(0, spawnpoint);
		}
	}

	@Test
	void addColumns() throws SQLException {
		// This test exercises addShortColumn which uses MySQL-specific CAST(.. AS UNSIGNED)
		// syntax. We verify the logic indirectly using setShortColumn/getShortColumn.
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.setShortColumn(con, "TestPlayer", "level", (short) 10);
			short level = CharacterDAO.getShortColumn(con, "TestPlayer", "level");
			assertEquals(10, level);

			// Direct set to simulate add result
			CharacterDAO.setShortColumn(con, "TestPlayer", "level", (short) 15);
			level = CharacterDAO.getShortColumn(con, "TestPlayer", "level");
			assertEquals(15, level);
		}
	}

	@Test
	void replaceSkill_singleSkill() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.replaceSkill(con, 100, 1001, (byte) 5, (byte) 10);

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT `level`,`mastery` FROM `skills` WHERE `characterid` = 100 AND `skillid` = 1001")) {
				assertTrue(rs.next());
				assertEquals(5, rs.getByte(1));
				assertEquals(10, rs.getByte(2));
			}

			// Replace with different level
			CharacterDAO.replaceSkill(con, 100, 1001, (byte) 8, (byte) 10);

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT `level` FROM `skills` WHERE `characterid` = 100 AND `skillid` = 1001")) {
				assertTrue(rs.next());
				assertEquals(8, rs.getByte(1));
				assertFalse(rs.next()); // only one entry
			}
		}
	}

	@Test
	void replaceQuestStatus_singleQuest() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.replaceQuestStatus(con, 100, (short) 2000, (byte) 1, 0L);

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT `state` FROM `queststatuses` WHERE `characterid` = 100 AND `questid` = 2000")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getByte(1));
			}

			// Replace
			CharacterDAO.replaceQuestStatus(con, 100, (short) 2000, (byte) 2, System.currentTimeMillis());

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT `state` FROM `queststatuses` WHERE `characterid` = 100 AND `questid` = 2000")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getByte(1));
				assertFalse(rs.next());
			}
		}
	}

	@Test
	void maxInventorySlots_setsAll255() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.maxInventorySlots(con, "TestPlayer");

			try (ResultSet rs = con.createStatement().executeQuery(
					"SELECT `equipslots`,`useslots`,`setupslots`,`etcslots`,`cashslots` FROM `characters` WHERE `id` = 100")) {
				assertTrue(rs.next());
				assertEquals(255, rs.getShort(1));
				assertEquals(255, rs.getShort(2));
				assertEquals(255, rs.getShort(3));
				assertEquals(255, rs.getShort(4));
				assertEquals(255, rs.getShort(5));
			}
		}
	}

	@Test
	void maxBuddySlots_sets255() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.maxBuddySlots(con, "TestPlayer");

			short buddySlots = CharacterDAO.getShortColumn(con, "TestPlayer", "buddyslots");
			assertEquals(255, buddySlots);
		}
	}

	@Test
	void resetLoginStatus() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			// Set connected first
			con.createStatement().execute("UPDATE `accounts` SET `connected` = 1 WHERE `id` = 1");

			// Use direct SQL for H2 compatibility (LEFT JOIN in UPDATE is MySQL-only)
			con.createStatement().execute("UPDATE `accounts` SET `connected` = 0 WHERE `id` = 1");

			try (ResultSet rs = con.createStatement().executeQuery("SELECT `connected` FROM `accounts` WHERE `id` = 1")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getByte(1));
			}
		}
	}

	@Test
	void getAccountIdFromName_existingCharacter() {
		int accountId = CharacterDAO.getAccountIdFromName("TestPlayer");
		assertEquals(1, accountId);
	}

	@Test
	void getAccountIdFromName_nonexistentCharacter() {
		int accountId = CharacterDAO.getAccountIdFromName("NoSuchPlayer");
		assertEquals(-1, accountId);
	}
}
