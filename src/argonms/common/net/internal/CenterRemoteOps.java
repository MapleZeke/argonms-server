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

package argonms.common.net.internal;

/**
 * Opcodes for packets sent from the center server and received on a remote
 * server.
 */
public final class CenterRemoteOps {
	public static final byte AUTH_RESPONSE = 0x00;
	public static final byte PING = 0x01;
	public static final byte PONG = 0x02;
	public static final byte GAME_CONNECTED = 0x03;
	public static final byte SHOP_CONNECTED = 0x04;
	public static final byte GAME_DISCONNECTED = 0x05;
	public static final byte SHOP_DISCONNECTED = 0x06;
	public static final byte CHANGE_POPULATION = 0x07;
	public static final byte CHANNEL_PORT_CHANGE = 0x08;
	public static final byte CROSS_CHANNEL_SYNCHRONIZATION = 0x09;
	public static final byte SHOP_CHANNEL_SHOP_SYNCHRONIZATION = 0x0A;
	public static final byte CENTER_SERVER_SYNCHRONIZATION = 0x0B;

	private CenterRemoteOps() {
		//uninstantiable...
	}
}
