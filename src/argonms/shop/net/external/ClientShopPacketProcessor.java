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

package argonms.shop.net.external;

import argonms.common.net.external.ClientPacketProcessor;
import argonms.common.net.external.ClientRecvOps;
import argonms.common.net.external.PacketDispatchTable;
import argonms.common.util.input.LittleEndianReader;
import argonms.shop.net.external.handler.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientShopPacketProcessor extends ClientPacketProcessor<ShopClient> {
	private static final Logger LOG = Logger.getLogger(ClientPacketProcessor.class.getName());

	private static final PacketDispatchTable<ShopClient> TABLE = new PacketDispatchTable<ShopClient>()
		.register(ClientRecvOps.PLAYER_CONNECTED, EnterShopHandler::handlePlayerConnection)
		.register(ClientRecvOps.PONG,             (r, sc) -> sc.getSession().receivedPong())
		.register(ClientRecvOps.CLIENT_ERROR,     (r, sc) -> sc.clientError(r.readLengthPrefixedString()))
		.noOp(ClientRecvOps.AES_IV_UPDATE_REQUEST)
		.register(ClientRecvOps.CHANGE_MAP,       CashShopHandler::handleReturnToChannel)
		.noOp(ClientRecvOps.MOVE_PET)
		.register(ClientRecvOps.CHECK_CASH,       CashShopHandler::handleCheckCash)
		.register(ClientRecvOps.BUY_CS_ITEM,      CashShopHandler::handleAction)
		.register(ClientRecvOps.COUPON_CODE,      CashShopHandler::handleRedeemCoupon)
		.register(ClientRecvOps.PLAYER_UPDATE,    (r, sc) -> sc.getPlayer().saveCharacter());

	@Override
	public void process(LittleEndianReader reader, ShopClient sc) {
		if (!TABLE.dispatch(reader, sc)) {
			LOG.log(Level.FINE, "Received unhandled client packet {0} bytes long:\n{1}",
					new Object[]{reader.available() + 2, reader});
		}
	}
}
