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

package argonms.common.util;

import argonms.common.util.DatabaseManager.DatabaseType;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DatabaseManager} HikariCP pool management.
 * Uses an in-process H2 database to avoid external dependencies.
 */
class DatabaseManagerTest {

	@AfterEach
	void tearDown() {
		DatabaseManager.closeAll();
	}

	@Test
	void setPropsInitializesStatePool() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testState;DB_CLOSE_DELAY=-1");
		props.setProperty("user", "sa");
		props.setProperty("password", "");

		DatabaseManager.setProps(props, false, false);

		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			assertNotNull(con);
			assertFalse(con.isClosed());
		}
	}

	@Test
	void getConnectionThrowsWhenPoolNotConfigured() {
		assertThrows(SQLException.class, () -> DatabaseManager.getConnection(DatabaseType.WZ));
	}

	@Test
	void inTransactionCommitsOnSuccess() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testTx;DB_CLOSE_DELAY=-1");
		props.setProperty("user", "sa");
		props.setProperty("password", "");
		DatabaseManager.setProps(props, false, false);

		// Create a table and insert in a transaction
		DatabaseManager.inTransaction(DatabaseType.STATE, con -> {
			con.createStatement().execute("CREATE TABLE IF NOT EXISTS test_table (id INT, name VARCHAR(50))");
			con.prepareStatement("INSERT INTO test_table VALUES (1, 'hello')").executeUpdate();
		});

		// Verify data was committed
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 var ps = con.prepareStatement("SELECT name FROM test_table WHERE id = 1");
			 var rs = ps.executeQuery()) {
			assertTrue(rs.next());
			assertEquals("hello", rs.getString(1));
		}
	}

	@Test
	void inTransactionRollsBackOnException() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testRollback;DB_CLOSE_DELAY=-1");
		props.setProperty("user", "sa");
		props.setProperty("password", "");
		DatabaseManager.setProps(props, false, false);

		// Create table first
		DatabaseManager.inTransaction(DatabaseType.STATE, con ->
			con.createStatement().execute("CREATE TABLE IF NOT EXISTS rollback_test (id INT, val VARCHAR(50))")
		);

		// Attempt a transaction that fails
		assertThrows(SQLException.class, () ->
			DatabaseManager.inTransaction(DatabaseType.STATE, con -> {
				con.prepareStatement("INSERT INTO rollback_test VALUES (1, 'should_not_exist')").executeUpdate();
				throw new SQLException("Simulated failure");
			})
		);

		// Verify the insert was rolled back
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
			 var ps = con.prepareStatement("SELECT COUNT(*) FROM rollback_test");
			 var rs = ps.executeQuery()) {
			assertTrue(rs.next());
			assertEquals(0, rs.getInt(1));
		}
	}

	@Test
	void closeAllShutsDownPools() throws SQLException {
		PropertiesConfiguration props = new PropertiesConfiguration();
		props.setProperty("driver", "org.h2.Driver");
		props.setProperty("url", "jdbc:h2:mem:testClose;DB_CLOSE_DELAY=-1");
		props.setProperty("user", "sa");
		props.setProperty("password", "");
		DatabaseManager.setProps(props, false, false);

		// Verify pool is active
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			assertNotNull(con);
		}

		// Close all pools
		DatabaseManager.closeAll();

		// Verify pool is no longer accessible
		assertThrows(SQLException.class, () -> DatabaseManager.getConnection(DatabaseType.STATE));
	}
}
