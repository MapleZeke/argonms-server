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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for NPC shop data queries from both WZ and STATE databases.
 */
public final class NpcShopDataDAO {

	private NpcShopDataDAO() {
	}

	/**
	 * Shop item slot record.
	 */
	public record ShopSlotRecord(int itemId, short quantity, int price) {}

	/**
	 * Shop definition record from MCDB shopdata table.
	 */
	public record ShopRecord(int shopId, int npcId, int rechargeTier) {}

	/**
	 * Recharge tier entry.
	 */
	public record RechargeEntry(int itemId, double price) {}

	// ---- MCDB (WZ database) queries ----

	/**
	 * Loads shop data for an NPC from the WZ database.
	 *
	 * @param con   the WZ connection (caller manages)
	 * @param npcId the NPC ID
	 * @return the shop record, or null if not found
	 * @throws DataAccessException if a database error occurs
	 */
	public static ShopRecord loadMcdbShop(Connection con, int npcId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `shopid`,`rechargetier` FROM `shopdata` WHERE `npcid` = ?")) {
			ps.setInt(1, npcId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new ShopRecord(rs.getInt(1), npcId, rs.getInt(2));
				}
				return null;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load shop data for NPC " + npcId, e);
		}
	}

	/**
	 * Loads all MCDB shop records.
	 *
	 * @param con the WZ connection (caller manages)
	 * @return list of all shop records
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<ShopRecord> loadAllMcdbShops(Connection con) {
		List<ShopRecord> shops = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `shopid`,`npcid`,`rechargetier` FROM `shopdata`");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				shops.add(new ShopRecord(rs.getInt(1), rs.getInt(2), rs.getInt(3)));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load all MCDB shop data", e);
		}
		return shops;
	}

	/**
	 * Loads recharge data for a specific tier.
	 *
	 * @param con  the WZ connection (caller manages)
	 * @param tier the recharge tier ID
	 * @return list of recharge entries
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<RechargeEntry> loadRechargeTier(Connection con, int tier) {
		List<RechargeEntry> entries = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `itemid`,`price` FROM `rechargedata` WHERE `id` = ?")) {
			ps.setInt(1, tier);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					entries.add(new RechargeEntry(rs.getInt(1), rs.getDouble(2)));
				}
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load recharge tier " + tier, e);
		}
		return entries;
	}

	/**
	 * Loads all recharge data grouped by tier.
	 *
	 * @param con the WZ connection (caller manages)
	 * @return map of tier ID → list of recharge entries
	 * @throws DataAccessException if a database error occurs
	 */
	public static Map<Integer, List<RechargeEntry>> loadAllRechargeTiers(Connection con) {
		Map<Integer, List<RechargeEntry>> tiers = new HashMap<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `id`,`itemid`,`price` FROM `rechargedata` ORDER BY `id` ASC");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int tierId = rs.getInt(1);
				tiers.computeIfAbsent(tierId, k -> new ArrayList<>())
						.add(new RechargeEntry(rs.getInt(2), rs.getDouble(3)));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load all recharge tiers", e);
		}
		return tiers;
	}

	/**
	 * Loads shop items for a specific shop ID.
	 *
	 * @param con    the WZ connection (caller manages)
	 * @param shopId the shop ID
	 * @return list of shop slots ordered by sort descending
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<ShopSlotRecord> loadMcdbShopItems(Connection con, int shopId) {
		List<ShopSlotRecord> items = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `itemid`,`quantity`,`price` FROM `shopitemdata` WHERE `shopid` = ? ORDER BY `sort` DESC")) {
			ps.setInt(1, shopId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					items.add(new ShopSlotRecord(rs.getInt(1), rs.getShort(2), rs.getInt(3)));
				}
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load shop items for shop " + shopId, e);
		}
		return items;
	}

	/**
	 * Loads all MCDB shop items grouped by shop ID.
	 *
	 * @param con the WZ connection (caller manages)
	 * @return map of shop ID → list of shop slots
	 * @throws DataAccessException if a database error occurs
	 */
	public static Map<Integer, List<ShopSlotRecord>> loadAllMcdbShopItems(Connection con) {
		Map<Integer, List<ShopSlotRecord>> shopItems = new HashMap<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `shopid`,`itemid`,`quantity`,`price` FROM `shopitemdata` ORDER BY `shopid`,`sort` DESC");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int shopId = rs.getInt(1);
				shopItems.computeIfAbsent(shopId, k -> new ArrayList<>())
						.add(new ShopSlotRecord(rs.getInt(2), rs.getShort(3), rs.getInt(4)));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load all MCDB shop items", e);
		}
		return shopItems;
	}

	/**
	 * Checks whether a shop exists for a specific NPC in the WZ database.
	 *
	 * @param con   the WZ connection (caller manages)
	 * @param npcId the NPC ID
	 * @return true if the shop exists
	 * @throws DataAccessException if a database error occurs
	 */
	public static boolean mcdbShopExists(Connection con, int npcId) {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT 1 FROM `shopdata` WHERE `npcid` = ? LIMIT 1")) {
			ps.setInt(1, npcId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not check shop existence for NPC " + npcId, e);
		}
	}

	// ---- Default (STATE database) queries ----

	/**
	 * Loads shop items for an NPC from the STATE database (default shops).
	 *
	 * @param con   the STATE connection (caller manages)
	 * @param npcId the NPC ID
	 * @return list of shop slots ordered by position ascending
	 * @throws DataAccessException if a database error occurs
	 */
	public static List<ShopSlotRecord> loadDefaultShopItems(Connection con, int npcId) {
		List<ShopSlotRecord> items = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `itemid`,`price` FROM `shopitems` WHERE `npcid` = ? ORDER BY `position` ASC")) {
			ps.setInt(1, npcId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					items.add(new ShopSlotRecord(rs.getInt(1), (short) 1, rs.getInt(2)));
				}
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load default shop items for NPC " + npcId, e);
		}
		return items;
	}

	/**
	 * Loads all default shop items grouped by NPC ID.
	 *
	 * @param con the STATE connection (caller manages)
	 * @return map of NPC ID → list of shop slots
	 * @throws DataAccessException if a database error occurs
	 */
	public static Map<Integer, List<ShopSlotRecord>> loadAllDefaultShopItems(Connection con) {
		Map<Integer, List<ShopSlotRecord>> shopItems = new HashMap<>();
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT `npcid`,`itemid`,`price` FROM `shopitems` ORDER BY `npcid`,`position` ASC");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int npcId = rs.getInt(1);
				shopItems.computeIfAbsent(npcId, k -> new ArrayList<>())
						.add(new ShopSlotRecord(rs.getInt(2), (short) 1, rs.getInt(3)));
			}
		} catch (SQLException e) {
			throw new DataAccessException("Could not load all default shop items", e);
		}
		return shopItems;
	}
}
