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
 * Data Access Object for NPC script name lookups from the STATE database.
 */
public final class NpcDataDAO {

	private NpcDataDAO() {
	}

	/**
	 * Loads the script name for a specific NPC.
	 *
	 * @param con   the connection (caller manages)
	 * @param npcId the NPC ID
	 * @return the script name, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static String loadScriptName(Connection con, int npcId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `script` FROM `npcscriptnames` WHERE `npcid` = ?")) {
			ps.setInt(1, npcId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load script name for NPC " + npcId, e);
		}
	}

	/**
	 * Loads all NPC script names.
	 *
	 * @param con the connection (caller manages)
	 * @return map of NPC ID → script name
	 * @throws DataAccessException if a database error occurs
	 */
	public static Map<Integer, String> loadAllScriptNames(Connection con) {
		Map<Integer, String> scripts = new HashMap<>();
		try (PreparedStatement ps = con.prepareStatement("SELECT `npcid`,`script` FROM `npcscriptnames`");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				scripts.put(rs.getInt(1), rs.getString(2));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load NPC script names", e);
		}
		return scripts;
	}
}
