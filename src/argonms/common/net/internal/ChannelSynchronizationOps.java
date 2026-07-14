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
 *
 * @author GoldenKevin
 */
public class ChannelSynchronizationOps {
	public static final byte INBOUND_PLAYER = 1;
	public static final byte INBOUND_PLAYER_ACCEPTED = 2;
	public static final byte PLAYER_SEARCH = 3;
	public static final byte PLAYER_SEARCH_RESPONSE = 4;
	public static final byte MULTI_CHAT = 5;
	public static final byte WHISPER_CHAT = 6;
	public static final byte WHISPER_RESPONSE = 7;
	public static final byte SPOUSE_CHAT = 8;
	public static final byte BUDDY_INVITE = 9;
	public static final byte BUDDY_INVITE_RESPONSE = 10;
	public static final byte BUDDY_INVITE_RETRACTION = 11;
	public static final byte BUDDY_ONLINE = 12;
	public static final byte BUDDY_ACCEPTED = 13;
	public static final byte BUDDY_ONLINE_RESPONSE = 14;
	public static final byte BUDDY_OFFLINE = 15;
	public static final byte BUDDY_DELETED = 16;
	public static final byte CHATROOM_INVITE = 17;
	public static final byte CHATROOM_INVITE_RESPONSE = 18;
	public static final byte CHATROOM_DECLINE = 19;
	public static final byte CHATROOM_TEXT = 20;
	public static final byte CROSS_CHANNEL_COMMAND_CHARACTER_MANIPULATION = 21;
	public static final byte CROSS_CHANNEL_COMMAND_CHARACTER_ACCESS = 22;
	public static final byte CROSS_CHANNEL_COMMAND_CHARACTER_ACCESS_RESPONSE = 23;
	public static final byte SYNCHRONIZED_NOTICE = 24;
	public static final byte SYNCHRONIZED_SHUTDOWN = 25;
	public static final byte SYNCHRONIZED_RATE_CHANGE = 26;
	public static final byte WHO_COMMAND = 27;
	public static final byte WHO_COMMAND_RESPONSE = 28;

	public static final byte SCAN_PLAYER_CHANNEL_NO_MATCH = 0;
	public static final byte SCAN_PLAYER_CHANNEL_HIDDEN = 1;
	public static final byte SCAN_PLAYER_CHANNEL_FOUND = 2;

	public static final byte CHANNEL_OFFLINE = -1;
	public static final byte CHANNEL_CASH_SHOP = 0;
}
