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

	// ---- Skill Macros ----

	/**
	 * Replaces all skill macros for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param macros      iterable of macro records
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceSkillMacros(Connection con, int characterId, Iterable<SkillMacroRecord> macros) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `skillmacros` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete skill macros for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `skillmacros` "
				+ "(`characterid`,`position`,`name`,`silent`,`skill1`,`skill2`,`skill3`) "
				+ "VALUES (?,?,?,?,?,?,?)")) {
			ps.setInt(1, characterId);
			for (SkillMacroRecord macro : macros) {
				ps.setByte(2, macro.position());
				ps.setString(3, macro.name());
				ps.setBoolean(4, macro.silent());
				ps.setInt(5, macro.skill1());
				ps.setInt(6, macro.skill2());
				ps.setInt(7, macro.skill3());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save skill macros for character " + characterId, e);
		}
	}

	// ---- Party ----

	/**
	 * Replaces the party entry for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param party       the party record, or null if the character is not in a party
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceParty(Connection con, int characterId, PartyRecord party) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `parties` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete party for character " + characterId, e);
		}

		if (party != null) {
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO `parties` "
					+ "(`world`,`partyid`,`characterid`,`leader`) VALUES (?,?,?,?)")) {
				ps.setByte(1, party.world());
				ps.setInt(2, party.partyId());
				ps.setInt(3, characterId);
				ps.setBoolean(4, party.leader());
				ps.executeUpdate();
			} catch (SQLException e) {
				throw new DataAccessException("Failed to save party for character " + characterId, e);
			}
		}
	}

	/**
	 * Loads the party ID for the given character.
	 *
	 * @param con         the connection
	 * @param characterId the character ID
	 * @return the party ID, or -1 if not in a party
	 * @throws DataAccessException if a database error occurs
	 */
	public static int loadPartyId(Connection con, int characterId) {
		try (PreparedStatement ps = con.prepareStatement("SELECT `partyid` FROM `parties` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
				return -1;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to load party for character " + characterId, e);
		}
	}

	// ---- Guild Members ----

	/**
	 * Replaces the guild member entry for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param member      the guild member record, or null if the character is not in a guild
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceGuildMember(Connection con, int characterId, GuildMemberRecord member) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `guildmembers` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete guild member for character " + characterId, e);
		}

		if (member != null) {
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO `guildmembers` "
					+ "(`guildid`,`characterid`,`rank`,`signature`,`alliancerank`) VALUES (?,?,?,?,?)")) {
				ps.setInt(1, member.guildId());
				ps.setInt(2, characterId);
				ps.setByte(3, member.rank());
				ps.setByte(4, member.signature());
				ps.setByte(5, member.allianceRank());
				ps.executeUpdate();
			} catch (SQLException e) {
				throw new DataAccessException("Failed to save guild member for character " + characterId, e);
			}
		}
	}

	/**
	 * Loads the guild ID for the given character.
	 *
	 * @param con         the connection
	 * @param characterId the character ID
	 * @return the guild ID, or -1 if not in a guild
	 * @throws DataAccessException if a database error occurs
	 */
	public static int loadGuildId(Connection con, int characterId) {
		try (PreparedStatement ps = con.prepareStatement("SELECT `g`.`id` FROM `guilds` `g` "
				+ "LEFT JOIN `guildmembers` `m` ON `g`.`id` = `m`.`guildid` "
				+ "WHERE `m`.`characterid` = ?")) {
			ps.setInt(1, characterId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
				return -1;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to load guild for character " + characterId, e);
		}
	}

	// ---- Wishlists ----

	/**
	 * Replaces the wishlist entries for the given character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param serialNumbers the wishlist serial numbers
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceWishlist(Connection con, int characterId, Iterable<Integer> serialNumbers) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `wishlists` WHERE `characterid` = ?")) {
			ps.setInt(1, characterId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete wishlist for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `wishlists` (`characterid`,`sn`) VALUES (?,?)")) {
			ps.setInt(1, characterId);
			for (Integer sn : serialNumbers) {
				ps.setInt(2, sn.intValue());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save wishlist for character " + characterId, e);
		}
	}

	// ---- Pet Ignore Items ----

	/**
	 * Saves pet ignore items by linking them to cash shop purchase unique IDs.
	 *
	 * @param con            the connection (caller manages transaction)
	 * @param petIgnoreItems map of unique ID → array of ignored item IDs
	 * @param validUniqueIds set of unique IDs that are actually in the cash inventory
	 * @throws DataAccessException if a database error occurs
	 */
	public static void savePetIgnoreItems(Connection con, Map<Long, int[]> petIgnoreItems,
			java.util.Set<Long> validUniqueIds) {
		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `petignoreitems` "
				+ "(`petinventoryitemid`,`ignoreitem`) SELECT `inventoryitemid`,? "
				+ "FROM `cashshoppurchases` WHERE `uniqueid` = ?")) {
			for (Map.Entry<Long, int[]> entry : petIgnoreItems.entrySet()) {
				long uniqueId = entry.getKey().longValue();
				if (!validUniqueIds.contains(uniqueId)) {
					continue;
				}
				ps.setLong(2, uniqueId);
				for (int itemId : entry.getValue()) {
					ps.setInt(1, itemId);
					ps.addBatch();
				}
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to save pet ignore items", e);
		}
	}

	// ---- Inventory Deletion ----

	/**
	 * Deletes character inventory items up to a given inventory type and storage items for the account.
	 *
	 * @param con              the connection (caller manages transaction)
	 * @param characterId      the character ID
	 * @param accountId        the account ID
	 * @param maxInvTypeByte   upper bound of inventory type byte for character items
	 * @param storageTypeByte  the storage inventory type byte value
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deleteInventoryItems(Connection con, int characterId, int accountId,
			byte maxInvTypeByte, byte storageTypeByte) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `inventoryitems` WHERE "
				+ "`characterid` = ? AND `inventorytype` <= " + maxInvTypeByte
				+ " OR `accountid` = ? AND `inventorytype` = " + storageTypeByte)) {
			ps.setInt(1, characterId);
			ps.setInt(2, accountId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete inventory items for character " + characterId, e);
		}
	}

	// ---- Offline Character Operations ----

	/**
	 * Sets a column value in the characters table for the given character name.
	 *
	 * @param con        the connection (caller manages transaction)
	 * @param charName   the character name
	 * @param column     the validated column name
	 * @param value      the short value to set
	 * @throws DataAccessException if a database error occurs
	 */
	public static void setShortColumn(Connection con, String charName, String column, short value) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET `" + column + "` = ? WHERE `name` = ?")) {
			ps.setShort(1, value);
			ps.setString(2, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to set " + column + " for character " + charName, e);
		}
	}

	/**
	 * Sets a column value (int) in the characters table for the given character name.
	 *
	 * @param con        the connection (caller manages transaction)
	 * @param charName   the character name
	 * @param column     the validated column name
	 * @param value      the int value to set
	 * @throws DataAccessException if a database error occurs
	 */
	public static void setIntColumn(Connection con, String charName, String column, int value) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET `" + column + "` = ? WHERE `name` = ?")) {
			ps.setInt(1, value);
			ps.setString(2, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to set " + column + " for character " + charName, e);
		}
	}

	/**
	 * Adds to a column value in the characters table, capped at a max.
	 *
	 * @param con        the connection (caller manages transaction)
	 * @param charName   the character name
	 * @param column     the validated column name
	 * @param value      the short value to add
	 * @param max        the maximum allowed value
	 * @throws DataAccessException if a database error occurs
	 */
	public static void addShortColumn(Connection con, String charName, String column, short value, short max) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET `" + column
				+ "` = LEAST(CAST(`" + column + "` AS UNSIGNED) + ?, ?) WHERE `name` = ?")) {
			ps.setShort(1, value);
			ps.setShort(2, max);
			ps.setString(3, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to add to " + column + " for character " + charName, e);
		}
	}

	/**
	 * Adds to a column value (int) in the characters table, capped at a max.
	 *
	 * @param con        the connection (caller manages transaction)
	 * @param charName   the character name
	 * @param column     the validated column name
	 * @param value      the int value to add
	 * @param max        the maximum allowed value
	 * @throws DataAccessException if a database error occurs
	 */
	public static void addIntColumn(Connection con, String charName, String column, int value, int max) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET `" + column
				+ "` = LEAST(CAST(`" + column + "` AS UNSIGNED) + ?, ?) WHERE `name` = ?")) {
			ps.setInt(1, value);
			ps.setInt(2, max);
			ps.setString(3, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to add to " + column + " for character " + charName, e);
		}
	}

	/**
	 * Reads a byte column from the characters table for the given character name.
	 *
	 * @param con        the connection
	 * @param charName   the character name
	 * @param column     the validated column name
	 * @return the byte value
	 * @throws DataAccessException if a database error occurs
	 */
	public static byte getByteColumn(Connection con, String charName, String column) {
		try (PreparedStatement ps = con.prepareStatement("SELECT `" + column + "` FROM `characters` WHERE `name` = ?")) {
			ps.setString(1, charName);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getByte(1);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to get " + column + " for character " + charName, e);
		}
	}

	/**
	 * Reads a short column from the characters table for the given character name.
	 *
	 * @param con        the connection
	 * @param charName   the character name
	 * @param column     the validated column name
	 * @return the short value
	 * @throws DataAccessException if a database error occurs
	 */
	public static short getShortColumn(Connection con, String charName, String column) {
		try (PreparedStatement ps = con.prepareStatement("SELECT `" + column + "` FROM `characters` WHERE `name` = ?")) {
			ps.setString(1, charName);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getShort(1);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to get " + column + " for character " + charName, e);
		}
	}

	/**
	 * Reads an int column from the characters table for the given character name.
	 *
	 * @param con        the connection
	 * @param charName   the character name
	 * @param column     the validated column name
	 * @return the int value
	 * @throws DataAccessException if a database error occurs
	 */
	public static int getIntColumn(Connection con, String charName, String column) {
		try (PreparedStatement ps = con.prepareStatement("SELECT `" + column + "` FROM `characters` WHERE `name` = ?")) {
			ps.setString(1, charName);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to get " + column + " for character " + charName, e);
		}
	}

	/**
	 * Computes the total equipment bonus for a character by name.
	 *
	 * @param con             the connection
	 * @param charName        the character name
	 * @param column          the validated equipment column name (e.g. "hp", "mp")
	 * @param equippedType    the byte value of the EQUIPPED inventory type
	 * @return the total equipment bonus (capped at Short.MAX_VALUE)
	 * @throws DataAccessException if a database error occurs
	 */
	public static short getTotalEquipBonus(Connection con, String charName, String column, byte equippedType) {
		try (PreparedStatement ps = con.prepareStatement("SELECT LEAST(SUM(`e`.`" + column + "`), " + Short.MAX_VALUE
				+ ") FROM `inventoryequipment` `e` "
				+ "LEFT JOIN `inventoryitems` `i` ON `i`.`inventoryitemid` = `e`.`inventoryitemid` "
				+ "LEFT JOIN `characters` `c` ON `i`.`characterid` = `c`.`id` "
				+ "WHERE `c`.`name` = ? AND `i`.`inventorytype` = " + equippedType)) {
			ps.setString(1, charName);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getShort(1);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to get total equip bonus for character " + charName, e);
		}
	}

	/**
	 * Inserts or ignores a map memory entry.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param key         the map memory key string
	 * @param mapId       the map ID
	 * @param spawnPoint  the spawn point
	 * @throws DataAccessException if a database error occurs
	 */
	public static void insertMapMemoryIfAbsent(Connection con, int characterId, String key, int mapId, byte spawnPoint) {
		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `mapmemory` (`characterid`,`key`,`value`,`spawnpoint`) "
				+ "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE `characterid` = `characterid`")) {
			ps.setInt(1, characterId);
			ps.setString(2, key);
			ps.setInt(3, mapId);
			ps.setByte(4, spawnPoint);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to insert map memory for character " + characterId, e);
		}
	}

	/**
	 * Loads a map memory entry for the given character and key.
	 *
	 * @param con         the connection
	 * @param characterId the character ID
	 * @param key         the map memory key string
	 * @return int array of [mapId, spawnPoint], or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static int[] loadMapMemoryEntry(Connection con, int characterId, String key) {
		try (PreparedStatement ps = con.prepareStatement("SELECT `value`,`spawnpoint` FROM `mapmemory` WHERE `characterid` = ? AND `key` = ?")) {
			ps.setInt(1, characterId);
			ps.setString(2, key);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new int[]{rs.getInt(1), rs.getByte(2)};
				}
				return null;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to load map memory entry for character " + characterId, e);
		}
	}

	/**
	 * Deletes a map memory entry for the given character and key.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param key         the map memory key string
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deleteMapMemoryEntry(Connection con, int characterId, String key) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `mapmemory` WHERE `characterid` = ? AND `key` = ?")) {
			ps.setInt(1, characterId);
			ps.setString(2, key);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete map memory entry for character " + characterId, e);
		}
	}

	/**
	 * Updates the map and spawnpoint for a character by name.
	 *
	 * @param con        the connection (caller manages transaction)
	 * @param charName   the character name
	 * @param mapId      the map ID
	 * @param spawnPoint the spawn point
	 * @throws DataAccessException if a database error occurs
	 */
	public static void updateMapAndSpawn(Connection con, String charName, int mapId, byte spawnPoint) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET `map` = ?, `spawnpoint` = ? WHERE `name` = ?")) {
			ps.setInt(1, mapId);
			ps.setByte(2, spawnPoint);
			ps.setString(3, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to update map for character " + charName, e);
		}
	}

	/**
	 * Replaces a single skill entry for an offline character.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param skillId     the skill ID
	 * @param level       the skill level
	 * @param masterLevel the master level
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceSkill(Connection con, int characterId, int skillId, byte level, byte masterLevel) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `skills` WHERE `characterid` = ? AND `skillid` = ?")) {
			ps.setInt(1, characterId);
			ps.setInt(2, skillId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete skill for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `skills` (`characterid`,`skillid`,`level`,`mastery`) VALUES (?,?,?,?)")) {
			ps.setInt(1, characterId);
			ps.setInt(2, skillId);
			ps.setByte(3, level);
			ps.setByte(4, masterLevel);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to insert skill for character " + characterId, e);
		}
	}

	/**
	 * Replaces a single quest status entry for an offline character.
	 *
	 * @param con            the connection (caller manages transaction)
	 * @param characterId    the character ID
	 * @param questId        the quest ID
	 * @param status         the quest state
	 * @param completionTime the completion time
	 * @throws DataAccessException if a database error occurs
	 */
	public static void replaceQuestStatus(Connection con, int characterId, short questId, byte status, long completionTime) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM `queststatuses` WHERE `characterid` = ? AND `questid` = ?")) {
			ps.setInt(1, characterId);
			ps.setShort(2, questId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete quest status for character " + characterId, e);
		}

		try (PreparedStatement ps = con.prepareStatement("INSERT INTO `queststatuses` (`characterid`,`questid`,`state`,`completed`) VALUES (?,?,?,?)")) {
			ps.setInt(1, characterId);
			ps.setShort(2, questId);
			ps.setByte(3, status);
			ps.setLong(4, completionTime);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to insert quest status for character " + characterId, e);
		}
	}

	/**
	 * Maxes out all equipment stats for equipped items of a character.
	 *
	 * @param con          the connection (caller manages transaction)
	 * @param charName     the character name
	 * @param equippedType the byte value of the EQUIPPED inventory type
	 * @throws DataAccessException if a database error occurs
	 */
	public static void maxAllEquipStats(Connection con, String charName, byte equippedType) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `inventoryequipment` `e` "
				+ "LEFT JOIN `inventoryitems` `i` ON `i`.`inventoryitemid` = `e`.`inventoryitemid` "
				+ "LEFT JOIN `characters` `c` ON `i`.`characterid` = `c`.`id` "
				+ "SET "
				+ "`e`.`str` = " + Short.MAX_VALUE + ", "
				+ "`e`.`dex` = " + Short.MAX_VALUE + ", "
				+ "`e`.`int` = " + Short.MAX_VALUE + ", "
				+ "`e`.`luk` = " + Short.MAX_VALUE + ", "
				+ "`e`.`hp` = 30000, "
				+ "`e`.`mp` = 30000, "
				+ "`e`.`watk` = " + Short.MAX_VALUE + ", "
				+ "`e`.`matk` = " + Short.MAX_VALUE + ", "
				+ "`e`.`wdef` = " + Short.MAX_VALUE + ", "
				+ "`e`.`mdef` = " + Short.MAX_VALUE + ", "
				+ "`e`.`acc` = " + Short.MAX_VALUE + ", "
				+ "`e`.`avoid` = " + Short.MAX_VALUE + ", "
				+ "`e`.`hands` = " + Short.MAX_VALUE + ", "
				+ "`e`.`speed` = 40, "
				+ "`e`.`jump` = 23 "
				+ "WHERE `c`.`name` = ? AND `i`.`inventorytype` = " + equippedType)) {
			ps.setString(1, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to max equip stats for character " + charName, e);
		}
	}

	/**
	 * Maxes out all inventory slots for a character.
	 *
	 * @param con      the connection (caller manages transaction)
	 * @param charName the character name
	 * @throws DataAccessException if a database error occurs
	 */
	public static void maxInventorySlots(Connection con, String charName) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET "
				+ "`equipslots` = 255, `useslots` = 255, `setupslots` = 255, `etcslots` = 255, `cashslots` = 255 "
				+ "WHERE `name` = ?")) {
			ps.setString(1, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to max inventory slots for character " + charName, e);
		}
	}

	/**
	 * Maxes out buddy list slots for a character.
	 *
	 * @param con      the connection (caller manages transaction)
	 * @param charName the character name
	 * @throws DataAccessException if a database error occurs
	 */
	public static void maxBuddySlots(Connection con, String charName) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET `buddyslots` = 255 WHERE `name` = ?")) {
			ps.setString(1, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to max buddy slots for character " + charName, e);
		}
	}

	/**
	 * Resets the login status for a character's account (used for kick).
	 *
	 * @param con      the connection (caller manages transaction)
	 * @param charName the character name
	 * @throws DataAccessException if a database error occurs
	 */
	public static void resetLoginStatus(Connection con, String charName) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `accounts` `a` LEFT JOIN `characters` `c` "
				+ "ON `c`.`accountid` = `a`.`id` SET `connected` = 0 WHERE `c`.`name` = ?")) {
			ps.setString(1, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to reset login status for character " + charName, e);
		}
	}

	/**
	 * Maxes storage slots for a character's account.
	 *
	 * @param con      the connection (caller manages transaction)
	 * @param charName the character name
	 * @throws DataAccessException if a database error occurs
	 */
	public static void maxStorageSlots(Connection con, String charName) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `accounts` SET `storageslots` = 255 "
				+ "WHERE `id` = (SELECT `accountid` FROM `characters` WHERE `name` = ?)")) {
			ps.setString(1, charName);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to max storage slots for character " + charName, e);
		}
	}

	/**
	 * Retrieves account ID for a character by name.
	 *
	 * @param charName the character name
	 * @return the account ID, or -1 if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static int getAccountIdFromName(String charName) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE);
				PreparedStatement ps = con.prepareStatement("SELECT `a`.`id` FROM `characters` `c` "
						+ "LEFT JOIN `accounts` `a` ON `c`.`accountid` = `a`.`id` WHERE `c`.`name` = ?")) {
			ps.setString(1, charName);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
				return -1;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not find account id of character " + charName, e);
		}
	}

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

	/**
	 * Skill macro record.
	 */
	public record SkillMacroRecord(byte position, String name, boolean silent, int skill1, int skill2, int skill3) {}

	/**
	 * Party record.
	 */
	public record PartyRecord(byte world, int partyId, boolean leader) {}

	/**
	 * Guild member record.
	 */
	public record GuildMemberRecord(int guildId, byte rank, byte signature, byte allianceRank) {}

	/**
	 * Updates mesos and inventory slot counts for a character.
	 *
	 * @param con         the connection (caller manages)
	 * @param characterId the character ID
	 * @param mesos       the mesos amount
	 * @param equipSlots  equip inventory max slots
	 * @param useSlots    use inventory max slots
	 * @param setupSlots  setup inventory max slots
	 * @param etcSlots    etc inventory max slots
	 * @param cashSlots   cash inventory max slots
	 * @return the number of rows updated
	 * @throws DataAccessException if a database error occurs
	 */
	public static int updateMesosAndSlots(Connection con, int characterId, int mesos,
			short equipSlots, short useSlots, short setupSlots, short etcSlots, short cashSlots) {
		try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET "
				+ "`mesos` = ?, `equipslots` = ?, `useslots` = ?, `setupslots` = ?, `etcslots` = ?, `cashslots` = ? "
				+ "WHERE `id` = ?")) {
			ps.setInt(1, mesos);
			ps.setShort(2, equipSlots);
			ps.setShort(3, useSlots);
			ps.setShort(4, setupSlots);
			ps.setShort(5, etcSlots);
			ps.setShort(6, cashSlots);
			ps.setInt(7, characterId);
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to update mesos/slots for character " + characterId, e);
		}
	}
}
