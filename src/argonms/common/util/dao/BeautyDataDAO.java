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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for beauty (face/hair) data loaded from the WZ database.
 */
public final class BeautyDataDAO {

	private BeautyDataDAO() {
	}

	/**
	 * Loads all face IDs from the WZ database.
	 *
	 * @param con the connection (caller manages)
	 * @return list of face IDs
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<Short> loadAllFaces(Connection con) {
		List<Short> faces = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement("SELECT `faceid` FROM `facedata`");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				faces.add(rs.getShort(1));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load face data", e);
		}
		return faces;
	}

	/**
	 * Loads all hair IDs from the WZ database.
	 *
	 * @param con the connection (caller manages)
	 * @return list of hair IDs
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<Short> loadAllHairs(Connection con) {
		List<Short> hairs = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement("SELECT `hairid` FROM `hairdata`");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				hairs.add(rs.getShort(1));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load hair data", e);
		}
		return hairs;
	}
}
