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
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Data Access Object for ranking operations (stored procedure calls).
 *
 * <p>All public methods are stateless and obtain their own connection from the pool.
 */
public final class RankingDAO {

	private RankingDAO() {
	}

	/**
	 * Executes the ranking update stored procedure for a specific type.
	 *
	 * @param con   the connection (caller manages)
	 * @param type  the ranking type ("overall", "world", "job", "fame")
	 * @param param the parameter (world ID for "world", job class for "job", null for others)
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateRanking(Connection con, String type, Byte param) {
		try (CallableStatement ps = con.prepareCall("{call updateranks(?,?)}")) {
			ps.setString(1, type);
			if (param != null) {
				ps.setByte(2, param.byteValue());
			} else {
				ps.setNull(2, Types.TINYINT);
			}
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not update ranking for type " + type, e);
		}
	}

	/**
	 * Executes all ranking updates: overall, per-world, per-job-class, and fame.
	 *
	 * @param worlds the set of world IDs to update rankings for
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateAllRankings(Iterable<Byte> worlds) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				CallableStatement ps = con.prepareCall("{call updateranks(?,?)}")) {
			ps.setString(1, "overall");
			ps.setNull(2, Types.TINYINT);
			ps.executeUpdate();
			ps.setString(1, "world");
			for (Byte world : worlds) {
				ps.setByte(2, world.byteValue());
				ps.executeUpdate();
			}
			ps.setString(1, "job");
			for (byte jobClass = 0; jobClass <= 5; jobClass++) {
				ps.setByte(2, jobClass);
				ps.executeUpdate();
			}
			ps.setString(1, "fame");
			ps.setNull(2, Types.TINYINT);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Could not update all rankings", e);
		}
	}
}
