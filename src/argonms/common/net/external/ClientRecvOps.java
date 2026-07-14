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

package argonms.common.net.external;

public final class ClientRecvOps {
	public static final short LOGIN_PASSWORD = 0x01;
	public static final short GUEST_LOGIN = 0x02;
	public static final short SERVERLIST_REREQUEST = 0x04;
	public static final short CHARLIST_REQ = 0x05;
	public static final short REQ_SERVERLOAD = 0x06;
	public static final short SET_GENDER = 0x08;
	public static final short PIN_OPERATION = 0x09;
	public static final short REGISTER_PIN = 0x0A;
	public static final short SERVERLIST_REQUEST = 0x0B;
	public static final short EXIT_CHARLIST = 0x0C;
	public static final short VIEW_ALL_CHARS = 0x0D;
	public static final short PICK_ALL_CHAR = 0x0E;
	public static final short ENTER_EXIT_VIEW_ALL = 0x0F;
	public static final short CHAR_NAME_CHANGE = 0x10;
	public static final short CHAR_TRANSFER = 0x12;
	public static final short CHAR_SELECT = 0x13;
	public static final short PLAYER_CONNECTED = 0x14;
	public static final short CHECK_CHAR_NAME = 0x15;
	public static final short CREATE_CHAR = 0x16;
	public static final short DELETE_CHAR = 0x17;
	public static final short PONG = 0x18;
	public static final short CLIENT_ERROR = 0x19;
	public static final short AES_IV_UPDATE_REQUEST = 0x1A;
	public static final short RELOG = 0x1C;
	public static final short CHANGE_MAP = 0x23;
	public static final short CHANGE_CHANNEL = 0x24;
	public static final short ENTER_CASH_SHOP = 0x25;
	public static final short MOVE_PLAYER = 0x26;
	public static final short CHAIR = 0x27;
	public static final short USE_CHAIR_ITEM = 0x28;
	public static final short MELEE_ATTACK = 0x29;
	public static final short RANGED_ATTACK = 0x2A;
	public static final short MAGIC_ATTACK = 0x2B;
	public static final short ENERGY_CHARGE_ATTACK = 0x2C;
	public static final short TAKE_DAMAGE = 0x2D;
	public static final short MAP_CHAT = 0x2E;
	public static final short CLOSE_CHALKBOARD = 0x2F;
	public static final short FACIAL_EXPRESSION = 0x30;
	public static final short USE_ITEMEFFECT = 0x31;
	public static final short NPC_TALK = 0x36;
	public static final short NPC_TALK_MORE = 0x38;
	public static final short NPC_SHOP = 0x39;
	public static final short NPC_STORAGE = 0x3A;
	public static final short USE_HIRED_MERCHANT = 0x3B;
	public static final short DUEY_ACTION = 0x3D;
	public static final short ITEM_SORT = 0x40;
	public static final short ITEM_MOVE = 0x42;
	public static final short USE_ITEM = 0x43;
	public static final short CANCEL_ITEM = 0x44;
	public static final short USE_SUMMON_BAG = 0x46;
	public static final short PET_FOOD = 0x47;
	public static final short USE_MOUNT_FOOD = 0x48;
	public static final short USE_CASH_ITEM = 0x49;
	public static final short USE_CATCH_ITEM = 0x4A;
	public static final short USE_SKILL_BOOK = 0x4B;
	public static final short USE_RETURN_SCROLL = 0x4E;
	public static final short USE_UPGRADE_SCROLL = 0x4F;
	public static final short DISTRIBUTE_AP = 0x50;
	public static final short HEAL_OVER_TIME = 0x51;
	public static final short DISTRIBUTE_SP = 0x52;
	public static final short USE_SKILL = 0x53;
	public static final short CANCEL_SKILL = 0x54;
	public static final short PREPARED_SKILL = 0x55;
	public static final short MESO_DROP = 0x56;
	public static final short GIVE_FAME = 0x57;
	public static final short OPEN_PERSONAL_INFO = 0x59;
	public static final short SPAWN_PET = 0x5A;
	public static final short CANCEL_DEBUFF = 0x5B;
	public static final short CHANGE_MAP_SPECIAL = 0x5C;
	public static final short USE_INNER_PORTAL = 0x5D;
	public static final short TROCK_ADD_MAP = 0x5E;
	public static final short QUEST_ACTION = 0x62;
	public static final short STATUS_EFFECTS_TOGGLE = 0x63;
	public static final short SKILL_MACRO = 0x65;
	public static final short REPORT = 0x68;
	public static final short USE_TREASURE_BOX = 0x69;
	public static final short PARTYCHAT = 0x6B;
	public static final short CLIENT_COMMAND = 0x6C;
	public static final short SPOUSECHAT = 0x6D;
	public static final short MESSENGER_ACT = 0x6E;
	public static final short MINIROOM_ACT = 0x6F;
	public static final short PARTYLIST_MODIFY = 0x70;
	public static final short DENY_PARTY_REQUEST = 0x71;
	public static final short GUILDLIST_MODIFY = 0x72;
	public static final short DENY_GUILD_REQUEST = 0x73;
	public static final short ADMIN_COMMAND = 0x74;
	public static final short ADMIN_COMMAND_LOG = 0x75;
	public static final short BUDDYLIST_MODIFY = 0x76;
	public static final short NOTE_ACTION = 0x77;
	public static final short USE_DOOR = 0x79;
	public static final short CHANGE_BINDING = 0x7B;
	public static final short ADMIN_LOG = 0x7F;
	public static final short UNKNOWN = 0x81;
	public static final short BBS_OPERATION = 0x86;
	public static final short ENTER_MTS = 0x87;
	public static final short PET_TALK = 0x8B;
	public static final short MOVE_PET = 0x8C;
	public static final short PET_CHAT = 0x8D;
	public static final short PET_COMMAND = 0x8E;
	public static final short PET_LOOT = 0x8F;
	public static final short PET_AUTO_POT = 0x90;
	public static final short PET_ITEM_IGNORE = 0x91;
	public static final short MOVE_SUMMON = 0x94;
	public static final short SUMMON_ATTACK = 0x95;
	public static final short DAMAGE_SUMMON = 0x96;
	public static final short MOVE_MOB = 0x9D;
	public static final short AUTO_AGGRO = 0x9E;
	public static final short MOB_DAMAGE_MOB = 0xA1;
	public static final short MONSTER_BOMB = 0xA2;
	public static final short HYPNOTIZE = 0xA3;
	public static final short MOVE_NPC = 0xA6;
	public static final short ITEM_PICKUP = 0xAB;
	public static final short DAMAGE_REACTOR = 0xAE;
	public static final short TOUCH_REACTOR = 0xAF;
	public static final short SNOWBALL = 0xB3;
	public static final short MONSTER_CARNIVAL = 0xB9;
	public static final short ENTERED_SHIP_MAP = 0xBB;
	public static final short PARTY_SEARCH_REGISTER = 0xBD;
	public static final short PARTY_SEARCH_START = 0xBF;
	public static final short PLAYER_UPDATE = 0xC0;
	public static final short CHECK_CASH = 0xC5;
	public static final short BUY_CS_ITEM = 0xC6;
	public static final short COUPON_CODE = 0xC7;
	public static final short MAPLE_TV = 0xD4;
	public static final short MTS_OP = 0xD9;

	private ClientRecvOps() {
		//uninstantiable...
	}
}
