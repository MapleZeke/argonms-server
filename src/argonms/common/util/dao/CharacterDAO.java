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

import argonms.common.character.KeyBinding;
import argonms.common.character.SkillEntry;
import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Data Access Object for character-level persistence (stats, skills, cooldowns,
 * keymaps, buddies, quests, etc.).
 *
 * <p>All public methods are stateless, accept a {@link Connection} for
 * transaction participation, and throw {@link DataAccessException} on failure.
 */
public final class CharacterDAO {
	private static final Logger LOG = Logger.getLogger(CharacterDAO.class.getName());

	private CharacterDAO() {
	}

	/**
	 * Updates core character stats in the {@code characters} table.
	 *
	 * @param con   the connection (caller manages transaction)
	 * @param stats the stats record containing all fields
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateStats(Connection con, CharacterStats stats) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET "
				+ "`accountid` = ?, `world` = ?, `name` = ?, `gender` = ?, `skin` = ?, `eyes` = ?, `hair` = ?, "
				+ "`level` = ?, `job` = ?, `str` = ?, `dex` = ?, `int` = ?, `luk` = ?, "
				+ "`hp` = ?, `maxhp` = ?, `mp` = ?, `maxmp` = ?, `ap` = ?, `sp` = ?, `exp` = ?, `fame` = ?, "
				+ "`spouse` = ?, `map` = ?, `spawnpoint` = ?, `mesos` = ?, "
				+ "`equipslots` = ?, `useslots` = ?, `setupslots` = ?, `etcslots` = ?, `cashslots` = ?, "
				+ "`buddyslots` = ?, `gm` = ? WHERE `id` = ?")) {
			ps.setInt(1, stats.accountId());
			ps.setByte(2, stats.world());
			ps.setString(3, stats.name());
			ps.setByte(4, stats.gender());
			ps.setInt(5, stats.skin());
			ps.setInt(6, stats.eyes());
			ps.setInt(7, stats.hair());
			ps.setShort(8, stats.level());
			ps.setShort(9, stats.job());
			ps.setShort(10, stats.str());
			ps.setShort(11, stats.dex());
			ps.setShort(12, stats.intt());
			ps.setShort(13, stats.luk());
			ps.setShort(14, stats.hp());
			ps.setShort(15, stats.maxHp());
			ps.setShort(16, stats.mp());
			ps.setShort(17, stats.maxMp());
			ps.setShort(18, stats.ap());
			ps.setShort(19, stats.sp());
			ps.setInt(20, stats.exp());
			ps.setShort(21, stats.fame());
			ps.setInt(22, stats.spouse());
			ps.setInt(23, stats.map());
			ps.setByte(24, stats.spawnpoint());
			ps.setInt(25, stats.mesos());
			ps.setShort(26, stats.equipSlots());
			ps.setShort(27, stats.useSlots());
			ps.setShort(28, stats.setupSlots());
			ps.setShort(29, stats.etcSlots());
			ps.setShort(30, stats.cashSlots());
			ps.setShort(31, stats.buddySlots());
			ps.setByte(32, stats.gm());
			ps.setInt(33, stats.characterId());
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save stats of character " + stats.name(), e);
		}
	}

	/**
	 * Replaces all skill entries for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param skills      map of skill ID → skill entry
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceSkills(Connection con, int characterId, Map<Integer, SkillEntry> skills) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `skills` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete skills for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `skills` (`characterid`,`skillid`,`level`,`mastery`) VALUES (?,?,?,?)")) {
			ps.setInt(1, characterId);
			for (Map.Entry<Integer, SkillEntry> skill : skills.entrySet()) {
				SkillEntry entry = skill.getValue();
				ps.setInt(2, skill.getKey());
				ps.setByte(3, entry.getLevel());
				ps.setByte(4, entry.getMasterLevel());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save skills for character " + characterId, e);
		}
	}

	/**
	 * Replaces all cooldown entries for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param cooldowns   map of skill ID → remaining seconds
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceCooldowns(Connection con, int characterId, Map<Integer, Short> cooldowns) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `cooldowns` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete cooldowns for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `cooldowns` (`characterid`,`skillid`,`remaining`) VALUES (?,?,?)")) {
			ps.setInt(1, characterId);
			for (Map.Entry<Integer, Short> cooling : cooldowns.entrySet()) {
				ps.setInt(2, cooling.getKey());
				ps.setShort(3, cooling.getValue());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save cooldowns for character " + characterId, e);
		}
	}

	/**
	 * Replaces all key bindings for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param bindings    map of key position → key binding
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceKeyBindings(Connection con, int characterId, Map<Byte, KeyBinding> bindings) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `keymaps` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete keymaps for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `keymaps` (`characterid`,`key`,`type`,`action`) VALUES (?,?,?,?)")) {
			ps.setInt(1, characterId);
			for (Map.Entry<Byte, KeyBinding> entry : bindings.entrySet()) {
				KeyBinding binding = entry.getValue();
				ps.setByte(2, entry.getKey());
				ps.setByte(3, binding.getType());
				ps.setInt(4, binding.getAction());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save keymaps for character " + characterId, e);
		}
	}

	/**
	 * Replaces all map memory entries for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param mapMemory   map of variable key → (mapId, spawnpoint) pair
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceMapMemory(Connection con, int characterId,
			Map<String, int[]> mapMemory) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `mapmemory` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete map memory for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `mapmemory` (`characterid`,`key`,`value`,`spawnpoint`) VALUES (?,?,?,?)")) {
			ps.setInt(1, characterId);
			for (Map.Entry<String, int[]> entry : mapMemory.entrySet()) {
				ps.setString(2, entry.getKey());
				ps.setInt(3, entry.getValue()[0]);
				ps.setByte(4, (byte) entry.getValue()[1]);
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save map memory for character " + characterId, e);
		}
	}

	/**
	 * Replaces all quest statuses (including mob progress) for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param quests      iterable of quest records
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceQuests(Connection con, int characterId, Iterable<QuestRecord> quests) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `queststatuses` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete quest statuses for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `queststatuses` "
				+ "(`characterid`,`questid`,`state`,`completed`) VALUES (?,?,?,?)",
				Statement.RETURN_GENERATED_KEYS);
			 PreparedStatement mps = con.prepareStatement("INSERT INTO `questmobprogress` "
				+ "(`queststatusid`,`mobid`,`count`) VALUES (?,?,?)")) {
			ps.setInt(1, characterId);
			for (QuestRecord quest : quests) {
				ps.setShort(2, quest.questId());
				ps.setByte(3, quest.state());
				ps.setLong(4, quest.completionTime());
				if (quest.mobProgress() != null && !quest.mobProgress().isEmpty()) {
					ps.executeUpdate();
					int questEntryId;
					try (ResultSet rs = ps.getGeneratedKeys()) {
						questEntryId = rs.next() ? rs.getInt(1) : -1;
					}
					mps.setInt(1, questEntryId);
					for (Map.Entry<Integer, Short> mob : quest.mobProgress().entrySet()) {
						mps.setInt(2, mob.getKey());
						mps.setShort(3, mob.getValue());
						mps.addBatch();
					}
				} else {
					ps.addBatch();
				}
			}
			ps.executeBatch();
			mps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save quest statuses for character " + characterId, e);
		}
	}

	/**
	 * Replaces buddy list entries for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param buddies     iterable of buddy records
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceBuddies(Connection con, int characterId, Iterable<BuddyRecord> buddies) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `buddyentries` WHERE `owner` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete buddy entries for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `buddyentries` "
				+ "(`owner`,`buddy`,`buddyname`,`status`) VALUES (?,?,?,?)")) {
			ps.setInt(1, characterId);
			for (BuddyRecord buddy : buddies) {
				ps.setInt(2, buddy.buddyId());
				ps.setString(3, buddy.buddyName());
				ps.setByte(4, buddy.status());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save buddy entries for character " + characterId, e);
		}
	}

	/**
	 * Replaces minigame scores for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param scores      iterable of minigame score records
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceMinigameScores(Connection con, int characterId, Iterable<MinigameScoreRecord> scores) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `minigamescores` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete minigame scores for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `minigamescores` "
				+ "(`characterid`,`gametype`,`wins`,`ties`,`losses`) VALUES (?,?,?,?,?)")) {
			ps.setInt(1, characterId);
			for (MinigameScoreRecord score : scores) {
				ps.setByte(2, score.gameType());
				ps.setInt(3, score.wins());
				ps.setInt(4, score.ties());
				ps.setInt(5, score.losses());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save minigame scores for character " + characterId, e);
		}
	}

	/**
	 * Replaces fame log entries for the given character (only entries within
	 * the past 30 days are persisted).
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param fameLog     map of target character ID → timestamp
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceFameLog(Connection con, int characterId, Map<Integer, Long> fameLog) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `famelog` WHERE `from` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete fame log for character " + characterId, e);
		}

		long threshold = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30;
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `famelog` (`from`,`to`,`millis`) VALUES (?,?,?)")) {
			ps.setInt(1, characterId);
			for (Map.Entry<Integer, Long> entry : fameLog.entrySet()) {
				long time = entry.getValue();
				if (time >= threshold) {
					ps.setInt(2, entry.getKey());
					ps.setLong(3, time);
					ps.addBatch();
				}
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save fame log for character " + characterId, e);
		}
	}

	/**
	 * Creates a new character in the database.
	 *
	 * @param con   the connection (caller manages transaction)
	 * @param stats the initial character data
	 * @return the generated character ID
	 * @throws DataAccessException if a database error occurs
	 */
	public static int createCharacter(Connection con, NewCharacterRecord stats) {
		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `characters` "
				+ "(`accountid`,`world`,`name`,`gender`,`skin`,`eyes`,`hair`,"
				+ "`str`,`dex`,`int`,`luk`,`gm`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
				Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, stats.accountId());
			ps.setByte(2, stats.world());
			ps.setString(3, stats.name());
			ps.setByte(4, stats.gender());
			ps.setInt(5, stats.skin());
			ps.setInt(6, stats.eyes());
			ps.setInt(7, stats.hair());
			ps.setShort(8, stats.str());
			ps.setShort(9, stats.dex());
			ps.setShort(10, stats.intt());
			ps.setShort(11, stats.luk());
			ps.setByte(12, stats.gm());
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
			throw new DataAccessException("No generated key returned for new character " + stats.name());
		} catch (SQLException e) {
			throw new DataAccessException("Failed to create character " + stats.name(), e);
		}
	}

	// ---- Record types for structured data transfer ----

	/**
	 * All character stats needed for a full save.
	 */
	public record CharacterStats(
		int characterId, int accountId, byte world, String name, byte gender,
		int skin, int eyes, int hair,
		short level, short job, short str, short dex, short intt, short luk,
		short hp, short maxHp, short mp, short maxMp, short ap, short sp,
		int exp, short fame, int spouse, int map, byte spawnpoint, int mesos,
		short equipSlots, short useSlots, short setupSlots, short etcSlots, short cashSlots,
		short buddySlots, byte gm
	) {}

	/**
	 * Data needed to create a new character.
	 */
	public record NewCharacterRecord(
		int accountId, byte world, String name, byte gender,
		int skin, int eyes, int hair,
		short str, short dex, short intt, short luk, byte gm
	) {}

	/**
	 * Quest progress record.
	 */
	public record QuestRecord(
		short questId, byte state, long completionTime,
		Map<Integer, Short> mobProgress
	) {}

	/**
	 * Buddy list entry record.
	 */
	public record BuddyRecord(int buddyId, String buddyName, byte status) {}

	/**
	 * Minigame score record.
	 */
	public record MinigameScoreRecord(byte gameType, int wins, int ties, int losses) {}

	// ---- Character lookup methods ----

	/**
	 * Retrieves a character's name by ID.
	 *
	 * @param characterId the character ID
	 * @return the character name, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static String getNameFromId(int characterId) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT `name` FROM `characters` WHERE `id` = ?")) {
			ps.setInt(1, characterId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not find name of character " + characterId, e);
		}
	}

	/**
	 * Retrieves a character's ID by name.
	 *
	 * @param name the character name
	 * @return the character ID, or -1 if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static int getIdFromName(String name) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT `id` FROM `characters` WHERE `name` = ?")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
				return -1;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not find id of character " + name, e);
		}
	}

	/**
	 * Checks whether a character exists by name and world.
	 *
	 * @param name  the character name
	 * @param world the world ID
	 * @return true if the character exists
	 * @throws DataAccessException if a database error occurs
	 */
	public static boolean characterExists(String name, byte world) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement(
						"SELECT EXISTS(SELECT 1 FROM `characters` WHERE `name` = ? AND `world` = ? LIMIT 1)")) {
			ps.setString(1, name);
			ps.setByte(2, world);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getBoolean(1);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not determine if character " + name + " exists", e);
		}
	}

	/**
	 * Looks up a character's connection status, world, ID, name, and GM level by name.
	 * Used during buddy invite processing.
	 *
	 * @param con  the connection (caller manages)
	 * @param name the character name
	 * @return the lookup result, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static CharacterLookup lookupByName(Connection con, String name) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `a`.`connected`,`c`.`world`,`c`.`id`,`c`.`name`,`a`.`gm` "
				+ "FROM `characters` `c` LEFT JOIN `accounts` `a` ON `c`.`accountid` = `a`.`id` "
				+ "WHERE `c`.`name` = ?")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return new CharacterLookup(
					rs.getByte(1), rs.getByte(2), rs.getInt(3), rs.getString(4), rs.getByte(5)
				);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not look up character " + name, e);
		}
	}

	/**
	 * Character lookup result record.
	 */
	public record CharacterLookup(byte connected, byte world, int characterId, String name, byte gm) {}
}
