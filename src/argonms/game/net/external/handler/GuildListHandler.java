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

package argonms.game.net.external.handler;

import argonms.common.character.inventory.InventoryTools;
import argonms.common.net.external.CheatTracker;
import argonms.common.net.external.ClientSendOps;
import argonms.common.util.DatabaseManager;
import argonms.common.util.TimeTool;
import argonms.common.util.dao.GuildBbsDAO;
import argonms.common.util.input.LittleEndianReader;
import argonms.common.util.output.LittleEndianByteArrayWriter;
import argonms.common.util.output.LittleEndianWriter;
import argonms.game.GameServer;
import argonms.game.character.GameCharacter;
import argonms.game.character.GuildList;
import argonms.game.net.external.GameClient;
import argonms.game.net.external.GamePackets;
import argonms.game.script.binding.ScriptNpc;
import argonms.game.script.binding.ScriptObjectManipulator;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GuildListHandler {
	private static final Logger LOG = Logger.getLogger(GuildListHandler.class.getName());

	private static final byte CREATE = 0x02;
	private static final byte INVITE = 0x05;
	private static final byte JOIN = 0x06;
	private static final byte LEAVE = 0x07;
	private static final byte EXPEL = 0x08;
	private static final byte CHANGE_RANK_STRING = 0x0D;
	private static final byte CHANGE_PLAYER_RANK = 0x0E;
	private static final byte CHANGE_EMBLEM = 0x0F;
	private static final byte CHANGE_NOTICE = 0x10;
	private static final byte GUILD_CONTRACT_RESPONSE = 0x1E;

	private static final byte EDIT_TOPIC_STARTER = 0x00;
	private static final byte DELETE_TOPIC = 0x01;
	private static final byte LIST_TOPICS = 0x02;
	private static final byte LOAD_TOPIC = 0x03;
	private static final byte NEW_REPLY = 0x04;
	private static final byte DELETE_REPLY = 0x05;

	public static final byte ASK_NAME = 0x01;
	public static final byte GENERAL_ERROR = 0x02;
	public static final byte GUILD_CONTRACT = 0x03;
	public static final byte INVITE_SENT = 0x05;
	public static final byte ASK_EMBLEM = 0x11;
	public static final byte LIST = 0x1A;
	public static final byte NAME_TAKEN = 0x1C;
	public static final byte LEVEL_TOO_LOW = 0x23;
	public static final byte JOINED_GUILD = 0x27;
	public static final byte ALREADY_IN_GUILD = 0x28;
	public static final byte CANNOT_FIND = 0x2A;
	public static final byte LEFT_GUILD = 0x2C;
	public static final byte EXPELLED_FROM_GUILD = 0x2F;
	public static final byte DISBANDED_GUILD = 0x32;
	public static final byte INVITE_DENIED = 0x37;
	public static final byte CAPACITY_CHANGED = 0x3A;
	public static final byte LEVEL_JOB_CHANGED = 0x3C;
	public static final byte CHANNEL_CHANGE = 0x3D;
	public static final byte RANK_TITLES_CHANGED = 0x3E;
	public static final byte RANK_CHANGED = 0x40;
	public static final byte EMBLEM_CHANGED = 0x42;
	public static final byte NOTICE_CHANGED = 0x44;
	public static final byte GUILD_GP_CHANGED = 0x48;
	public static final byte SHOW_GUILD_RANK_BOARD = 0x49;

	private static final byte TOPIC_LIST = 0x06;
	private static final byte REPLY_LIST = 0x07;

	public static void handleListModification(LittleEndianReader packet, GameClient gc) {
		GameCharacter p = gc.getPlayer();
		GuildList currentGuild = p.getGuild();
		switch (packet.readByte()) {
			case CREATE: {
				String name = packet.readLengthPrefixedString();
				ScriptNpc npc = gc.getNpc();
				if (npc != null) {
					ScriptObjectManipulator.guildNameReceived(npc, name);
				}
				break;
			}
			case INVITE: {
				//invites only check players on current channel
				String name = packet.readLengthPrefixedString();
				GameCharacter invited = GameServer.getChannel(gc.getChannel()).getPlayerByName(name);
				if (currentGuild != null) {
					if (!currentGuild.isFull()) {
						if (invited != null) {
							if (invited.getGuild() == null) {
								invited.getClient().getSession().send(writeGuildInvite(currentGuild.getId(), p.getName()));
							} else {
								gc.getSession().send(GamePackets.writeSimpleGuildListMessage(ALREADY_IN_GUILD));
							}
						} else {
							gc.getSession().send(GamePackets.writeSimpleGuildListMessage(CANNOT_FIND));
						}
					} else {
						CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to invite player to full guild");
					}
				} else {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to invite player to nonexistent guild");
				}
				break;
			}
			case JOIN: {
				int guildId = packet.readInt();
				int characterId = packet.readInt();
				if (characterId != p.getId()) {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.CERTAIN_PACKET_EDITING, "Tried to join guild without being invited");
					return;
				}
				//TODO: check if player was actually invited

				if (currentGuild == null) {
					GameServer.getChannel(gc.getChannel()).getCrossServerInterface().sendJoinGuild(p, guildId);
				} else {
					gc.getSession().send(GamePackets.writeSimpleGuildListMessage(ALREADY_IN_GUILD));
				}
				break;
			}
			case LEAVE: {
				int characterId = packet.readInt();
				String characterName = packet.readLengthPrefixedString();
				if (characterId != p.getId() || !p.getName().equals(characterName) || currentGuild == null) {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to leave guild without being in one");
					return;
				}

				GameServer.getChannel(gc.getChannel()).getCrossServerInterface().sendLeaveGuild(p, currentGuild.getId());
				break;
			}
			case EXPEL: {
				int expelled = packet.readInt();
				/*String expelledName = */packet.readLengthPrefixedString();
				if (currentGuild == null || currentGuild.getMember(p.getId()).getRank() > 2) {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to expel guild member without having privileges");
					return;
				}

				GameServer.getChannel(gc.getChannel()).getCrossServerInterface().sendExpelGuildMember(currentGuild.getMember(expelled), currentGuild.getId());
				break;
			}
			case CHANGE_RANK_STRING: {
				String[] titles = new String[5];
				for (int i = 0; i < 5; i++) {
					titles[i] = packet.readLengthPrefixedString();
					if (titles[i].length() > 12 || (i <= 2 || i > 2 && !titles[i].isEmpty()) && titles[i].length() < 4) {
						CheatTracker.get(gc).suspicious(CheatTracker.Infraction.CERTAIN_PACKET_EDITING, "Tried to set invalid guild title");
						return;
					}
				}
				if (currentGuild == null || currentGuild.getMember(p.getId()).getRank() > 2) {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to edit guild titles without having privileges");
					return;
				}

				GameServer.getChannel(gc.getChannel()).getCrossServerInterface().sendUpdateGuildTitles(currentGuild, titles);
				break;
			}
			case CHANGE_PLAYER_RANK: {
				int characterId = packet.readInt();
				byte newRank = packet.readByte();
				if (currentGuild == null || currentGuild.getMember(p.getId()).getRank() > 2 || newRank >= 2 && currentGuild.getMember(p.getId()).getRank() > 1) {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to edit guild rankings without having privileges");
					return;
				}

				GameServer.getChannel(gc.getChannel()).getCrossServerInterface().sendUpdateGuildRank(currentGuild, characterId, newRank);
				break;
			}
			case CHANGE_EMBLEM: {
				short background = packet.readShort();
				byte backgroundColor = packet.readByte();
				short design = packet.readShort();
				byte designColor = packet.readByte();
				ScriptNpc npc = gc.getNpc();
				if (npc != null) {
					ScriptObjectManipulator.guildEmblemReceived(npc, background, backgroundColor, design, designColor);
				}
				break;
			}
			case CHANGE_NOTICE: {
				String notice = packet.readLengthPrefixedString();
				if (notice.length() > 100) {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.CERTAIN_PACKET_EDITING, "Tried to set invalid guild notice");
					return;
				}

				GameServer.getChannel(gc.getChannel()).getCrossServerInterface().sendUpdateGuildNotice(currentGuild, notice);
				break;
			}
			case GUILD_CONTRACT_RESPONSE: {
				int characterId = packet.readInt();
				boolean accept = packet.readBool();
				if (characterId != p.getId()) {
					CheatTracker.get(gc).suspicious(CheatTracker.Infraction.CERTAIN_PACKET_EDITING, "Tried to accept guild contract of another player");
					return;
				}

				GameServer.getChannel(gc.getChannel()).getCrossServerInterface().sendVoteGuildContract(currentGuild, characterId, accept);
				break;
			}
		}
	}

	public static void handleDenyRequest(LittleEndianReader packet, GameClient gc) {
		packet.readByte();
		String from = packet.readLengthPrefixedString();
		String to = packet.readLengthPrefixedString();
		GameCharacter inviter = GameServer.getChannel(gc.getChannel()).getPlayerByName(from);
		if (inviter != null) { //check if inviter changed channels or logged off
			inviter.getClient().getSession().send(writeGuildInviteRejected(to));
		}
	}

	private static String truncateTo(String str, int maxLength) {
		if (str.length() > maxLength) {
			return str.substring(0, maxLength);
		}
		return str;
	}

	public static void handleGuildBbs(LittleEndianReader packet, GameClient gc) {
		GameCharacter p = gc.getPlayer();
		GuildList guild = p.getGuild();
		if (guild == null) {
			return; //player has just been expelled from guild or is packet editing

		}
		switch (packet.readByte()) {
			case EDIT_TOPIC_STARTER: {
				GuildBbsDAO.BbsTopic topic;
				guild.lockBbsWrite();
				try {
					int topicId = -1; //new topic
					if (packet.readBool()) {
						topicId = packet.readInt(); //edit existing topic
					}
					if (packet.readBool()) {
						topicId = 0; //create new notice topic
					}
					String subject = truncateTo(packet.readLengthPrefixedString(), 25);
					String content = truncateTo(packet.readLengthPrefixedString(), 600);
					int icon = packet.readInt();
					if (icon >= 100 && icon <= 106) {
						if (!InventoryTools.hasItem(p, 5290000 - 100 + icon, 1)) {
							CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to use message icon for guild BBS without owning item");
							return;
						}
					} else if (icon < 0 || icon > 2) {
						CheatTracker.get(gc).suspicious(CheatTracker.Infraction.CERTAIN_PACKET_EDITING, "Tried to use invalid message icon for guild BBS topic starter");
						return;
					}

				long now = System.currentTimeMillis();
				try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
					if (topicId == -1 || topicId == 0) {
						if (topicId == -1) {
							topicId = GuildBbsDAO.getAndIncrement(con, "guilds", "nextbbstopicid", "id", null, guild.getId(), -1);
						}
						GuildBbsDAO.insertOrUpdateTopic(con, guild.getId(), topicId, p.getId(), now, subject, content, icon, topicId == 0);
						if (topicId == -1) {
							topic = new GuildBbsDAO.BbsTopic(topicId, p.getId(), now, subject, content, icon, List.of());
						} else {
							topic = new GuildBbsDAO.BbsTopic(topicId, p.getId(), now, subject, content, icon, GuildBbsDAO.loadReplies(con, guild.getId(), topicId));
						}
					} else {
						boolean isAdmin = guild.getMember(p.getId()).getRank() <= 2;
						int updateRows = GuildBbsDAO.updateTopic(con, guild.getId(), topicId, p.getId(), now, subject, content, icon, isAdmin);
						if (updateRows == 0) {
							CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to edit BBS topic starter without permission");
							return;
						}
						topic = new GuildBbsDAO.BbsTopic(topicId, p.getId(), now, subject, content, icon, GuildBbsDAO.loadReplies(con, guild.getId(), topicId));
					}
				} catch (SQLException ex) {
					LOG.log(Level.WARNING, "Could not edit guild BBS topic starter", ex);
					return;
				}
			} finally {
					guild.unlockBbsWrite();
				}

				gc.getSession().send(writeBbsTopic(topic));
				break;
			}
			case DELETE_TOPIC: {
				guild.lockBbsWrite();
				try {
					int topicId = packet.readInt();

					try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
						boolean isAdmin = guild.getMember(p.getId()).getRank() <= 2;
						int updateRows = GuildBbsDAO.deleteTopic(con, guild.getId(), topicId, p.getId(), isAdmin);
						if (updateRows == 0) {
							CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to delete BBS topic without permission");
						}
					} catch (SQLException ex) {
						LOG.log(Level.WARNING, "Could not delete guild BBS topic", ex);
					}
				} finally {
					guild.unlockBbsWrite();
				}
				break;
			}
			case LIST_TOPICS: {
				GuildBbsDAO.BbsTopic notice;
				List<GuildBbsDAO.BbsTopic> topics;
				int totalTopics;
				guild.lockBbsRead();
				try {
					int page = packet.readInt();

					try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
						notice = GuildBbsDAO.loadTopic(con, guild.getId(), 0);
						topics = GuildBbsDAO.loadTopics(con, guild.getId(), page);
						totalTopics = GuildBbsDAO.countTopics(con, guild.getId());
					} catch (SQLException ex) {
						LOG.log(Level.WARNING, "Could not list guild BBS topics", ex);
						return;
					}
				} finally {
					guild.unlockBbsRead();
				}

				gc.getSession().send(writeBbs(notice, topics, totalTopics));
				break;
			}
			case LOAD_TOPIC: {
				int topicId = packet.readInt();

				GuildBbsDAO.BbsTopic topic;
				try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
					topic = GuildBbsDAO.loadTopic(con, guild.getId(), topicId);
					if (topic == null) {
						return;
					}
				} catch (SQLException ex) {
					LOG.log(Level.WARNING, "Could not load guild BBS topic", ex);
					return;
				}

				gc.getSession().send(writeBbsTopic(topic));
				break;
			}
			case NEW_REPLY: {
				GuildBbsDAO.BbsTopic topic;
				guild.lockBbsWrite();
				try {
					int topicId = packet.readInt();
					String content = truncateTo(packet.readLengthPrefixedString(), 25);

					try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
						int replyId = GuildBbsDAO.getAndIncrement(con, "guildbbstopics", "nextreplyid", "guildid", "topicid", guild.getId(), topicId);
						GuildBbsDAO.insertReply(con, guild.getId(), topicId, replyId, p.getId(), System.currentTimeMillis(), content);

						topic = GuildBbsDAO.loadTopic(con, guild.getId(), topicId);
					} catch (SQLException ex) {
						LOG.log(Level.WARNING, "Could not create guild BBS topic reply", ex);
						return;
					}
				} finally {
					guild.unlockBbsWrite();
				}

				gc.getSession().send(writeBbsTopic(topic));
				break;
			}
			case DELETE_REPLY: {
				GuildBbsDAO.BbsTopic topic;
				guild.lockBbsWrite();
				try {
					int topicId = packet.readInt();
					int replyId = packet.readInt();

					try (Connection con = DatabaseManager.getConnection(DatabaseManager.DatabaseType.STATE)) {
						boolean isAdmin = guild.getMember(p.getId()).getRank() <= 2;
						int updateRows = GuildBbsDAO.deleteReply(con, guild.getId(), topicId, replyId, p.getId(), isAdmin);
						if (updateRows == 0) {
							CheatTracker.get(gc).suspicious(CheatTracker.Infraction.POSSIBLE_PACKET_EDITING, "Tried to delete BBS reply without permission");
							return;
						}

						topic = GuildBbsDAO.loadTopic(con, guild.getId(), topicId);
					} catch (SQLException ex) {
						LOG.log(Level.WARNING, "Could not delete guild BBS reply", ex);
						return;
					}
				} finally {
					guild.unlockBbsWrite();
				}

				gc.getSession().send(writeBbsTopic(topic));
				break;
			}
		}
	}

	private static byte[] writeGuildInviteRejected(String name) {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter(5 + name.length());

		lew.writeShort(ClientSendOps.GUILD_LIST);
		lew.writeByte(INVITE_DENIED);
		lew.writeLengthPrefixedString(name);

		return lew.getBytes();
	}

	private static byte[] writeGuildInvite(int guildId, String inviter) {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter(9 + inviter.length());

		lew.writeShort(ClientSendOps.GUILD_LIST);
		lew.writeByte(INVITE_SENT);
		lew.writeInt(guildId);
		lew.writeLengthPrefixedString(inviter);

		return lew.getBytes();
	}

	private static void writeBbsEntry(LittleEndianWriter lew, GuildBbsDAO.BbsTopic topic) {
		lew.writeInt(topic.topicId());
		lew.writeInt(topic.poster());
		lew.writeLengthPrefixedString(topic.subject());
		lew.writeLong(TimeTool.unixToWindowsTime(topic.postTime()));
		lew.writeInt(topic.icon());
		lew.writeInt(topic.replies().size());
	}

	private static byte[] writeBbs(GuildBbsDAO.BbsTopic notice, List<GuildBbsDAO.BbsTopic> topics, int totalTopics) {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter();

		lew.writeShort(ClientSendOps.BBS_OPERATION);
		lew.writeByte(TOPIC_LIST);
		lew.writeBool(notice != null);
		if (notice != null) {
			writeBbsEntry(lew, notice);
		}
		lew.writeInt(totalTopics);
		lew.writeInt(topics.size());
		for (GuildBbsDAO.BbsTopic topic : topics)
			writeBbsEntry(lew, topic);

		return lew.getBytes();
	}

	private static byte[] writeBbsTopic(GuildBbsDAO.BbsTopic topic) {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter();

		lew.writeShort(ClientSendOps.BBS_OPERATION);
		lew.writeByte(REPLY_LIST);
		lew.writeInt(topic.topicId());
		lew.writeInt(topic.poster());
		lew.writeLong(TimeTool.unixToWindowsTime(topic.postTime()));
		lew.writeLengthPrefixedString(topic.subject());
		lew.writeLengthPrefixedString(topic.content());
		lew.writeInt(topic.icon());
		lew.writeInt(topic.replies().size());
		for (GuildBbsDAO.BbsReply reply : topic.replies()) {
			lew.writeInt(reply.replyId());
			lew.writeInt(reply.poster());
			lew.writeLong(TimeTool.unixToWindowsTime(reply.postTime()));
			lew.writeLengthPrefixedString(reply.content());
		}

		return lew.getBytes();
	}

	private GuildListHandler() {
	}
}
