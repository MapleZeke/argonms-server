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

/**
 *
 * @author GoldenKevin
 */
public final class ClientSendOps {
	public static final short LOGIN_RESULT = 0x00;
	public static final short SERVERLOAD_MSG = 0x03;
	public static final short GENDER_DONE = 0x04;
	public static final short PIN_RESPONSE = 0x06;
	public static final short PIN_ASSIGNED = 0x07;
	public static final short ALL_CHARLIST = 0x08;
	public static final short WORLD_ENTRY = 0x0A;
	public static final short CHARLIST = 0x0B;
	public static final short CHANNEL_ADDRESS = 0x0C;
	public static final short CHECK_NAME_RESP = 0x0D;
	public static final short CHAR_CREATED = 0x0E;
	public static final short DELETE_CHAR_RESPONSE = 0x0F;
	public static final short GAME_HOST_ADDRESS = 0x10;
	public static final short PING = 0x11;
	public static final short RELOG_RESPONSE = 0x16;
	public static final short MODIFY_INVENTORY_SLOT = 0x1A;
	public static final short UPDATE_INVENTORY_CAPACITY = 0x1B;
	public static final short PLAYER_STAT_UPDATE = 0x1C;
	public static final short FIRST_PERSON_APPLY_STATUS_EFFECT = 0x1D;
	public static final short FIRST_PERSON_CANCEL_STATUS_EFFECT = 0x1E;
	public static final short SKILL_ENTRY_UPDATE = 0x21;
	public static final short FAME_OPERATION = 0x23;
	public static final short SHOW_STATUS_INFO = 0x24;
	public static final short SHOW_QUEST_COMPLETION = 0x2E;
	public static final short SHOW_HIRED_MERCHANT_AGREEMENT = 0x2F;
	public static final short GENDER = 0x37;
	public static final short BBS_OPERATION = 0x38;
	public static final short PERSONAL_INFO_RESPONSE = 0x3A;
	public static final short PARTY_LIST = 0x3B;
	public static final short BUDDY_LIST = 0x3C;
	public static final short GUILD_LIST = 0x3E;
	public static final short ALLIANCE_LIST = 0x3F;
	public static final short SPAWN_PORTAL = 0x40;
	public static final short SERVER_MESSAGE = 0x41;
	public static final short TIP_MESSAGE = 0x4A;
	public static final short PLAYER_NPC = 0x4E;
	public static final short SKILL_MACRO = 0x5B;
	public static final short CHANGE_MAP = 0x5C;
	public static final short MTS_OPEN = 0x5D;
	public static final short CS_OPEN = 0x5E;
	public static final short BLOCK_PORTAL = 0x61;
	public static final short BLOCK_MIGRATE = 0x62;
	public static final short SHOW_EQUIP_EFFECT = 0x63;
	public static final short PRIVATE_CHAT = 0x64;
	public static final short WHISPER = 0x65;
	public static final short SPOUSE_CHAT = 0x66;
	public static final short MAP_EFFECT = 0x68;
	public static final short GM = 0x6B;
	public static final short CLOCK = 0x6E;
	public static final short SHIP = 0x6F;
	public static final short SHOW_PLAYER = 0x78;
	public static final short REMOVE_PLAYER = 0x79;
	public static final short MAP_CHAT = 0x7A;
	public static final short MINIROOM_BALLOON = 0x7C;
	public static final short SHOW_SCROLL_EFFECT = 0x7E;
	public static final short TOGGLE_PET = 0x7F;
	public static final short MOVE_PET = 0x81;
	public static final short PET_CHAT = 0x82;
	public static final short PET_NAME_CHANGE = 0x83;
	public static final short PET_ITEM_IGNORE = 0x84;
	public static final short PET_RESPONSE = 0x85;
	public static final short SHOW_SUMMON = 0x86;
	public static final short REMOVE_SUMMON = 0x87;
	public static final short MOVE_SUMMON = 0x88;
	public static final short SUMMON_ATTACK = 0x89;
	public static final short DAMAGE_SUMMON = 0x8A;
	public static final short MOVE_PLAYER = 0x8D;
	public static final short MELEE_ATTACK = 0x8E;
	public static final short RANGED_ATTACK = 0x8F;
	public static final short MAGIC_ATTACK = 0x90;
	public static final short ENERGY_CHARGE_ATTACK = 0x91;
	public static final short PREPARED_SKILL = 0x92;
	public static final short END_KEY_DOWN = 0x93;
	public static final short DAMAGE_PLAYER = 0x94;
	public static final short FACIAL_EXPRESSION = 0x95;
	public static final short ITEM_CHAIR = 0x97;
	public static final short UPDATE_AVATAR = 0x98;
	public static final short THIRD_PERSON_VISUAL_EFFECT = 0x99;
	public static final short THIRD_PERSON_APPLY_STATUS_EFFECT = 0x9A;
	public static final short THIRD_PERSON_CANCEL_STATUS_EFFECT = 0x9B;
	public static final short UPDATE_PARTY_MEMBER_HP = 0x9C;
	public static final short UPDATE_GUILD_MEMBERSHIP = 0x9D;
	public static final short UPDATE_GUILD_EMBLEM = 0x9E;
	public static final short CHAIR = 0xA0;
	public static final short FIRST_PERSON_VISUAL_EFFECT = 0xA1;
	public static final short QUEST_ACTION = 0xA6;
	public static final short PLAYER_HINT = 0xA9;
	public static final short COOLDOWN = 0xAD;
	public static final short SHOW_MONSTER = 0xAF;
	public static final short REMOVE_MONSTER = 0xB0;
	public static final short CONTROL_MONSTER = 0xB1;
	public static final short MOVE_MONSTER = 0xB2;
	public static final short MOVE_MONSTER_RESPONSE = 0xB3;
	public static final short APPLY_MONSTER_STATUS_EFFECT = 0xB5;
	public static final short CANCEL_MONSTER_STATUS_EFFECT = 0xB6;
	public static final short DAMAGE_MONSTER = 0xB9;
	public static final short SHOW_MONSTER_HP = 0xBD;
	public static final short MONSTER_DRAGGED = 0xBE;
	public static final short SHOW_NPC = 0xC2;
	public static final short REMOVE_NPC = 0xC3;
	public static final short CONTROL_NPC = 0xC4;
	public static final short MOVE_NPC = 0xC5;
	public static final short SHOW_HIRED_MERCHANT = 0xCA;
	public static final short REMOVE_HIRED_MERCHANT = 0xCB;
	public static final short HIRED_MERCHANT_BALLOON = 0xCC;
	public static final short SHOW_ITEM_DROP = 0xCD;
	public static final short REMOVE_ITEM_DROP = 0xCE;
	public static final short SHOW_MIST = 0xD2;
	public static final short REMOVE_MIST = 0xD3;
	public static final short SHOW_DOOR = 0xD4;
	public static final short REMOVE_DOOR = 0xD5;
	public static final short HIT_REACTOR = 0xD6;
	public static final short SHOW_REACTOR = 0xD8;
	public static final short REMOVE_REACTOR = 0xD9;
	public static final short NPC_TALK = 0xED;
	public static final short NPC_SHOP = 0xEE;
	public static final short CONFIRM_SHOP_TRANSACTION = 0xEF;
	public static final short NPC_STORAGE = 0xF0;
	public static final short MESSENGER_ACT = 0xF4;
	public static final short MINIROOM_ACT = 0xF5;
	public static final short CS_BALANCE = 0xFF;
	public static final short CASH_SHOP = 0x100;
	public static final short KEYMAP = 0x107;
	public static final short PET_AUTO_HP_POT = 0x108;
	public static final short PET_AUTO_MP_POT = 0x109;
	
	private ClientSendOps() {
		//uninstantiable...
	}
}
