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

import argonms.common.character.Player;
import argonms.common.character.inventory.IInventory;
import argonms.common.character.inventory.Inventory;
import argonms.common.character.inventory.InventorySlot;
import argonms.common.character.inventory.InventoryTools;
import argonms.common.character.inventory.Pet;
import argonms.common.loading.item.ItemDataLoader;
import argonms.common.util.DatabaseManager;
import argonms.common.util.collections.Pair;
import argonms.common.util.dao.CashShopStagingDAO;
import argonms.shop.ShopServer;
import argonms.shop.loading.cashshop.CashShopDataLoader;
import argonms.shop.loading.cashshop.Commodity;
import argonms.shop.net.external.CashShopPackets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CashShopStaging implements IInventory {
	private static final Logger LOG = Logger.getLogger(ShopCharacter.class.getName());

	private static final short MAX_SLOTS = 0xFF;

	public static class CashItemGiftNotification {
		private final long uniqueId;
		private final int itemId;
		private final String sender;
		private final String message;

		public CashItemGiftNotification(long uniqueId, int dataId, String sender, String message) {
			this.uniqueId = uniqueId;
			this.itemId = dataId;
			this.sender = sender;
			this.message = message;
		}

		public CashItemGiftNotification(CashPurchaseProperties props, InventorySlot slot, String message) {
			uniqueId = slot.getUniqueId();
			itemId = slot.getDataId();
			sender = props.getGifterCharacterName();
			this.message = message;
		}

		public long getUniqueId() {
			return uniqueId;
		}

		public int getItemId() {
			return itemId;
		}

		public String getSender() {
			return sender;
		}

		public String getMessage() {
			return message;
		}
	}

	public static class CashPurchaseProperties {
		private int purchaserAccountId;
		private String gifterName;
		private int serialNumber;

		private CashPurchaseProperties() {
			
		}

		public CashPurchaseProperties(int account, String character, int sn) {
			purchaserAccountId = account;
			gifterName = character;
			serialNumber = sn;
		}

		public int getPurchaserAccountId() {
			return purchaserAccountId;
		}

		public String getGifterCharacterName() {
			return gifterName;
		}

		public int getSerialNumber() {
			return serialNumber;
		}

		/* package-private */ static CashPurchaseProperties loadFromDatabase(ResultSet rs, int itemId, int defaultAccount) throws SQLException {
			CashPurchaseProperties props = new CashPurchaseProperties();
			props.purchaserAccountId = rs.getInt(1);
		if (rs.wasNull()) {
			props.purchaserAccountId = defaultAccount;
		}
			props.gifterName = rs.getString(2);
			props.serialNumber = rs.getInt(3);
			return props;
		}

		public static CashPurchaseProperties loadFromDatabase(long uniqueId, int itemId, int defaultAccount) {
			try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE);
					PreparedStatement ps = con.prepareStatement("SELECT `purchaseracctid`,`gifterchrname`,`serialnumber` FROM `cashshoppurchases` WHERE `uniqueid` = ?")) {
				ps.setLong(1, uniqueId);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						return loadFromDatabase(rs, itemId, defaultAccount);
					}
				}
			} catch (SQLException ex) {
				LOG.log(Level.WARNING, "Could not load cash shop purchase properties from database", ex);
			}
			return null;
		}

		/* package-private */ static CashPurchaseProperties loadFromRecord(CashShopStagingDAO.PurchasePropertyRecord rec, int itemId, int defaultAccount) {
			CashPurchaseProperties props = new CashPurchaseProperties();
			props.purchaserAccountId = rec.purchaserAccountId() == 0 ? defaultAccount : rec.purchaserAccountId();
			props.gifterName = rec.gifterName();
			props.serialNumber = rec.serialNumber();
			return props;
		}
	}

	public interface ItemManipulator {
		public boolean manipulate(InventorySlot item, int serialNumber, Commodity c);
	}

	private final ReadWriteLock locks;
	private final Map<Long, InventorySlot> slots;
	private final Map<Long, CashPurchaseProperties> purchaseProperties;
	private final List<CashItemGiftNotification> giftNotifications;

	public CashShopStaging() {
		//forgo to the overhead of ConcurrentHashMap. with no scripts and
		//commands, we are guaranteed to not do much concurrency in cash shop
		locks = new ReentrantReadWriteLock();
		slots = new LinkedHashMap<>();
		purchaseProperties = new HashMap<>();
		giftNotifications = new ArrayList<>();
	}

	public void loadPurchaseProperties(int accountId) {
		lockWrite();
		try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
			List<CashShopStagingDAO.PurchasePropertyRecord> records =
					CashShopStagingDAO.loadPurchaseProperties(con, slots.keySet());
			for (CashShopStagingDAO.PurchasePropertyRecord rec : records) {
				Long uniqueId = Long.valueOf(rec.uniqueId());
				InventorySlot item = slots.get(uniqueId);
				if (item != null) {
					purchaseProperties.put(uniqueId,
							CashPurchaseProperties.loadFromRecord(rec, item.getDataId(), accountId));
				}
			}

			List<CashShopStagingDAO.GiftNotificationRecord> gifts =
					CashShopStagingDAO.loadAndDeleteGiftNotes(con, accountId);
			for (CashShopStagingDAO.GiftNotificationRecord gift : gifts) {
				Long oUid = Long.valueOf(gift.uniqueId());
				InventorySlot item = slots.get(oUid);
				CashPurchaseProperties props = purchaseProperties.get(oUid);
				if (item == null || props == null) {
					LOG.log(Level.FINE, "Dropping gift with unique ID {0} for {1}. Missing item data or not owned by recipient.",
							new Object[]{oUid, Integer.valueOf(accountId)});
					continue;
				}
				giftNotifications.add(new CashItemGiftNotification(props, item, gift.message()));
			}
		} catch (Exception ex) {
			LOG.log(Level.WARNING, "Could not load cash shop purchase properties from database", ex);
		} finally {
			unlockWrite();
		}
	}

	public void lockRead() {
		locks.readLock().lock();
	}

	public void unlockRead() {
		locks.readLock().unlock();
	}

	public void lockWrite() {
		locks.writeLock().lock();
	}

	public void unlockWrite() {
		locks.writeLock().unlock();
	}

	//needs to be supported for loading CashShopStaging inventory
	//we better hope that retrieving from database is in slot position order
	@Override
	public void put(short position, InventorySlot item) {
		lockWrite();
		try {
			//if (position != slots.size())
				//throw new UnsupportedOperationException("CashShopStaging has no concept of slot positions - can only append items when (position == getAllValues.size() == getFreeSlots(1).get(1).shortValue())");
			append(item, null);
		} finally {
			unlockWrite();
		}
	}

	//needs to be supported for commiting CashShopStaging inventory
	@Override
	public Map<Short, InventorySlot> getAll() {
		//throw new UnsupportedOperationException("CashShopStaging has no concept of slot positions");
		lockRead();
		try {
			Map<Short, InventorySlot> slotBasedMap = new LinkedHashMap<>();
			short slot = 1;
			for (InventorySlot item : getAllValues()) {
				slotBasedMap.put(Short.valueOf(slot), item);
				slot++;
			}
			return slotBasedMap;
		} finally {
			unlockRead();
		}
	}

	@Override
	public short getMaxSlots() {
		return MAX_SLOTS; //maybe? should test this
	}

	/**
	 * This CashShopStaging must be read locked while the returned collection is in scope.
	 * @return 
	 */
	public Collection<InventorySlot> getAllValues() {
		return slots.values();
	}

	public CashPurchaseProperties getPurchaseProperties(long uniqueId) {
		return purchaseProperties.get(Long.valueOf(uniqueId));
	}

	/**
	 * This CashShopStaging must be read locked while the returned collection is in scope.
	 * @return 
	 */
	public Collection<CashItemGiftNotification> getGiftedItems() {
		return giftNotifications;
	}

	public void newGiftedItem(CashItemGiftNotification gift) {
		lockWrite();
		try {
			giftNotifications.clear();
			giftNotifications.add(gift);
		} finally {
			unlockWrite();
		}
	}

	public InventorySlot getByUniqueId(long uniqueId) {
		lockRead();
		try {
			return slots.get(Long.valueOf(uniqueId));
		} finally {
			unlockRead();
		}
	}

	public void removeByUniqueId(long uniqueId) {
		lockWrite();
		try {
			Long oUid = Long.valueOf(uniqueId);
			slots.remove(oUid);
			purchaseProperties.remove(oUid);
		} finally {
			unlockWrite();
		}
	}

	public void append(InventorySlot item, CashPurchaseProperties props) {
		lockWrite();
		try {
			Long oUid = Long.valueOf(item.getUniqueId());
			slots.put(oUid, item);
			if (props != null) {
				purchaseProperties.put(oUid, props);
			}
		} finally {
			unlockWrite();
		}
	}

	public boolean isFull() {
		lockRead();
		try {
			return slots.size() >= getMaxSlots();
		} finally {
			unlockRead();
		}
	}

	public boolean canFit(int add) {
		lockRead();
		try {
			return slots.size() + add <= getMaxSlots();
		} finally {
			unlockRead();
		}
	}

	public static void attachCashPurchaseProperties(long uniqueId, CashPurchaseProperties props) {
		CashShopStagingDAO.attachPurchaseProperties(uniqueId, props.purchaserAccountId,
				props.gifterName, props.getSerialNumber());
	}

	public static Pair<InventorySlot, CashPurchaseProperties> createItem(Commodity c, int serialNumber, int senderAcctId, String senderName) {
		InventorySlot item = InventoryTools.makeItemWithId(c.itemDataId);
		if (!InventoryTools.isPet(c.itemDataId)) {
			item.setExpiration(System.currentTimeMillis() + (c.period * 1000L * 60 * 60 * 24));
		} else {
			item.setExpiration(System.currentTimeMillis() + (ItemDataLoader.getInstance().getPetPeriod(c.itemDataId) * 1000L * 60 * 60 * 24));
		}
		if (c.quantity != 1) {
			item.setQuantity(c.quantity);
		}

		CashShopStaging.CashPurchaseProperties props = new CashShopStaging.CashPurchaseProperties(senderAcctId, senderName, serialNumber);
		CashShopStaging.attachCashPurchaseProperties(item.getUniqueId(), props);

		return new Pair<>(item, props);
	}

	public static boolean giveGift(int senderAcctId, String senderName, int recipientAcctId, int[] serialNumbers, String message, ItemManipulator itemManipulator) {
		try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
			try {
				ShopCharacter recipient = null;
				List<Integer> charIds = CashShopStagingDAO.getCharacterIdsForAccount(con, recipientAcctId);
				for (Integer charId : charIds) {
					recipient = ShopServer.getInstance().getPlayerById(charId.intValue());
					if (recipient != null) {
						break;
					}
				}
				if (recipient != null) {
					if (!recipient.getCashShopInventory().canFit(serialNumbers.length)) {
						return false;
					}

					CashShopDataLoader csdl = CashShopDataLoader.getInstance();
					for (int serialNumber : serialNumbers) {
						Commodity c = csdl.getCommodity(serialNumber);
						Pair<InventorySlot, CashPurchaseProperties> item = createItem(c, serialNumber, senderAcctId, senderName);
						if (itemManipulator != null && !itemManipulator.manipulate(item.left, serialNumber, c)) {
							continue;
						}

						recipient.getCashShopInventory().append(item.left, item.right);
						recipient.onExpirableItemAdded(item.left);
						recipient.getCashShopInventory().newGiftedItem(new CashItemGiftNotification(item.left.getUniqueId(), item.left.getDataId(), senderName, message));
						recipient.getClient().getSession().send(CashShopPackets.writeCashItemStagingInventory(recipient));
						recipient.getClient().getSession().send(CashShopPackets.writeGiftedCashItems(recipient));
					}
					return true;
				}

				short position = CashShopStagingDAO.getNextPosition(con, recipientAcctId, Inventory.InventoryType.CASH_SHOP.byteValue());
				if (position - 1 + serialNumbers.length > MAX_SLOTS) {
					return false;
				}

				final Map<Short, InventorySlot> inv = new LinkedHashMap<>(serialNumbers.length);
				CashShopDataLoader csdl = CashShopDataLoader.getInstance();
				for (int serialNumber : serialNumbers) {
					Commodity c = csdl.getCommodity(serialNumber);
					final Pair<InventorySlot, CashPurchaseProperties> item = createItem(c, serialNumber, senderAcctId, senderName);
					if (itemManipulator != null && !itemManipulator.manipulate(item.left, serialNumber, c)) {
						continue;
					}

					inv.put(Short.valueOf(position), item.left);
					position++;
				}

				Player.commitInventory(recipientAcctId, recipientAcctId, new Pet[3], con, Collections.singletonMap(Inventory.InventoryType.CASH_SHOP, new IInventory() {
					@Override
					public void put(short position, InventorySlot item) {
						throw new UnsupportedOperationException("Player.commitInventory should not be mutating inventory.");
					}

					@Override
					public Map<Short, InventorySlot> getAll() {
						return inv;
					}

					@Override
					public short getMaxSlots() {
						throw new UnsupportedOperationException("Player.commitInventory should not need capacity.");
					}
				}));

				List<Long> uniqueIds = new ArrayList<>();
				for (InventorySlot item : inv.values()) {
					uniqueIds.add(Long.valueOf(item.getUniqueId()));
				}
				CashShopStagingDAO.insertGiftNotes(con, recipientAcctId, uniqueIds, message);
				return true;
			} catch (SQLException ex) {
				LOG.log(Level.WARNING, "Could not insert new cash item gift to database", ex);
				return false;
			}
		} catch (SQLException ex) {
			LOG.log(Level.WARNING, "Could not insert new cash item gift to database", ex);
			return false;
		}
	}

	public static int[] getBestItems() {
		return CashShopStagingDAO.getBestItems();
	}
}
