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

package argonms.shop.character;

import argonms.common.character.BuddyListEntry;
import argonms.common.character.Cooldown;
import argonms.common.character.LoggedInPlayer;
import argonms.common.character.QuestEntry;
import argonms.common.character.ShopPlayerContinuation;
import argonms.common.character.SkillEntry;
import argonms.common.character.inventory.IInventory;
import argonms.common.character.inventory.Inventory.InventoryType;
import argonms.common.character.inventory.InventorySlot;
import argonms.common.util.DatabaseManager;
import argonms.common.util.DatabaseManager.DatabaseType;
import argonms.common.util.dao.AccountDAO;
import argonms.common.util.dao.CharacterDAO;
import argonms.shop.ShopServer;
import argonms.shop.net.external.CashShopPackets;
import argonms.shop.net.external.ShopClient;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ShopCharacter extends LoggedInPlayer {
	private static final Logger LOG = Logger.getLogger(ShopCharacter.class.getName());

	public static final int PAYPAL_NX = 1;
	public static final int MAPLE_POINTS = 2;
	public static final int GAME_CARD_NX = 4;

	private ShopClient client;

	private ShopBuddyList buddies;
	private int partyId;
	private int guildId;
	private byte maxCharacters;
	private CashShopStaging shopInventory;
	private volatile int mesos;
	private int birthday;
	private final AtomicInteger[] cashShopBalance;
	private final Map<Integer, SkillEntry> skills;
	private final Map<Integer, Cooldown> cooldowns;
	private final Map<Short, QuestEntry> questStatuses;
	private final List<Integer> wishList;
	private ShopPlayerContinuation returnContext;

	private ShopCharacter() {
		cashShopBalance = new AtomicInteger[4];
		skills = new HashMap<>();
		cooldowns = new HashMap<>();
		questStatuses = new HashMap<>();
		wishList = new ArrayList<>(10);

		itemExpireTask = new ItemExpireTask() {
			@Override
			protected void onExpire(long uniqueId) {
				InventorySlot item = shopInventory.getByUniqueId(uniqueId);
				if (item != null && expireItem(item)) {
					shopInventory.removeByUniqueId(uniqueId);
				}
			}
		};
	}

	@Override
	public ShopClient getClient() {
		return client;
	}

	@Override
	public ShopBuddyList getBuddyList() {
		return buddies;
	}

	public int getPartyId() {
		return partyId;
	}

	public int getGuildId() {
		return guildId;
	}

	public short getMaxCharacters() {
		return maxCharacters;
	}

	public short increaseMaxCharacters() {
		return ++maxCharacters;
	}

	public int getBirthday() {
		return birthday;
	}

	@Override
	public int getMesos() {
		return mesos;
	}

	private boolean expireItem(InventorySlot item) {
		if (item.getType() == InventorySlot.ItemType.PET) {
			return false;
		} else {
			getClient().getSession().send(CashShopPackets.writeItemExpired(item.getUniqueId()));
			return true;
		}
	}

	@Override
	public void checkForExpiredItems() {
		long now = System.currentTimeMillis();
		for (Iterator<InventorySlot> iter = getCashShopInventory().getAllValues().iterator(); iter.hasNext(); ) {
			InventorySlot item = iter.next();
			if (item.getExpiration() != 0) {
				if (now < item.getExpiration()) {
					itemExpireTask.addExpire(item.getExpiration(), item.getUniqueId());
				} else if (expireItem(item)) {
					iter.remove();
				}
			}
		}
	}

	public boolean gainMesos(int gain) {
		long newValue = (long) mesos + gain;
		if (newValue <= Integer.MAX_VALUE && newValue >= 0) {
			mesos = (int) newValue;
			return true;
		}
		return false;
	}

	public int getCashShopCurrency(int type) {
		return cashShopBalance[type - 1].get();
	}

	public void setCashShopCurrency(int type, int newVal) {
		cashShopBalance[type - 1].set(newVal);
		getClient().getSession().send(CashShopPackets.writeCashShopCurrencyBalance(this));
	}

	public boolean gainCashShopCurrency(int type, int gain) {
		long newValue = (long) getCashShopCurrency(type) + gain;
		if (newValue <= Integer.MAX_VALUE && newValue >= 0) {
			setCashShopCurrency(type, (int) newValue);
			return true;
		}
		return false;
	}

	public CashShopStaging getCashShopInventory() {
		return shopInventory;
	}

	@Override
	public Map<Integer, SkillEntry> getSkillEntries() {
		return Collections.unmodifiableMap(skills);
	}

	@Override
	public Map<Integer, Cooldown> getCooldowns() {
		return Collections.unmodifiableMap(cooldowns);
	}

	@Override
	public Map<Short, QuestEntry> getAllQuests() {
		return Collections.unmodifiableMap(questStatuses);
	}

	public List<Integer> getWishListSerialNumbers() {
		return Collections.unmodifiableList(wishList);
	}

	public void setWishList(List<Integer> newList) {
		wishList.clear();
		wishList.addAll(newList);
	}

	private void removeCooldown(int skill) {
		cooldowns.remove(Integer.valueOf(skill)).cancel();
	}

	private void addCooldown(final int skill, short time) {
		cooldowns.put(Integer.valueOf(skill), new Cooldown(time * 1000, () ->
			removeCooldown(skill)));
	}

	public void setReturnContext(ShopPlayerContinuation context) {
		returnContext = context;
	}

	public ShopPlayerContinuation getReturnContext() {
		returnContext.compactForReturn();
		return returnContext;
	}

	private void prepareExitServer() {
		itemExpireTask.cancel();
		for (Cooldown cooling : cooldowns.values())
			cooling.cancel();

		saveCharacter();
	}

	public void prepareChannelChange() {
		if (partyId != 0) {
			ShopServer.getInstance().getCrossServerInterface().sendPartyMemberLogOffNotifications(this, false);
		}
		if (guildId != 0) {
			ShopServer.getInstance().getCrossServerInterface().sendGuildMemberLogOffNotifications(this, false);
		}
		prepareExitServer();
	}

	public void prepareLogOff() {
		ShopServer.getInstance().getCrossServerInterface().sendBuddyLogOffNotifications(this);
		if (partyId != 0) {
			ShopServer.getInstance().getCrossServerInterface().sendPartyMemberLogOffNotifications(this, true);
		}
		if (guildId != 0) {
			ShopServer.getInstance().getCrossServerInterface().sendGuildMemberLogOffNotifications(this, true);
		}
		prepareExitServer();
	}

	public void saveCharacter() {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			int prevTransactionIsolation = Connection.TRANSACTION_REPEATABLE_READ;
			boolean prevAutoCommit = true;
			try {
				prevTransactionIsolation = con.getTransactionIsolation();
				prevAutoCommit = con.getAutoCommit();
				con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
				con.setAutoCommit(false);
				updateDbAccount(con);
				updateDbStats(con);
				updateDbInventory(con);
				updateDbCooldowns(con);
				updateDbWishList(con);
				con.commit();
			} catch (Throwable ex) {
				LOG.log(Level.WARNING, "Could not save character " + getDataId() + ". Rolling back all changes...", ex);
				try {
					con.rollback();
				} catch (SQLException ex2) {
					LOG.log(Level.WARNING, "Error rolling back character.", ex2);
				}
			} finally {
				try {
					con.setAutoCommit(prevAutoCommit);
					con.setTransactionIsolation(prevTransactionIsolation);
				} catch (SQLException ex) {
					LOG.log(Level.WARNING, "Could not reset Connection config after saving character " + getDataId(), ex);
				}
			}
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not save character " + getDataId() + ". Rolling back all changes...", ex);
		}
	}

	private void updateDbAccount(Connection con) throws SQLException {
		try {
			AccountDAO.updateMaxCharacters(con, client.getAccountId(), maxCharacters);
			AccountDAO.upsertCashBalance(con, client.getAccountId(),
					getCashShopCurrency(PAYPAL_NX),
					getCashShopCurrency(MAPLE_POINTS),
					getCashShopCurrency(GAME_CARD_NX));
			con.commit();
		} catch (Exception e) {
			throw new SQLException("Failed to save account-info of character " + name, e);
		}
	}

	private void updateDbStats(Connection con) throws SQLException {
		try {
			int updateRows = CharacterDAO.updateMesosAndSlots(con, getDataId(), mesos,
					getInventory(InventoryType.EQUIP).getMaxSlots(),
					getInventory(InventoryType.USE).getMaxSlots(),
					getInventory(InventoryType.SETUP).getMaxSlots(),
					getInventory(InventoryType.ETC).getMaxSlots(),
					getInventory(InventoryType.CASH).getMaxSlots());
			if (updateRows < 1) {
				LOG.log(Level.WARNING, "Updating a deleted character with name {0} of account {1}.",
					new Object[]{name, client.getAccountId()});
			}
		} catch (Exception e) {
			throw new SQLException("Failed to save stats of character " + name, e);
		}
	}

	private void updateDbInventory(Connection con) throws SQLException {
		CharacterDAO.deleteInventoryItems(con, getDataId(), client.getAccountId(),
				InventoryType.CASH.byteValue(), InventoryType.CASH_SHOP.byteValue());

		EnumMap<InventoryType, IInventory> union = new EnumMap<>(getInventories());
		union.put(InventoryType.CASH_SHOP, shopInventory);
		commitInventory(con, union);
	}

	private void updateDbCooldowns(Connection con) throws SQLException {
		Map<Integer, Short> cooldownMap = new HashMap<>();
		for (Map.Entry<Integer, Cooldown> cooling : cooldowns.entrySet()) {
			cooldownMap.put(cooling.getKey(), cooling.getValue().getSecondsRemaining());
		}
		CharacterDAO.replaceCooldowns(con, getDataId(), cooldownMap);
	}

	private void updateDbWishList(Connection con) throws SQLException {
		CharacterDAO.replaceWishlist(con, getDataId(), wishList);
	}

	@Override
	protected void loadInventory(Connection con, ResultSet rs, Map<InventoryType, ? extends IInventory> inventories) throws SQLException {
		super.loadInventory(con, rs, inventories);
		shopInventory.loadPurchaseProperties(getClient().getAccountId());
	}

	public static ShopCharacter loadPlayer(ShopClient c, int id) {
		try (Connection con = DatabaseManager.getConnection(DatabaseType.STATE)) {
			ShopCharacter p;
			int accountid;
			short maxBuddies;
			try (PreparedStatement ps = con.prepareStatement("SELECT `c`.*,`a`.`name`,`a`.`characters`,`a`.`birthday`,"
					+ "COALESCE(`b`.`paypalnx`,0) AS paypalnx,COALESCE(`b`.`maplepoints`,0) AS maplepoints, COALESCE(`b`.`gamecardnx`) as gamecardnx "
					+ "FROM `characters` `c` "
					+ "LEFT JOIN `accounts` `a` ON `c`.`accountid` = `a`.`id` "
					+ "LEFT JOIN `cashshopbalance` `b` ON `b`.`accountid` = `a`.`id`"
					+ "WHERE `c`.`id` = ?")) {
				ps.setInt(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						LOG.log(Level.WARNING, "Client requested to load a nonexistent character w/ id {0} (account {1}).",
								new Object[]{id, c.getAccountId()});
						return null;
					}
					accountid = rs.getInt(1);
					c.setAccountId(accountid); //we aren't aware of our accountid yet
					byte world = rs.getByte(2);
					c.setWorld(world); //we aren't aware of our world yet
					p = new ShopCharacter();
					p.client = c;
					p.loadPlayerStats(rs, id);
					p.mesos = rs.getInt(26);
					maxBuddies = rs.getShort(32);
					c.setAccountName(rs.getString(42));
					p.maxCharacters = rs.getByte(43);
					p.birthday = rs.getInt(44);
					p.cashShopBalance[PAYPAL_NX - 1] = new AtomicInteger(rs.getInt(45));
					p.cashShopBalance[MAPLE_POINTS - 1] = new AtomicInteger(rs.getInt(46));
					p.cashShopBalance[GAME_CARD_NX - 1] = new AtomicInteger(rs.getInt(47));
				}
			}

			p.shopInventory = new CashShopStaging();
			EnumMap<InventoryType, IInventory> invUnion = new EnumMap<>(p.getInventories());
			invUnion.put(InventoryType.CASH_SHOP, p.shopInventory);
			try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `inventoryitems` WHERE "
					+ "`characterid` = ? AND `inventorytype` <= " + InventoryType.CASH.byteValue()
					+ " OR `accountid` = ? AND `inventorytype` = " + InventoryType.CASH_SHOP.byteValue())) {
				ps.setInt(1, id);
				ps.setInt(2, accountid);
				try (ResultSet rs = ps.executeQuery()) {
					p.loadInventory(con, rs, invUnion);
				}
			}

			try (PreparedStatement ps = con.prepareStatement("SELECT `skillid`,`level`,`mastery` "
					+ "FROM `skills` WHERE `characterid` = ?")) {
				ps.setInt(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						p.skills.put(Integer.valueOf(rs.getInt(1)), new SkillEntry(rs.getByte(2), rs.getByte(3)));
					}
				}
			}

			try (PreparedStatement ps = con.prepareStatement("SELECT `skillid`,`remaining` "
					+ "FROM `cooldowns` WHERE `characterid` = ?")) {
				ps.setInt(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						p.addCooldown(rs.getInt(1), rs.getShort(2));
					}
				}
			}

			List<BuddyListEntry> buddies = new ArrayList<>();
			try (PreparedStatement ps = con.prepareStatement("SELECT `e`.`buddy` AS `id`,"
					+ "IF(ISNULL(`c`.`name`),`e`.`buddyname`,`c`.`name`) AS `name`,`e`.`status` "
					+ "FROM `buddyentries` `e` LEFT JOIN `characters` `c` ON `c`.`id` = `e`.`buddy` "
					+ "WHERE `owner` = ?")) {
				ps.setInt(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						byte status = rs.getByte(3);
						if (status != BuddyListEntry.STATUS_INVITED) {
							buddies.add(new BuddyListEntry(rs.getInt(1), rs.getString(2), status));
						}
					}
				}
			}
			p.buddies = new ShopBuddyList(maxBuddies, buddies);

			int partyId = CharacterDAO.loadPartyId(con, id);
			if (partyId != -1) {
				p.partyId = partyId;
			}

			int guildId = CharacterDAO.loadGuildId(con, id);
			if (guildId != -1) {
				p.guildId = guildId;
			}

			try (PreparedStatement ps = con.prepareStatement("SELECT `id`,`questid`,`state`,`completed` "
					+ "FROM `queststatuses` WHERE `characterid` = ?")) {
				ps.setInt(1, id);
				try (ResultSet rs = ps.executeQuery();
						PreparedStatement mps = con.prepareStatement("SELECT `mobid`,`count` "
								+ "FROM `questmobprogress` WHERE `queststatusid` = ?")) {
					while (rs.next()) {
						int questEntryId = rs.getInt(1);
						short questId = rs.getShort(2);
						Map<Integer, AtomicInteger> mobProgress = new LinkedHashMap<>();
						mps.setInt(1, questEntryId);
						try (ResultSet mrs = mps.executeQuery()) {
							while (mrs.next()) {
								mobProgress.put(Integer.valueOf(mrs.getInt(1)), new AtomicInteger(mrs.getShort(2)));
							}
						}
						QuestEntry status = new QuestEntry(rs.getByte(3), mobProgress);
						status.setCompletionTime(rs.getLong(4));
						p.questStatuses.put(Short.valueOf(questId), status);
					}
				}
			}

			try (PreparedStatement ps = con.prepareStatement("SELECT `sn` FROM `wishlists` WHERE `characterid` = ?")) {
				ps.setInt(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						p.wishList.add(Integer.valueOf(rs.getInt(1)));
					}
				}
			}
			return p;
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not load character " + id + " from database", ex);
			return null;
		}
	}

	public static int getAccountIdFromName(String name) {
		return CharacterDAO.getAccountIdFromName(name);
	}
}
