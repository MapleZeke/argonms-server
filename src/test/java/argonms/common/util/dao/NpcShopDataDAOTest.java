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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link NpcShopDataDAO} using an in-memory H2 database.
 */
class NpcShopDataDAOTest {

	@BeforeEach
	void setUp() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testNpcShopDAO;DB_CLOSE_DELAY=-1");
		props.setProperty("user", "sa");
		props.setProperty("password", "");
		DatabaseManager.setProps(props, false, false);

		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute(
				"CREATE TABLE IF NOT EXISTS `shopitems` ("
				+ "`id` INT AUTO_INCREMENT PRIMARY KEY, `npcid` INT, "
				+ "`itemid` INT, `price` INT, `position` INT)"
			);
			// Insert test data
			con.createStatement().execute("INSERT INTO `shopitems` (`npcid`,`itemid`,`price`,`position`) VALUES (1000,'2000001',100,1)");
			con.createStatement().execute("INSERT INTO `shopitems` (`npcid`,`itemid`,`price`,`position`) VALUES (1000,'2000002',200,2)");
			con.createStatement().execute("INSERT INTO `shopitems` (`npcid`,`itemid`,`price`,`position`) VALUES (2000,'3000001',300,1)");
		}
	}

	@AfterEach
	void tearDown() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			con.createStatement().execute("DROP TABLE IF EXISTS `shopitems`");
		}
		DatabaseManager.closeAll();
	}

	@Test
	void loadDefaultShopItems_existingNpc_returnsItems() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<NpcShopDataDAO.ShopSlotRecord> items = NpcShopDataDAO.loadDefaultShopItems(con, 1000);
			assertEquals(2, items.size());
			assertEquals(2000001, items.get(0).itemId());
			assertEquals(100, items.get(0).price());
			assertEquals(1, items.get(0).quantity());
		}
	}

	@Test
	void loadDefaultShopItems_nonexistentNpc_returnsEmpty() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			List<NpcShopDataDAO.ShopSlotRecord> items = NpcShopDataDAO.loadDefaultShopItems(con, 9999);
			assertTrue(items.isEmpty());
		}
	}

	@Test
	void loadAllDefaultShopItems_groupsByNpc() throws SQLException {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			Map<Integer, List<NpcShopDataDAO.ShopSlotRecord>> allItems = NpcShopDataDAO.loadAllDefaultShopItems(con);
			assertEquals(2, allItems.size());
			assertTrue(allItems.containsKey(1000));
			assertTrue(allItems.containsKey(2000));
			assertEquals(2, allItems.get(1000).size());
			assertEquals(1, allItems.get(2000).size());
		}
	}
}
