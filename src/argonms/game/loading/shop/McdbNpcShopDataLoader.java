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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

	private boolean loadRechargeTier(int tier, ResultSet rs) throws SQLException {
		boolean more;
		Map<Integer, Double> tierData = new HashMap<>();
		do {
			int itemId = rs.getInt(2);
			double price = rs.getDouble(3);
			tierData.put(Integer.valueOf(itemId), Double.valueOf(price));
		} while ((more = rs.next()) && rs.getInt(1) == tier);
		rechargeTiers.put(Integer.valueOf(tier), Collections.unmodifiableMap(tierData));
		return more;
	}

	@Override
	protected void load(int npcid) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ);
				PreparedStatement ps = con.prepareStatement("SELECT `shopid`,`rechargetier` FROM `shopdata` WHERE `npcid` = ?")) {
			ps.setInt(1, npcid);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					Map<Integer, Double> rechargeables;
					int rechargeTier = rs.getInt(2);
					if (rechargeTier == 0) {
						rechargeables = Collections.emptyMap();
					} else {
						if (!rechargeTiers.containsKey(Integer.valueOf(rechargeTier))) {
							try (PreparedStatement rps = con.prepareStatement("SELECT `itemid`,`price` FROM `rechargedata` WHERE `id` = ?")) {
								rps.setInt(1, rechargeTier);
								try (ResultSet rrs = rps.executeQuery()) {
									if (rrs.next()) {
										loadRechargeTier(rechargeTier, rrs);
									}
								}
							}
						}
						rechargeables = rechargeTiers.get(Integer.valueOf(rechargeTier));
					}

					List<NpcShop.ShopSlot> items = new ArrayList<>();
					try (PreparedStatement ips = con.prepareStatement("SELECT `itemid`,`quantity`,`price` FROM `shopitemdata` WHERE `shopid` = ? ORDER BY `sort` DESC")) {
						ips.setInt(1, rs.getInt(1));
						try (ResultSet irs = ips.executeQuery()) {
							while (irs.next()) {
								items.add(new NpcShop.ShopSlot(irs.getInt(1), irs.getShort(2), irs.getInt(3)));
							}
						}
					}

					//saves a bit of memory - shops that use the same recharge tier
					//just use aliases of the same immutable map
					NpcShop shop = new NpcShop.McdbNpcShopStock(rechargeables, items);
					loadedShops.put(Integer.valueOf(npcid), shop);
				} else {
					loadedShops.put(Integer.valueOf(npcid), null);
				}
			}
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not read MCDB data for shop of NPC " + npcid, ex);
		}
	}

	@Override
	public boolean loadAll() {
		List<NpcShop.ShopSlot> items;
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ)) {
			try (PreparedStatement ps = con.prepareStatement("SELECT `id`,`itemid`,`price` FROM `rechargedata` ORDER BY `id` ASC");
					ResultSet rs = ps.executeQuery()) {
				boolean more = false;
				while (more || rs.next()) {
					more = loadRechargeTier(rs.getInt(1), rs);
				}
			}

			Map<Integer, List<NpcShop.ShopSlot>> shopItems = new HashMap<>();
			try (PreparedStatement ps = con.prepareStatement("SELECT `shopid`,`itemid`,`quantity`,`price` FROM `shopitemdata` ORDER BY `shopid`,`sort` DESC");
					ResultSet rs = ps.executeQuery()) {
				boolean more = false;
				while (more || rs.next()) {
					int shopId = rs.getInt(1);
					items = new ArrayList<>();
					do {
						items.add(new NpcShop.ShopSlot(rs.getInt(2), rs.getShort(3), rs.getInt(4)));
					}
					while ((more = rs.next()) && rs.getInt(1) == shopId);
					shopItems.put(Integer.valueOf(shopId), items);
				}
			}

			try (PreparedStatement ps = con.prepareStatement("SELECT `shopid`,`npcid`,`rechargetier` FROM `shopdata`");
					ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					int shopId = rs.getInt(1);
					int npcId = rs.getInt(2);
					int rechargeTier = rs.getInt(3);
					Map<Integer, Double> rechargeables = rechargeTier != 0 ? rechargeTiers.get(Integer.valueOf(rechargeTier)) : Collections.emptyMap();
					loadedShops.put(Integer.valueOf(npcId), new NpcShop.McdbNpcShopStock(rechargeables != null ? rechargeables : Collections.emptyMap(), shopItems.get(Integer.valueOf(shopId))));
				}
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
		//if loadedShops.containsKey npcid but loadedShops.get npcid is null, that means the shop of npcid could not be loaded
		if (loadedShops.containsKey(Integer.valueOf(npcid))) {
			return false;
		}
		try (Connection con = DatabaseManager.getConnection(DatabaseType.WZ);
				PreparedStatement ps = con.prepareStatement("SELECT * FROM `shopdata` WHERE `npcid` = ?")) {
			ps.setInt(1, npcid);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return true;
				}
				return false;
			}
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not use MCDB to determine whether npc " + npcid + " has a shop.", ex);
			return false;
		}
	}
}
