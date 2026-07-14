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
import java.util.HashMap;
import java.util.Map;

/**
 * Data Access Object for reactor script name lookups from the STATE database
 * and reactor event data existence checks.
 */
public final class ReactorDataDAO {

	private ReactorDataDAO() {
	}

	/**
	 * Loads the script name for a specific reactor.
	 *
	 * @param con       the connection (caller manages)
	 * @param reactorId the reactor ID
	 * @return the script name, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static String loadScriptName(Connection con, int reactorId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `script` FROM `reactorscriptnames` WHERE `reactorid` = ?")) {
			ps.setInt(1, reactorId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load script name for reactor " + reactorId, e);
		}
	}

	/**
	 * Loads all reactor script names.
	 *
	 * @param con the connection (caller manages)
	 * @return map of reactor ID → script name
	 * @throws DataAccessException if a database error occurs
	 */
	public static Map<Integer, String> loadAllScriptNames(Connection con) {
		Map<Integer, String> scripts = new HashMap<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `reactorid`,`script` FROM `reactorscriptnames` ORDER BY `reactorid`");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				scripts.put(rs.getInt(1), rs.getString(2));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load reactor script names", e);
		}
		return scripts;
	}

	/**
	 * Checks whether reactor event data exists for a given reactor.
	 *
	 * @param con       the connection to the WZ database (caller manages)
	 * @param reactorId the reactor ID
	 * @return true if event data exists
	 * @throws DataAccessException if a database error occurs
	 */
	public static boolean reactorEventDataExists(Connection con, int reactorId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT 1 FROM `reactoreventdata` WHERE `reactorid` = ? LIMIT 1")) {
			ps.setInt(1, reactorId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not check reactor event data for " + reactorId, e);
		}
	}

	/**
	 * Checks whether a reactor script name exists for a given reactor.
	 *
	 * @param con       the connection to the STATE database (caller manages)
	 * @param reactorId the reactor ID
	 * @return true if a script name exists
	 * @throws DataAccessException if a database error occurs
	 */
	public static boolean reactorScriptExists(Connection con, int reactorId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT 1 FROM `reactorscriptnames` WHERE `reactorid` = ? LIMIT 1")) {
			ps.setInt(1, reactorId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not check reactor script for " + reactorId, e);
		}
	}
}
