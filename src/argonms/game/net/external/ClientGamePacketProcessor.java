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

package argonms.game.net.external;

import argonms.common.net.external.ClientPacketProcessor;
import argonms.common.net.external.ClientRecvOps;
import argonms.common.net.external.PacketDispatchTable;
import argonms.common.util.input.LittleEndianReader;
import argonms.game.net.external.handler.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientGamePacketProcessor extends ClientPacketProcessor<GameClient> {
	private static final Logger LOG = Logger.getLogger(ClientPacketProcessor.class.getName());

	private static final PacketDispatchTable<GameClient> TABLE = new PacketDispatchTable<GameClient>()
		// Remnants of character-select flow received after transfer – silently ignored
		.noOp(ClientRecvOps.SERVERLIST_REREQUEST)
		.noOp(ClientRecvOps.EXIT_CHARLIST)
		.noOp(ClientRecvOps.PICK_ALL_CHAR)
		.noOp(ClientRecvOps.ENTER_EXIT_VIEW_ALL)
		.noOp(ClientRecvOps.CHAR_SELECT)
		.noOp(ClientRecvOps.RELOG)
		// Core lifecycle
		.register(ClientRecvOps.PLAYER_CONNECTED,      EnterHandler::handlePlayerConnection)
		.register(ClientRecvOps.PONG,                  (r, gc) -> gc.getSession().receivedPong())
		.register(ClientRecvOps.CLIENT_ERROR,          (r, gc) -> gc.clientError(r.readLengthPrefixedString()))
		.noOp(ClientRecvOps.AES_IV_UPDATE_REQUEST)
		// Navigation
		.register(ClientRecvOps.CHANGE_MAP,            GoToHandler::handleMapChange)
		.register(ClientRecvOps.CHANGE_CHANNEL,        GoToHandler::handleChangeChannel)
		.register(ClientRecvOps.ENTER_CASH_SHOP,       GoToHandler::handleWarpCs)
		.register(ClientRecvOps.CHANGE_MAP_SPECIAL,    GoToHandler::handleEnteredSpecialPortal)
		.register(ClientRecvOps.USE_INNER_PORTAL,      GoToHandler::handleEnteredInnerPortal)
		.register(ClientRecvOps.USE_DOOR,              GoToHandler::handleMysticDoor)
		// Movement
		.register(ClientRecvOps.MOVE_PLAYER,           MovementHandler::handleMovePlayer)
		.register(ClientRecvOps.MOVE_PET,              MovementHandler::handleMovePet)
		.register(ClientRecvOps.MOVE_SUMMON,           MovementHandler::handleMoveSummon)
		.register(ClientRecvOps.MOVE_MOB,              MovementHandler::handleMoveMob)
		.register(ClientRecvOps.MOVE_NPC,              MovementHandler::handleMoveNpc)
		// Combat
		.register(ClientRecvOps.MELEE_ATTACK,          DealDamageHandler::handleMeleeAttack)
		.register(ClientRecvOps.RANGED_ATTACK,         DealDamageHandler::handleRangedAttack)
		.register(ClientRecvOps.MAGIC_ATTACK,          DealDamageHandler::handleMagicAttack)
		.register(ClientRecvOps.ENERGY_CHARGE_ATTACK,  DealDamageHandler::handleEnergyChargeAttack)
		.register(ClientRecvOps.PREPARED_SKILL,        DealDamageHandler::handlePreparedSkill)
		.register(ClientRecvOps.SUMMON_ATTACK,         DealDamageHandler::handleSummonAttack)
		.register(ClientRecvOps.TAKE_DAMAGE,           TakeDamageHandler::handleTakeDamage)
		.register(ClientRecvOps.DAMAGE_SUMMON,         TakeDamageHandler::handlePuppetTakeDamage)
		.register(ClientRecvOps.MOB_DAMAGE_MOB,        TakeDamageHandler::handleMobDamageMob)
		// Player misc
		.register(ClientRecvOps.CHAIR,                 PlayerMiscHandler::handleChair)
		.register(ClientRecvOps.USE_CHAIR_ITEM,        PlayerMiscHandler::handleItemChair)
		.register(ClientRecvOps.FACIAL_EXPRESSION,     PlayerMiscHandler::handleEmote)
		.register(ClientRecvOps.HEAL_OVER_TIME,        PlayerMiscHandler::handleReplenishHpMp)
		.register(ClientRecvOps.CHANGE_BINDING,        PlayerMiscHandler::handleBindingChange)
		.noOp(ClientRecvOps.CANCEL_DEBUFF)
		.noOp(ClientRecvOps.STATUS_EFFECTS_TOGGLE)
		.noOp(ClientRecvOps.UNKNOWN)               // suppressed – would spam logs
		// Stats & skills
		.register(ClientRecvOps.DISTRIBUTE_AP,         StatAllocationHandler::handleApAllocation)
		.register(ClientRecvOps.DISTRIBUTE_SP,         StatAllocationHandler::handleSpAllocation)
		.register(ClientRecvOps.USE_SKILL,             BuffHandler::handleUseSkill)
		.register(ClientRecvOps.CANCEL_SKILL,          BuffHandler::handleCancelSkill)
		.register(ClientRecvOps.SKILL_MACRO,           StatAllocationHandler::handleSkillMacroAssign)
		.register(ClientRecvOps.USE_ITEM,              BuffHandler::handleUseItem)
		.register(ClientRecvOps.CANCEL_ITEM,           BuffHandler::handleCancelItem)
		// Inventory
		.register(ClientRecvOps.ITEM_MOVE,             InventoryHandler::handleItemMove)
		.register(ClientRecvOps.USE_RETURN_SCROLL,     InventoryHandler::handleReturnScroll)
		.register(ClientRecvOps.USE_UPGRADE_SCROLL,    InventoryHandler::handleUpgradeScroll)
		.register(ClientRecvOps.MESO_DROP,             InventoryHandler::handleMesoDrop)
		.register(ClientRecvOps.ITEM_PICKUP,           InventoryHandler::handleMapItemPickUp)
		.register(ClientRecvOps.PET_LOOT,              InventoryHandler::handlePetMapItemPickUp)
		.register(ClientRecvOps.USE_CASH_ITEM,         CashConsumeHandler::handleCashItem)
		// NPCs & quests
		.register(ClientRecvOps.NPC_TALK,              NpcHandler::handleStartConversation)
		.register(ClientRecvOps.NPC_TALK_MORE,         NpcHandler::handleContinueConversation)
		.register(ClientRecvOps.QUEST_ACTION,          NpcHandler::handleQuestAction)
		.register(ClientRecvOps.NPC_SHOP,              NpcMiniroomHandler::handleNpcShopAction)
		.register(ClientRecvOps.NPC_STORAGE,           NpcMiniroomHandler::handleNpcStorageAction)
		// Social
		.register(ClientRecvOps.MAP_CHAT,              ChatHandler::handleMapChat)
		.register(ClientRecvOps.PARTYCHAT,             ChatHandler::handlePrivateChat)
		.register(ClientRecvOps.CLIENT_COMMAND,        ChatHandler::handleClientCommand)
		.register(ClientRecvOps.SPOUSECHAT,            ChatHandler::handleSpouseChat)
		.register(ClientRecvOps.GIVE_FAME,             PersonalInfoHandler::handleFameUp)
		.register(ClientRecvOps.OPEN_PERSONAL_INFO,    PersonalInfoHandler::handleOpenInfo)
		.register(ClientRecvOps.MESSENGER_ACT,         MessengerHandler::handleAction)
		.register(ClientRecvOps.MINIROOM_ACT,          MiniroomHandler::handleAction)
		.register(ClientRecvOps.PARTYLIST_MODIFY,      PartyListHandler::handleListModification)
		.register(ClientRecvOps.DENY_PARTY_REQUEST,    PartyListHandler::handleDenyRequest)
		.register(ClientRecvOps.GUILDLIST_MODIFY,      GuildListHandler::handleListModification)
		.register(ClientRecvOps.DENY_GUILD_REQUEST,    GuildListHandler::handleDenyRequest)
		.register(ClientRecvOps.BBS_OPERATION,         GuildListHandler::handleGuildBbs)
		.register(ClientRecvOps.BUDDYLIST_MODIFY,      BuddyListHandler::handleListModification)
		// Pets
		.register(ClientRecvOps.SPAWN_PET,             PetHandler::handleUsePet)
		.register(ClientRecvOps.PET_FOOD,              PetHandler::handlePetFood)
		.register(ClientRecvOps.PET_CHAT,              PetHandler::handlePetChat)
		.register(ClientRecvOps.PET_COMMAND,           PetHandler::handlePetCommand)
		.register(ClientRecvOps.PET_AUTO_POT,          PetHandler::handlePetAutoPotion)
		.register(ClientRecvOps.PET_ITEM_IGNORE,       PetHandler::handlePetItemIgnore)
		// Mobs & reactors
		.noOp(ClientRecvOps.AUTO_AGGRO)
		.register(ClientRecvOps.DAMAGE_REACTOR,        ReactorHandler::handleReactorTrigger)
		.register(ClientRecvOps.TOUCH_REACTOR,         ReactorHandler::handleReactorTouch)
		// Misc
		.register(ClientRecvOps.ENTERED_SHIP_MAP,      EnterHandler::handleShipDockedCheck)
		.register(ClientRecvOps.PLAYER_UPDATE,         (r, gc) -> gc.getPlayer().saveCharacter())
		.noOp(ClientRecvOps.MAPLE_TV);

	@Override
	public void process(LittleEndianReader reader, GameClient gc) {
		if (!TABLE.dispatch(reader, gc)) {
			LOG.log(Level.FINE, "Received unhandled client packet {0} bytes long:\n{1}",
					new Object[]{reader.available() + 2, reader});
		}
	}
}
