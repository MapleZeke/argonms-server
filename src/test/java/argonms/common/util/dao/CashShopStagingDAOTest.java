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
 * Integration tests for {@link CashShopStagingDAO} using an in-memory H2 database.
 */
class CashShopStagingDAOTest {

	@BeforeEach
	void setUp() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testCashStaging;DB_CLOSE_DELAY=-1");
		props.setProperty("user", "sa");
		props.setProperty("password", "");
		DatabaseManager.setProps(props, false, false);

		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `cashshoppurchases` ("
				+ "`inventoryitemid` INT, `uniqueid` BIGINT PRIMARY KEY, "
				+ "`purchaseracctid` INT, `gifterchrname` VARCHAR(50), `serialnumber` INT)"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `characters` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `accountid` INT, `name` VARCHAR(50))"
			);
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `inventoryitems` ("
				+ "`inventoryitemid` INT AUTO_INCREMENT PRIMARY KEY, "
				+ "`accountid` INT, `characterid` INT, `inventorytype` TINYINT, `position` SMALLINT)"
			);
			// Insert test data
			con.createStatement().execute(
				"INSERT INTO `cashshoppurchases` (`inventoryitemid`,`uniqueid`,`purchaseracctid`,`gifterchrname`,`serialnumber`) "
				+ "VALUES (1,1000,5,'GifterName',99)"
			);
			con.createStatement().execute(
				"INSERT INTO `cashshoppurchases` (`inventoryitemid`,`uniqueid`,`purchaseracctid`,`gifterchrname`,`serialnumber`) "
				+ "VALUES (2,1001,5,NULL,100)"
			);
			con.createStatement().execute(
				"INSERT INTO `characters` (`id`,`accountid`,`name`) VALUES (1,10,'TestChar')"
			);
			con.createStatement().execute(
				"INSERT INTO `characters` (`id`,`accountid`,`name`) VALUES (2,10,'TestChar2')"
			);
		}
	}

	@AfterEach
	void tearDown() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute("DROP TABLE IF EXISTS `inventoryitems`");
			con.createStatement().execute("DROP TABLE IF EXISTS `characters`");
			con.createStatement().execute("DROP TABLE IF EXISTS `cashshoppurchases`");
		}
		DatabaseManager.closeAll();
	}

	@Test
	void loadPurchaseProperties_returnsRecords() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<Long> uniqueIds = List.of(1000L, 1001L, 9999L);
			List<CashShopStagingDAO.PurchasePropertyRecord> results =
					CashShopStagingDAO.loadPurchaseProperties(con, uniqueIds);
			assertEquals(2, results.size());
			assertEquals(1000L, results.get(0).uniqueId());
			assertEquals(5, results.get(0).purchaserAccountId());
			assertEquals("GifterName", results.get(0).gifterName());
			assertEquals(99, results.get(0).serialNumber());
		}
	}

	@Test
	void attachPurchaseProperties_updatesRecord() throws SQLException {
		CashShopStagingDAO.attachPurchaseProperties(1000L, 7, "NewGifter", 200);

		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<CashShopStagingDAO.PurchasePropertyRecord> results =
					CashShopStagingDAO.loadPurchaseProperties(con, List.of(1000L));
			assertEquals(1, results.size());
			assertEquals(7, results.get(0).purchaserAccountId());
			assertEquals("NewGifter", results.get(0).gifterName());
			assertEquals(200, results.get(0).serialNumber());
		}
	}

	@Test
	void getBestItems_returnsTopItems() throws SQLException {
		// Add more purchases for serial 99 to make it top
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute(
				"INSERT INTO `cashshoppurchases` (`inventoryitemid`,`uniqueid`,`purchaseracctid`,`serialnumber`) "
				+ "VALUES (3,1002,6,99)"
			);
		}

		int[] bestItems = CashShopStagingDAO.getBestItems();
		assertEquals(99, bestItems[0]);
	}

	@Test
	void getNextPosition_returnsCorrectPosition() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			// No items yet for account 10 with type 6
			short pos = CashShopStagingDAO.getNextPosition(con, 10, (byte) 6);
			assertEquals(1, pos);

			// Insert an item
			con.createStatement().execute(
				"INSERT INTO `inventoryitems` (`accountid`,`characterid`,`inventorytype`,`position`) "
				+ "VALUES (10,1,6,5)"
			);

			pos = CashShopStagingDAO.getNextPosition(con, 10, (byte) 6);
			assertEquals(6, pos);
		}
	}

	@Test
	void getCharacterIdsForAccount_returnsIds() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<Integer> ids = CashShopStagingDAO.getCharacterIdsForAccount(con, 10);
			assertEquals(2, ids.size());
			assertTrue(ids.contains(1));
			assertTrue(ids.contains(2));
		}
	}

	@Test
	void getCharacterIdsForAccount_emptyForUnknownAccount() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<Integer> ids = CashShopStagingDAO.getCharacterIdsForAccount(con, 999);
			assertTrue(ids.isEmpty());
		}
	}
}
