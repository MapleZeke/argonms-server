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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration2.PropertiesConfiguration;

/**
 * Provides centralized database connection management via HikariCP.
 *
 * <p>Connections obtained from {@link #getConnection(DatabaseType)} are pooled
 * and <strong>must</strong> be closed by the caller (ideally via
 * try-with-resources) to return them to the pool.
 *
 * <p>The legacy {@link #cleanup} method is retained for backward compatibility
 * during migration but callers should prefer try-with-resources.
 */
public final class DatabaseManager {
	public enum DatabaseType { STATE, WZ }

	/**
	 * Functional interface for code that executes within a single database
	 * transaction.
	 */
	@FunctionalInterface
	public interface TransactionalWork {
		void execute(Connection con) throws SQLException;
	}

	private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());

	private static final Map<DatabaseType, HikariDataSource> dataSources = new EnumMap<>(DatabaseType.class);

	/**
	 * Returns a connection from the pool for the given database type.
	 * The caller <strong>must</strong> close the returned connection when done.
	 */
	public static Connection getConnection(DatabaseType type) throws SQLException {
		HikariDataSource ds = dataSources.get(type);
		if (ds == null) {
			throw new SQLException("No data source configured for " + type);
		}
		return ds.getConnection();
	}

	/**
	 * Legacy cleanup helper retained for backward compatibility.
	 * New code should use try-with-resources instead.
	 */
	public static void cleanup(DatabaseType type, ResultSet rs, PreparedStatement ps, Connection con) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException ignored) {
			}
		}
		if (ps != null) {
			try {
				ps.close();
			} catch (SQLException ignored) {
			}
		}
		if (con != null) {
			try {
				con.close();
			} catch (SQLException ignored) {
			}
		}
	}

	/**
	 * Executes the given work inside a single database transaction.
	 * The transaction is committed if the work completes normally, or
	 * rolled back if it throws an exception.
	 */
	public static void inTransaction(DatabaseType type, TransactionalWork work) throws SQLException {
		try (Connection con = getConnection(type)) {
			con.setAutoCommit(false);
			try {
				work.execute(con);
				con.commit();
			} catch (SQLException | RuntimeException ex) {
				try {
					con.rollback();
				} catch (SQLException rollbackEx) {
					ex.addSuppressed(rollbackEx);
				}
				throw ex;
			} finally {
				con.setAutoCommit(true);
			}
		}
	}

	/**
	 * Initializes connection pools from the given properties.
	 *
	 * @param props    database properties (driver, url, user, password, mcdb)
	 * @param useMcdb  whether to initialize the WZ database pool
	 * @param nio      ignored (retained for API compatibility during migration)
	 */
	public static void setProps(PropertiesConfiguration props, boolean useMcdb, boolean nio) throws SQLException {
		String driver = props.getString("driver");
		String url = props.getString("url");
		String user = props.getString("user");
		String password = props.getString("password");

		dataSources.put(DatabaseType.STATE, createDataSource("argonms-state", driver, url, user, password));
		LOG.log(Level.INFO, "Initialized HikariCP pool for STATE database");

		if (useMcdb) {
			String wz = props.getString("mcdb");
			dataSources.put(DatabaseType.WZ, createDataSource("argonms-wz", driver, wz, user, password));
			LOG.log(Level.INFO, "Initialized HikariCP pool for WZ database");
		}
	}

	/**
	 * Shuts down all connection pools gracefully.
	 */
	public static Map<DatabaseType, Map<Connection, SQLException>> closeAll() {
		Map<DatabaseType, Map<Connection, SQLException>> exceptions = new EnumMap<>(DatabaseType.class);
		for (Map.Entry<DatabaseType, HikariDataSource> entry : dataSources.entrySet()) {
			try {
				entry.getValue().close();
			} catch (RuntimeException ex) {
				LOG.log(Level.WARNING, "Error closing pool for " + entry.getKey(), ex);
			}
		}
		dataSources.clear();
		return exceptions;
	}

	/**
	 * Creates a HikariCP data source with sensible defaults.
	 *
	 * <p>Pool sizing can be tuned via system properties:
	 * <ul>
	 *   <li>{@code argonms.db.maxPoolSize} – maximum pool size (default: 10)</li>
	 *   <li>{@code argonms.db.minIdle} – minimum idle connections (default: 2)</li>
	 *   <li>{@code argonms.db.idleTimeout} – idle connection timeout in ms (default: 300000)</li>
	 *   <li>{@code argonms.db.maxLifetime} – max connection lifetime in ms (default: 600000)</li>
	 *   <li>{@code argonms.db.connectionTimeout} – connection acquisition timeout in ms (default: 30000)</li>
	 * </ul>
	 */
	private static HikariDataSource createDataSource(String poolName, String driver, String url, String user, String password) {
		HikariConfig config = new HikariConfig();
		config.setPoolName(poolName);
		config.setDriverClassName(driver);
		config.setJdbcUrl(url);
		config.setUsername(user);
		config.setPassword(password);
		config.setMaximumPoolSize(Integer.getInteger("argonms.db.maxPoolSize", 10));
		config.setMinimumIdle(Integer.getInteger("argonms.db.minIdle", 2));
		config.setIdleTimeout(Long.getLong("argonms.db.idleTimeout", 300_000L));
		config.setMaxLifetime(Long.getLong("argonms.db.maxLifetime", 600_000L));
		config.setConnectionTimeout(Long.getLong("argonms.db.connectionTimeout", 30_000L));
		config.setAutoCommit(true);

		// MySQL prepared-statement caching for improved query performance.
		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		config.addDataSourceProperty("useServerPrepStmts", "true");

		return new HikariDataSource(config);
	}

	private DatabaseManager() {
		//uninstantiable...
	}
}
