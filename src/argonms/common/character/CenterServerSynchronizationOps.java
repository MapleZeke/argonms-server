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

package argonms.common.character;

/**
 *
 * @author GoldenKevin
 */
public class CenterServerSynchronizationOps {
	public static final byte PARTY_CREATE = 0;
	public static final byte PARTY_DISBAND = 1;
	public static final byte PARTY_ADD_PLAYER = 2;
	public static final byte PARTY_REMOVE_PLAYER = 3;
	public static final byte PARTY_CHANGE_LEADER = 4;
	public static final byte PARTY_JOIN_ERROR = 5;
	public static final byte PARTY_FETCH_LIST = 6;
	public static final byte PARTY_MEMBER_CONNECTED = 7;
	public static final byte PARTY_MEMBER_DISCONNECTED = 8;
	public static final byte PARTY_MEMBER_STAT_UPDATED = 9;
	public static final byte GUILD_CREATE = 10;
	public static final byte GUILD_CONTRACT = 11;
	public static final byte GUILD_FETCH_LIST = 12;
	public static final byte GUILD_MEMBER_CONNECTED = 13;
	public static final byte GUILD_MEMBER_DISCONNECTED = 14;
	public static final byte GUILD_MEMBER_STAT_UPDATED = 15;
	public static final byte GUILD_ADD_PLAYER = 16;
	public static final byte GUILD_JOIN_ERROR = 17;
	public static final byte GUILD_REMOVE_PLAYER = 18;
	public static final byte GUILD_EXPAND = 19;
	public static final byte GUILD_EMBLEM_UPDATE = 20;
	public static final byte GUILD_TITLES_UPDATE = 21;
	public static final byte GUILD_MEMBER_RANK_UPDATE = 22;
	public static final byte GUILD_NOTICE_UPDATE = 23;
	public static final byte GUILD_CONTRACT_VOTE = 24;
	public static final byte GUILD_CREATED = 25;
	public static final byte GUILD_DISBAND = 26;
	public static final byte CHATROOM_CREATE = 27;
	public static final byte CHATROOM_ADD_PLAYER = 28;
	public static final byte CHATROOM_REMOVE_PLAYER = 29;
	public static final byte CHATROOM_UPDATE_AVATAR_CHANNEL = 30;
	public static final byte CHATROOM_UPDATE_AVATAR_LOOK = 31;
	public static final byte CHATROOM_CREATED = 32;
	public static final byte CHATROOM_ROOM_CHANGED = 33;
	public static final byte CHATROOM_SLOT_CHANGED = 34;
}
