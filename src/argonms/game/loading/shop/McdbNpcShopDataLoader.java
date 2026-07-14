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

package argonms.game.loading.shop;

import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import argonms.common.util.dao.NpcShopDataDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class McdbNpcShopDataLoader extends NpcShopDataLoader {
	private static final Logger LOG = Logger.getLogger(McdbNpcShopDataLoader.class.getName());

	private final Map<Integer, Map<Integer, Double>> rechargeTiers;

	protected McdbNpcShopDataLoader() {
		rechargeTiers = new HashMap<>();
	}

	@Override
	protected void load(int npcid) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ)) {
			NpcShopDataDAO.ShopRecord shop = NpcShopDataDAO.loadMcdbShop(con, npcid);
			if (shop != null) {
				Map<Integer, Double> rechargeables;
				int rechargeTier = shop.rechargeTier();
				if (rechargeTier == 0) {
					rechargeables = Collections.emptyMap();
				} else {
					if (!rechargeTiers.containsKey(Integer.valueOf(rechargeTier))) {
						List<NpcShopDataDAO.RechargeEntry> entries = NpcShopDataDAO.loadRechargeTier(con, rechargeTier);
						Map<Integer, Double> tierData = new HashMap<>();
						for (NpcShopDataDAO.RechargeEntry e : entries) {
							tierData.put(e.itemId(), e.price());
						}
						rechargeTiers.put(Integer.valueOf(rechargeTier), Collections.unmodifiableMap(tierData));
					}
					rechargeables = rechargeTiers.get(Integer.valueOf(rechargeTier));
				}

				List<NpcShopDataDAO.ShopSlotRecord> records = NpcShopDataDAO.loadMcdbShopItems(con, shop.shopId());
				List<NpcShop.ShopSlot> items = new ArrayList<>();
				for (NpcShopDataDAO.ShopSlotRecord r : records) {
					items.add(new NpcShop.ShopSlot(r.itemId(), r.quantity(), r.price()));
				}

				NpcShop npcShop = new NpcShop.McdbNpcShopStock(rechargeables, items);
				loadedShops.put(Integer.valueOf(npcid), npcShop);
			} else {
				loadedShops.put(Integer.valueOf(npcid), null);
			}
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not read MCDB data for shop of NPC " + npcid, ex);
		}
	}

	@Override
	public boolean loadAll() {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ)) {
			// Load all recharge tiers
			Map<Integer, List<NpcShopDataDAO.RechargeEntry>> allTiers = NpcShopDataDAO.loadAllRechargeTiers(con);
			for (Map.Entry<Integer, List<NpcShopDataDAO.RechargeEntry>> entry : allTiers.entrySet()) {
				Map<Integer, Double> tierData = new HashMap<>();
				for (NpcShopDataDAO.RechargeEntry e : entry.getValue()) {
					tierData.put(e.itemId(), e.price());
				}
				rechargeTiers.put(entry.getKey(), Collections.unmodifiableMap(tierData));
			}

			// Load all shop items grouped by shop ID
			Map<Integer, List<NpcShopDataDAO.ShopSlotRecord>> allShopItems = NpcShopDataDAO.loadAllMcdbShopItems(con);

			// Load all shops and wire them up
			List<NpcShopDataDAO.ShopRecord> shops = NpcShopDataDAO.loadAllMcdbShops(con);
			for (NpcShopDataDAO.ShopRecord shop : shops) {
				Map<Integer, Double> rechargeables = shop.rechargeTier() != 0
						? rechargeTiers.get(Integer.valueOf(shop.rechargeTier()))
						: Collections.emptyMap();
				List<NpcShopDataDAO.ShopSlotRecord> records = allShopItems.get(Integer.valueOf(shop.shopId()));
				List<NpcShop.ShopSlot> items = new ArrayList<>();
				if (records != null) {
					for (NpcShopDataDAO.ShopSlotRecord r : records) {
						items.add(new NpcShop.ShopSlot(r.itemId(), r.quantity(), r.price()));
					}
				}
				loadedShops.put(Integer.valueOf(shop.npcId()),
						new NpcShop.McdbNpcShopStock(rechargeables != null ? rechargeables : Collections.emptyMap(), items));
			}
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not load all shop data from MCDB.", ex);
			return false;
		}
		return false;
	}

	@Override
	public boolean canLoad(int npcid) {
		if (loadedShops.get(Integer.valueOf(npcid)) != null) {
			return true;
		}
		if (loadedShops.containsKey(Integer.valueOf(npcid))) {
			return false;
		}
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ)) {
			return NpcShopDataDAO.mcdbShopExists(con, npcid);
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not use MCDB to determine whether npc " + npcid + " has a shop.", ex);
			return false;
		}
	}
}
