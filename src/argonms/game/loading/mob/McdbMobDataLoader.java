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

package argonms.game.loading.mob;

import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class McdbMobDataLoader extends MobDataLoader {
	private static final Logger LOG = Logger.getLogger(McdbMobDataLoader.class.getName());

	protected McdbMobDataLoader() {
		
	}

	@Override
	protected void load(int mobid) {
		MobStats stats = null;
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ);
				PreparedStatement ps = con.prepareStatement("SELECT * FROM `mobdata` WHERE `mobid` = ?")) {
			ps.setInt(1, mobid);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					stats = new MobStats(mobid);
					doWork(rs, mobid, stats, con);
				}
			}
		} catch (SQLException e) {
			LOG.log(Level.WARNING, "Could not read MCDB data for mob " + mobid, e);
		}
		mobStats.put(Integer.valueOf(mobid), stats);
	}

	@Override
	public boolean loadAll() {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ);
				PreparedStatement ps = con.prepareStatement("SELECT * FROM `mobdata`");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int mobid = rs.getInt(1);
				MobStats stats = new MobStats(mobid);
				doWork(rs, mobid, stats, con);
				mobStats.put(Integer.valueOf(mobid), stats);
			}
			return true;
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not load all mob data from MCDB.", ex);
			return false;
		}
	}

	@Override
	public boolean canLoad(int mobid) {
		if (mobStats.containsKey(mobid)) {
			return true;
		}
		boolean exists = false;
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ);
				PreparedStatement ps = con.prepareStatement("SELECT * FROM `mobdata` WHERE `mobid` = ?")) {
			ps.setInt(1, mobid);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					exists = true;
				}
			}
		} catch (SQLException e) {
			LOG.log(Level.WARNING, "Could not use MCDB to determine whether mob " + mobid + " is valid.", e);
		}
		return exists;
	}

	private void doWork(ResultSet rs, int mobid, MobStats stats, Connection con) throws SQLException {
		stats.setLevel(rs.getShort(2));
		stats.setMaxHp(rs.getInt(3));
		stats.setMaxMp(rs.getInt(4));
		stats.setExp(rs.getInt(8));
		if (rs.getInt(13) != 0) {
			stats.setUndead();
		}
		stats.setElementalAttribute(rs.getString(20));
		stats.setRemoveAfter(rs.getInt(11));
		stats.setHpTagColor(rs.getByte(18));
		stats.setHpTagBgColor(rs.getByte(19));
		if (rs.getInt(12) != 0) {
			stats.setBoss();
		}
		stats.setSelfDestructHp(rs.getInt(7));
		try (PreparedStatement ps = con.prepareStatement("SELECT `summonid` FROM `mobsummondata` where `mobid` = ?")) {
			ps.setInt(1, mobid);
			try (ResultSet rs2 = ps.executeQuery()) {
				while (rs2.next()) {
					stats.addSummon(rs2.getInt(1));
				}
			}
		}
		try (PreparedStatement ps = con.prepareStatement("SELECT `skillid`,`level`,`effectafter` FROM `mobskilldata` WHERE `mobid` = ?")) {
			ps.setInt(1, mobid);
			try (ResultSet rs2 = ps.executeQuery()) {
				while (rs2.next()) {
					Skill s = new Skill();
					s.setSkill(rs2.getShort(1));
					s.setLevel(rs2.getByte(2));
					s.setEffectDelay(rs2.getShort(3));
					stats.addSkill(s);
				}
			}
		}
		stats.setBuffToGive(rs.getInt(10));
		try (PreparedStatement ps = con.prepareStatement("SELECT `ismesos`,`itemid`,`min`,`max`,`chance` FROM `dropdata` WHERE `dropperid` = ?")) {
			ps.setInt(1, mobid);
			try (ResultSet rs2 = ps.executeQuery()) {
				while (rs2.next()) {
					if (rs2.getBoolean(1)) {
						stats.setMesoDrop(rs2.getInt(5), rs2.getInt(3), rs2.getInt(4));
					} else {
						stats.addItemDrop(rs2.getInt(2), rs2.getInt(5), rs.getShort(3), rs.getShort(4));
					}
				}
			}
		}
		try (PreparedStatement ps = con.prepareStatement("SELECT `attackid`,`mpconsume`,`mpburn`,`disease`,`level`,`deadly` FROM `mobattackdata` WHERE `mobid` = ?")) {
			ps.setInt(1, mobid);
			try (ResultSet rs2 = ps.executeQuery()) {
				while (rs2.next()) {
					Attack a = new Attack();
					a.setMpConsume(rs2.getInt(2));
					a.setMpBurn((short) Math.min(Short.MAX_VALUE, rs2.getInt(3)));
					a.setDiseaseSkill(rs2.getByte(4));
					a.setDiseaseLevel(rs2.getByte(5));
					a.setDeadlyAttack(rs2.getBoolean(6));
					stats.addAttack(rs2.getByte(1), a);
				}
			}
		}
	}
}
