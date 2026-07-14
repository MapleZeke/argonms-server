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

package argonms.game.command;

import argonms.common.GlobalConstants;
import argonms.common.character.Player;
import argonms.common.character.inventory.Inventory;
import argonms.common.character.inventory.InventoryTools;
import argonms.common.character.inventory.Pet;
import argonms.common.net.external.CheatTracker;
import argonms.common.util.DatabaseManager;
import argonms.common.util.TimeTool;
import argonms.common.util.dao.CharacterDAO;
import argonms.game.character.ExpTables;
import argonms.game.character.MapMemoryVariable;
import argonms.game.loading.map.MapDataLoader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OfflineCharacterCommandTarget implements CommandTarget {
	private static final Logger LOG = Logger.getLogger(OfflineCharacterCommandTarget.class.getName());
	private static final Set<String> SAFE_CHARACTER_COLUMNS = Set.of(
		"ap",
		"buddyslots",
		"cashslots",
		"dex",
		"equipslots",
		"etcslots",
		"exp",
		"fame",
		"hp",
		"int",
		"job",
		"level",
		"luk",
		"map",
		"maxhp",
		"maxmp",
		"mesos",
		"mp",
		"setupslots",
		"spawnpoint",
		"sp",
		"str",
		"useslots"
	);
	private static final Set<String> SAFE_EQUIP_COLUMNS = Set.of("hp", "mp");

	private final String target;

	public OfflineCharacterCommandTarget(String name) {
		target = name;
	}

	private static String requireCharacterColumn(String column) {
		String normalized = column.toLowerCase(Locale.ROOT);
		if (!SAFE_CHARACTER_COLUMNS.contains(normalized)) {
			throw new IllegalArgumentException("Unsupported characters column: " + column);
		}
		return normalized;
	}

	private static String requireEquipColumn(String column) {
		String normalized = column.toLowerCase(Locale.ROOT);
		if (!SAFE_EQUIP_COLUMNS.contains(normalized)) {
			throw new IllegalArgumentException("Unsupported inventoryequipment column: " + column);
		}
		return normalized;
	}

	private static String inventorySlotColumn(Inventory.InventoryType type) {
		return switch (type) {
			case EQUIP -> "equipslots";
			case USE -> "useslots";
			case SETUP -> "setupslots";
			case ETC -> "etcslots";
			case CASH_SHOP -> "cashslots";
			default -> throw new IllegalArgumentException("Unsupported inventory type for slot lookup: " + type);
		};
	}

	private void setValueInCharactersTable(Connection con, String column, short value) throws SQLException {
		String safeColumn = requireCharacterColumn(column);
		CharacterDAO.setShortColumn(con, target, safeColumn, value);
	}

	private void addValueInCharactersTable(Connection con, String column, short value, short max) throws SQLException {
		String safeColumn = requireCharacterColumn(column);
		CharacterDAO.addShortColumn(con, target, safeColumn, value, max);
	}

	private void setValueInCharactersTable(Connection con, String column, int value) throws SQLException {
		String safeColumn = requireCharacterColumn(column);
		CharacterDAO.setIntColumn(con, target, safeColumn, value);
	}

	private void addValueInCharactersTable(Connection con, String column, int value, int max) throws SQLException {
		String safeColumn = requireCharacterColumn(column);
		CharacterDAO.addIntColumn(con, target, safeColumn, value, max);
	}

	private byte getByteValueInCharactersTable(Connection con, String column) throws SQLException {
		String safeColumn = requireCharacterColumn(column);
		return CharacterDAO.getByteColumn(con, target, safeColumn);
	}

	private int getIntValueInCharactersTable(Connection con, String column) throws SQLException {
		String safeColumn = requireCharacterColumn(column);
		return CharacterDAO.getIntColumn(con, target, safeColumn);
	}

	private short getShortValueInCharactersTable(Connection con, String column) throws SQLException {
		String safeColumn = requireCharacterColumn(column);
		return CharacterDAO.getShortColumn(con, target, safeColumn);
	}

	private short getTotalEquipBonus(Connection con, String column) throws SQLException {
		String safeColumn = requireEquipColumn(column);
		return CharacterDAO.getTotalEquipBonus(con, target, safeColumn, Inventory.InventoryType.EQUIPPED.byteValue());
	}

	private int getInt(CharacterManipulation update) {
		return ((Integer) update.getValue()).intValue();
	}

	private short getShort(CharacterManipulation update) {
		return ((Short) update.getValue()).shortValue();
	}

	@Override
	public void mutate(List<CharacterManipulation> updates) {
		int prevTransactionIsolation = Connection.TRANSACTION_REPEATABLE_READ;
		boolean prevAutoCommit = true;
		try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
			try {
				prevTransactionIsolation = con.getTransactionIsolation();
				prevAutoCommit = con.getAutoCommit();
				con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
				con.setAutoCommit(false);

				for (CharacterManipulation update : updates) {
					switch (update.getKey()) {
					case CHANGE_MAP: {
						MapValue value = (MapValue) update.getValue();

						if (value.mapId / 100 == MapValue.FREE_MARKET_MAP_ID / 100 || value.mapId == MapValue.JAIL_MAP_ID) {
							int map = getIntValueInCharactersTable(con, "map");
							byte spawnPoint = getByteValueInCharactersTable(con, "spawnpoint");

							CharacterDAO.insertMapMemoryIfAbsent(con, Player.getIdFromName(target),
									value.mapId == MapValue.JAIL_MAP_ID ? MapMemoryVariable.JAIL.toString() : MapMemoryVariable.FREE_MARKET.toString(),
									map, spawnPoint);
						}

						CharacterDAO.updateMapAndSpawn(con, target, value.mapId, value.spawnPoint);
						break;
					}
					case CHANGE_CHANNEL:
						throw new UnsupportedOperationException("Cannot change channel of offline player");
					case ADD_LEVEL: {
						addValueInCharactersTable(con, "level", getShort(update), GlobalConstants.MAX_LEVEL);
						short level = getShortValueInCharactersTable(con, "level");
						//clip exp in case we subtracted levels or reached max level
						addValueInCharactersTable(con, "exp", 0, level < GlobalConstants.MAX_LEVEL ? ExpTables.getExpForPlayerLevel(level) - 1 : 0);
						break;
					}
					case SET_LEVEL: {
						short level = getShort(update);
						setValueInCharactersTable(con, "level", level);
						//clip exp in case we subtracted levels or reached max level
						addValueInCharactersTable(con, "exp", 0, level < GlobalConstants.MAX_LEVEL ? ExpTables.getExpForPlayerLevel(level) - 1 : 0);
						break;
					}
					case SET_JOB:
						setValueInCharactersTable(con, "job", getShort(update));
						break;
					case ADD_STR:
						addValueInCharactersTable(con, "str", getShort(update), Short.MAX_VALUE);
						break;
					case SET_STR:
						setValueInCharactersTable(con, "str", getShort(update));
						break;
					case ADD_DEX:
						addValueInCharactersTable(con, "dex", getShort(update), Short.MAX_VALUE);
						break;
					case SET_DEX:
						setValueInCharactersTable(con, "dex", getShort(update));
						break;
					case ADD_INT:
						addValueInCharactersTable(con, "int", getShort(update), Short.MAX_VALUE);
						break;
					case SET_INT:
						setValueInCharactersTable(con, "int", getShort(update));
						break;
					case ADD_LUK:
						addValueInCharactersTable(con, "luk", getShort(update), Short.MAX_VALUE);
						break;
					case SET_LUK:
						setValueInCharactersTable(con, "luk", getShort(update));
						break;
					case ADD_AP:
						addValueInCharactersTable(con, "ap", getShort(update), Short.MAX_VALUE);
						break;
					case SET_AP:
						setValueInCharactersTable(con, "ap", getShort(update));
						break;
					case ADD_SP:
						addValueInCharactersTable(con, "sp", getShort(update), Short.MAX_VALUE);
						break;
					case SET_SP:
						setValueInCharactersTable(con, "sp", getShort(update));
						break;
					case ADD_MAX_HP:
						addValueInCharactersTable(con, "maxhp", getShort(update), 30000);
						break;
					case SET_MAX_HP:
						setValueInCharactersTable(con, "maxhp", getShort(update));
						break;
					case ADD_MAX_MP:
						addValueInCharactersTable(con, "maxmp", getShort(update), 30000);
						break;
					case SET_MAX_MP:
						setValueInCharactersTable(con, "maxmp", getShort(update));
						break;
					case ADD_HP: {
						short maxHp = (short) Math.min(getShortValueInCharactersTable(con, "maxhp") + getTotalEquipBonus(con, "hp"), 30000);
						addValueInCharactersTable(con, "hp", getShort(update), maxHp);
						break;
					}
					case SET_HP: {
						short maxHp = (short) Math.min(getShortValueInCharactersTable(con, "maxhp") + getTotalEquipBonus(con, "hp"), 30000);
						setValueInCharactersTable(con, "hp", (short) Math.min(getShort(update), maxHp));
						break;
					}
					case ADD_MP: {
						short maxMp = (short) Math.min(getShortValueInCharactersTable(con, "maxmp") + getTotalEquipBonus(con, "mp"), 30000);
						addValueInCharactersTable(con, "mp", getShort(update), maxMp);
						break;
					}
					case SET_MP: {
						short maxMp = (short) Math.min(getShortValueInCharactersTable(con, "maxmp") + getTotalEquipBonus(con, "mp"), 30000);
						setValueInCharactersTable(con, "mp", (short) Math.min(getShort(update), maxMp));
						break;
					}
					case ADD_FAME:
						addValueInCharactersTable(con, "fame", getShort(update), Short.MAX_VALUE);
						break;
					case SET_FAME:
						setValueInCharactersTable(con, "fame", getShort(update));
						break;
					case ADD_EXP: {
						short level = getShortValueInCharactersTable(con, "level");
						addValueInCharactersTable(con, "exp", getInt(update), level < GlobalConstants.MAX_LEVEL ? ExpTables.getExpForPlayerLevel(level) - 1 : 0);
						break;
					}
					case SET_EXP: {
						setValueInCharactersTable(con, "exp", getInt(update));
						short level = getShortValueInCharactersTable(con, "level");
						addValueInCharactersTable(con, "exp", 0, level < GlobalConstants.MAX_LEVEL ? ExpTables.getExpForPlayerLevel(level) - 1 : 0);
						break;
					}
					case ADD_MESO:
						addValueInCharactersTable(con, "mesos", getInt(update), Integer.MAX_VALUE);
						break;
					case SET_MESO:
						setValueInCharactersTable(con, "mesos", getInt(update));
						break;
					case SET_SKILL_LEVEL: {
						SkillValue value = (SkillValue) update.getValue();
						int characterId = Player.getIdFromName(target);
						CharacterDAO.replaceSkill(con, characterId, value.skillId, value.skillLevel, value.skillMasterLevel);
						break;
					}
					case SET_QUEST_STATUS: {
						QuestStatusValue value = (QuestStatusValue) update.getValue();
						int characterId = Player.getIdFromName(target);
						CharacterDAO.replaceQuestStatus(con, characterId, value.questId, value.status, value.completionTime);
						break;
					}
					case ADD_ITEM: {
						ItemValue value = (ItemValue) update.getValue();
						Inventory.InventoryType type = InventoryTools.getCategory(value.itemId);
						Pet[] pets = new Pet[3];
						String slotColumn = inventorySlotColumn(type);

						int accountId;
						int characterId;
						Map<Inventory.InventoryType, Inventory> inventories;
						try (PreparedStatement ps = con.prepareStatement("SELECT `accountid`,`id`,`" + slotColumn + "` FROM `characters` WHERE `name` = ?")) {
							ps.setString(1, target);
							try (ResultSet rs = ps.executeQuery()) {
								rs.next(); //assert this is true
								accountId = rs.getInt(1);
								characterId = rs.getInt(2);
								inventories = Collections.singletonMap(type, new Inventory(rs.getShort(2)));
							}
						}

						try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `inventoryitems` WHERE "
								+ "`characterid` = ? AND `inventorytype` = ?")) {
							ps.setInt(1, characterId);
							ps.setByte(2, type.byteValue());
							try (ResultSet rs = ps.executeQuery()) {
								Player.loadInventory(pets, con, rs, inventories);
							}
						}

						if (value.quantity > 0) {
							InventoryTools.addToInventory(inventories.get(type), value.itemId, value.quantity);
						} else {
							Inventory inv = inventories.get(type);
							int quantity;
							if (value.quantity == Integer.MIN_VALUE) {
								quantity = InventoryTools.getAmountOfItem(inv, value.itemId);
								if (type == Inventory.InventoryType.EQUIP) {
									quantity += InventoryTools.getAmountOfItem(inv, value.itemId);
								}
							} else {
								quantity = -value.quantity;
							}
							InventoryTools.removeFromInventory(inv, value.itemId, quantity, true);
						}

						try (PreparedStatement ps = con.prepareStatement("DELETE FROM `inventoryitems` WHERE "
								+ "`characterid` = ? AND `inventorytype` = ?")) {
							ps.setInt(1, characterId);
							ps.setByte(2, type.byteValue());
							ps.executeUpdate();
						}

						Player.commitInventory(characterId, accountId, pets, con, inventories);
						break;
					}
					case CANCEL_DEBUFFS:
						//offline characters don't have any active status effects
						break;
					case MAX_ALL_EQUIP_STATS:
						CharacterDAO.maxAllEquipStats(con, target, Inventory.InventoryType.EQUIPPED.byteValue());
						break;
					case MAX_INVENTORY_SLOTS:
						CharacterDAO.maxInventorySlots(con, target);
						CharacterDAO.maxStorageSlots(con, target);
						break;
					case MAX_BUDDY_LIST_SLOTS:
						CharacterDAO.maxBuddySlots(con, target);
						break;
					case BAN: {
						BanValue value = (BanValue) update.getValue();
						Calendar cal = TimeTool.currentDateTime();
						cal.setTimeInMillis(value.expireTimestamp);
						CheatTracker.get(target).ban(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, value.banner, value.reason, cal);
						break;
					}
					case KICK: {
						//just make sure the character's login status is reset...
						CharacterDAO.resetLoginStatus(con, target);
						break;
					}
					case STUN:
						break;
					case CLEAR_INVENTORY_SLOTS: {
						InventorySlotRangeValue value = (InventorySlotRangeValue) update.getValue();
						Pet[] pets = new Pet[3];
						String slotColumn = inventorySlotColumn(value.type);

						int accountId;
						int characterId;
						Map<Inventory.InventoryType, Inventory> inventories;
						try (PreparedStatement ps = con.prepareStatement("SELECT `accountid`,`id`,`" + slotColumn + "` FROM `characters` WHERE `name` = ?")) {
							ps.setString(1, target);
							try (ResultSet rs = ps.executeQuery()) {
								rs.next(); //assert this is true
								accountId = rs.getInt(1);
								characterId = rs.getInt(2);
								inventories = Collections.singletonMap(value.type, new Inventory(rs.getShort(2)));
							}
						}

						try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `inventoryitems` WHERE "
								+ "`characterid` = ? AND `inventorytype` = ?")) {
							ps.setInt(1, characterId);
							ps.setByte(2, value.type.byteValue());
							try (ResultSet rs = ps.executeQuery()) {
								Player.loadInventory(pets, con, rs, inventories);
							}
						}

						Inventory inv = inventories.get(value.type);
						short upperBound = (short) Math.min(value.endSlot, inv.getMaxSlots());
						for (short slot = value.startSlot; slot <= upperBound; slot++) {
							inv.remove(slot);
						}

						try (PreparedStatement ps = con.prepareStatement("DELETE FROM `inventoryitems` WHERE "
								+ "`characterid` = ? AND `inventorytype` = ?")) {
							ps.setInt(1, characterId);
							ps.setByte(2, value.type.byteValue());
							ps.executeUpdate();
						}

						Player.commitInventory(characterId, accountId, pets, con, inventories);
						break;
					}
					case RETURN_TO_REMEMBERED_MAP: {
						MapMemoryVariable value = (MapMemoryVariable) update.getValue();
						int characterId = Player.getIdFromName(target);

						int[] entry = CharacterDAO.loadMapMemoryEntry(con, characterId, value.toString());

						if (entry != null) {
							int mapId = entry[0];
							byte spawnPoint = (byte) entry[1];
							if (mapId != GlobalConstants.NULL_MAP && spawnPoint != -1) {
								CharacterDAO.deleteMapMemoryEntry(con, characterId, value.toString());
								CharacterDAO.updateMapAndSpawn(con, target, mapId, spawnPoint);
							}
						}
						break;
					}
					}
				}

				con.commit();
			} catch (Throwable e) {
				LOG.log(Level.WARNING, "Could not manipulate stat of offline character. Rolling back all changes...", e);
				try {
					con.rollback();
				} catch (SQLException ex2) {
					LOG.log(Level.WARNING, "Error rolling back stat manipulations of offline character.", ex2);
				}
			} finally {
				try {
					con.setAutoCommit(prevAutoCommit);
					con.setTransactionIsolation(prevTransactionIsolation);
				} catch (SQLException ex) {
					LOG.log(Level.WARNING, "Could not reset Connection config after manipulating offline character " + target, ex);
				}
			}
		} catch (SQLException e) {
			LOG.log(Level.WARNING, "Could not manipulate stat of offline character. Rolling back all changes...", e);
		}
	}

	@Override
	public Object access(CharacterProperty key) {
		try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
			switch (key) {
				case MAP:
					return new MapValue(getIntValueInCharactersTable(con, "map"), getByteValueInCharactersTable(con, "spawnpoint"), (byte) 0);
				case CHANNEL:
					return Byte.valueOf((byte) 0);
				case POSITION:
					return MapDataLoader.getInstance().getMapStats(getIntValueInCharactersTable(con, "map")).getPortals().get(Byte.valueOf(getByteValueInCharactersTable(con, "spawnpoint"))).getPosition();
				case PLAYER_ID:
					return Integer.valueOf(Player.getIdFromName(target));
				default:
					return null;
			}
		} catch (SQLException e) {
			LOG.log(Level.WARNING, "Could not retrieve stat of offline character", e);
			return null;
		}
	}
}
