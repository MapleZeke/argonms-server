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

package argonms.shop.coupon;

import argonms.common.util.DatabaseManager;
import argonms.common.util.dao.CouponDAO;
import argonms.common.util.dao.DataAccessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CouponFactory {
	private static final Logger LOG = Logger.getLogger(CouponFactory.class.getName());

	private static final CouponFactory INSTANCE = new CouponFactory();

	private final ConcurrentMap<String, Coupon> loadedCoupons;

	private CouponFactory() {
		loadedCoupons = new ConcurrentHashMap<>();
	}

	public Coupon getCoupon(String code) {
		Coupon c = new Coupon(code);
		synchronized (c) {
			Coupon existing = loadedCoupons.putIfAbsent(code, c);
			if (existing != null) {
				return existing;
			}

			try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
				CouponDAO.CouponRecord record = CouponDAO.loadCoupon(con, code);
				if (record != null) {
					c.setMaplePoints(record.maplePoints());
					c.setMesos(record.mesos());
					c.setRemainingUses(record.remainingUses());
					c.setExpireDate(record.expireDate());

					List<Integer> users = CouponDAO.loadCouponUsers(con, code);
					for (Integer accountId : users) {
						c.addUser(accountId);
					}
					List<Integer> items = CouponDAO.loadCouponItems(con, code);
					for (Integer sn : items) {
						c.addItem(sn);
					}

					c.onInitialized();
					return c;
				}
			} catch (SQLException | DataAccessException ex) {
				LOG.log(Level.WARNING, "Could not fetch coupon " + code, ex);
			}
		}
		loadedCoupons.remove(code);
		return null;
	}

	public void commitCoupon(Coupon c) {
		try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
			synchronized (c) {
				CouponDAO.upsertCoupon(con, c.getCode(), c.getMaplePointsReward(),
						c.getMesosReward(), c.getRemainingUses(), c.getExpireDate());

				if (c.shouldUpdateUsers()) {
					CouponDAO.replaceCouponUsers(con, c.getCode(), c.getUsers());
				}

				if (c.shouldUpdateItems()) {
					CouponDAO.replaceCouponItems(con, c.getCode(), c.getItems());
				}
			}
		} catch (SQLException | DataAccessException ex) {
			LOG.log(Level.WARNING, "Could not commit coupon " + c.getCode(), ex);
		}
	}

	public static CouponFactory getInstance() {
		return INSTANCE;
	}
}
