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

import argonms.common.character.Player;
import argonms.common.character.inventory.IInventory;
import argonms.common.character.inventory.Inventory.InventoryType;
import argonms.common.character.inventory.InventorySlot;
import argonms.common.character.inventory.Pet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Data Access Object for inventory persistence operations.
 *
 * <p>Provides a thin DAO facade over the existing inventory commit/load logic.
 * All public methods are stateless, accept a {@link Connection} for transaction
 * participation, and throw {@link DataAccessException} on failure.
 */
public final class InventoryDAO {
	private static final Logger LOG = Logger.getLogger(InventoryDAO.class.getName());

	private InventoryDAO() {
	}

	/**
	 * Deletes all inventory items for the given character (up to and including
	 * the specified maximum inventory type).
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param maxType     the maximum inventory type ordinal to delete (inclusive)
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deleteInventoryItems(Connection con, int characterId, byte maxType) {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE FROM `inventoryitems` WHERE `characterid` = ? AND `inventorytype` <= ?")) {
			ps.setInt(1, characterId);
			ps.setByte(2, maxType);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete inventory items for character " + characterId, e);
		}
	}

	/**
	 * Deletes all inventory items for the character and also storage items
	 * belonging to the account.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param accountId   the account ID (for storage deletion)
	 * @param maxType     the maximum character-inventory type ordinal to delete
	 * @param storageType the storage inventory type ordinal
	 * @throws DataAccessException if a database error occurs
	 */
	public static void deleteInventoryAndStorage(Connection con, int characterId, int accountId,
			byte maxType, byte storageType) {
		try (PreparedStatement ps = con.prepareStatement(
				"DELETE FROM `inventoryitems` WHERE "
				+ "`characterid` = ? AND `inventorytype` <= ? "
				+ "OR `accountid` = ? AND `inventorytype` = ?")) {
			ps.setInt(1, characterId);
			ps.setByte(2, maxType);
			ps.setInt(3, accountId);
			ps.setByte(4, storageType);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to delete inventory/storage for character " + characterId, e);
		}
	}

	/**
	 * Commits all inventory items to the database. This delegates to
	 * {@link Player#commitInventory(int, int, Pet[], Connection, Map)} and
	 * wraps any {@code SQLException} in a {@link DataAccessException}.
	 *
	 * @param con         the connection (caller manages transaction)
	 * @param characterId the character ID
	 * @param accountId   the account ID
	 * @param pets        the pet array (for pet position lookup)
	 * @param inventories the inventories to persist
	 * @throws DataAccessException if a database error occurs
	 */
	public static void commitInventory(Connection con, int characterId, int accountId,
			Pet[] pets, Map<InventoryType, ? extends IInventory> inventories) {
		try {
			Player.commitInventory(characterId, accountId, pets, con, inventories);
		} catch (SQLException e) {
			throw new DataAccessException("Failed to commit inventory for character " + characterId, e);
		}
	}

	/**
	 * Loads inventory items from a result set. This is a convenience wrapper
	 * that delegates to {@link Player#loadInventory(Pet[], Connection, ResultSet, Map)}.
	 *
	 * @param pets        the pet array for pet position binding
	 * @param con         the connection for subqueries (rings, mounts, etc.)
	 * @param rs          the result set positioned at the first inventory item row
	 * @param inventories the target inventory map to populate
	 * @throws DataAccessException if a database error occurs
	 */
	public static void loadInventory(Pet[] pets, Connection con, ResultSet rs,
			Map<InventoryType, ? extends IInventory> inventories) {
		try {
			Player.loadInventory(pets, con, rs, inventories);
		} catch (SQLException e) {
			throw new DataAccessException("Failed to load inventory", e);
		}
	}

	/**
	 * Inserts pet-ignore-items for cash inventory items.
	 *
	 * @param con           the connection (caller manages transaction)
	 * @param petIgnoreItems map of unique item ID → array of ignore item IDs
	 * @param cashInventory  the cash inventory to verify item presence
	 * @throws DataAccessException if a database error occurs
	 */
	public static void savePetIgnoreItems(Connection con,
			Map<Long, int[]> petIgnoreItems, IInventory cashInventory) {
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT INTO `petignoreitems` (`petinventoryitemid`,`ignoreitem`) "
				+ "SELECT `inventoryitemid`,? FROM `cashshoppurchases` WHERE `uniqueid` = ?")) {
			for (Map.Entry<Long, int[]> entry : petIgnoreItems.entrySet()) {
				long uniqueId = entry.getKey();
				boolean inInventory = false;
				for (InventorySlot item : cashInventory.getAll().values()) {
					if (item.getUniqueId() == uniqueId) {
						inInventory = true;
						break;
					}
				}
				if (!inInventory) {
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
}
