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

package argonms.login.net.external;

import argonms.common.net.external.ClientPacketProcessor;
import argonms.common.net.external.ClientRecvOps;
import argonms.common.net.external.PacketDispatchTable;
import argonms.common.util.input.LittleEndianReader;
import argonms.login.net.external.handler.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientLoginPacketProcessor extends ClientPacketProcessor<LoginClient> {
	private static final Logger LOG = Logger.getLogger(ClientPacketProcessor.class.getName());

	private static final PacketDispatchTable<LoginClient> TABLE = new PacketDispatchTable<LoginClient>()
		.register(ClientRecvOps.LOGIN_PASSWORD,      AuthHandler::handleLogin)
		.register(ClientRecvOps.SERVERLIST_REREQUEST, WorldlistHandler::handleWorldListRequest)
		.register(ClientRecvOps.CHARLIST_REQ,         WorldlistHandler::handleCharlist)
		.register(ClientRecvOps.REQ_SERVERLOAD,       WorldlistHandler::sendServerStatus)
		.register(ClientRecvOps.SET_GENDER,           AuthHandler::handleGender)
		.register(ClientRecvOps.PIN_OPERATION,        AuthHandler::handlePin)
		.register(ClientRecvOps.REGISTER_PIN,         AuthHandler::handlePinRegister)
		.register(ClientRecvOps.SERVERLIST_REQUEST,   WorldlistHandler::handleWorldListRequest)
		.noOp(ClientRecvOps.EXIT_CHARLIST)            // cancel-loading no-op
		.register(ClientRecvOps.VIEW_ALL_CHARS,       WorldlistHandler::handleViewAllChars)
		.register(ClientRecvOps.PICK_ALL_CHAR,        WorldlistHandler::handlePickFromAllChars)
		.noOp(ClientRecvOps.ENTER_EXIT_VIEW_ALL)      // cancel-loading no-op
		.register(ClientRecvOps.CHAR_SELECT,          WorldlistHandler::handlePickFromWorldCharlist)
		.register(ClientRecvOps.CHECK_CHAR_NAME,      WorldlistHandler::handleNameCheck)
		.register(ClientRecvOps.CREATE_CHAR,          WorldlistHandler::handleCreateCharacter)
		.register(ClientRecvOps.DELETE_CHAR,          WorldlistHandler::handleDeleteChar)
		.register(ClientRecvOps.PONG,                 (r, lc) -> lc.getSession().receivedPong())
		.register(ClientRecvOps.CLIENT_ERROR,         (r, lc) -> lc.clientError(r.readLengthPrefixedString()))
		.noOp(ClientRecvOps.AES_IV_UPDATE_REQUEST)
		.register(ClientRecvOps.RELOG,                WorldlistHandler::backToLogin)
		.noOp(ClientRecvOps.MESSENGER_ACT)            // clean-up already handled server-side
		.noOp(ClientRecvOps.PLAYER_UPDATE);

	@Override
	public void process(LittleEndianReader reader, LoginClient lc) {
		if (!TABLE.dispatch(reader, lc)) {
			LOG.log(Level.FINE, "Received unhandled client packet {0} bytes long:\n{1}",
					new Object[]{reader.available() + 2, reader});
		}
	}
}
