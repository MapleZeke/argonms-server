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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link CharacterDAO} using an in-memory H2 database.
 */
class CharacterDAOTest {

	@BeforeEach
	void setUp() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testCharDAO;DB_CLOSE_DELAY=-1");
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
				+ "`spouse` INT DEFAULT 0, `map` INT DEFAULT 0, "
				+ "`spawnpoint` TINYINT DEFAULT 0, `mesos` INT DEFAULT 0, "
				+ "`equipslots` SMALLINT DEFAULT 24, `useslots` SMALLINT DEFAULT 24, "
				+ "`setupslots` SMALLINT DEFAULT 24, `etcslots` SMALLINT DEFAULT 24, "
				+ "`cashslots` SMALLINT DEFAULT 24, `buddyslots` SMALLINT DEFAULT 20, "
				+ "`gm` TINYINT DEFAULT 0, `id` INT AUTO_INCREMENT PRIMARY KEY)"
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
				"INSERT INTO `characters` (`accountid`,`world`,`name`,`gender`,`skin`,`eyes`,`hair`,`id`) "
				+ "VALUES (1,1,'OtherWorld',0,0,20000,30000,101)"
			);
		}
	}

	@AfterEach
	void tearDown() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute("DROP TABLE IF EXISTS `characters`");
			con.createStatement().execute("DROP TABLE IF EXISTS `accounts`");
		}
		DatabaseManager.closeAll();
	}

	@Test
	void getNameFromId_existingCharacter_returnsName() {
		String name = CharacterDAO.getNameFromId(100);
		assertEquals("TestPlayer", name);
	}

	@Test
	void getNameFromId_nonexistentCharacter_returnsNull() {
		String name = CharacterDAO.getNameFromId(9999);
		assertNull(name);
	}

	@Test
	void getIdFromName_existingCharacter_returnsId() {
		int id = CharacterDAO.getIdFromName("TestPlayer");
		assertEquals(100, id);
	}

	@Test
	void getIdFromName_nonexistentCharacter_returnsNegativeOne() {
		int id = CharacterDAO.getIdFromName("NonExistent");
		assertEquals(-1, id);
	}

	@Test
	void characterExists_existingCharacterOnWorld_returnsTrue() {
		assertTrue(CharacterDAO.characterExists("TestPlayer", (byte) 0));
	}

	@Test
	void characterExists_existingCharacterWrongWorld_returnsFalse() {
		assertFalse(CharacterDAO.characterExists("TestPlayer", (byte) 1));
	}

	@Test
	void characterExists_nonexistentCharacter_returnsFalse() {
		assertFalse(CharacterDAO.characterExists("NoSuchPlayer", (byte) 0));
	}

	@Test
	void lookupByName_existingCharacter_returnsLookup() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.CharacterLookup lookup = CharacterDAO.lookupByName(con, "TestPlayer");
			assertNotNull(lookup);
			assertEquals(100, lookup.characterId());
			assertEquals("TestPlayer", lookup.name());
			assertEquals(0, lookup.world());
		}
	}

	@Test
	void lookupByName_nonexistent_returnsNull() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.CharacterLookup lookup = CharacterDAO.lookupByName(con, "Ghost");
			assertNull(lookup);
		}
	}

	@Test
	void createCharacter_returnsGeneratedId() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			CharacterDAO.NewCharacterRecord rec = new CharacterDAO.NewCharacterRecord(
				1, (byte) 0, "NewChar", (byte) 0, 0, 20000, 30000,
				(short) 4, (short) 4, (short) 4, (short) 4, (byte) 0
			);
			int id = CharacterDAO.createCharacter(con, rec);
			assertTrue(id > 0);

			// Verify it exists
			CharacterDAO.CharacterLookup lookup = CharacterDAO.lookupByName(con, "NewChar");
			assertNotNull(lookup);
			assertEquals(id, lookup.characterId());
		}
	}
}
