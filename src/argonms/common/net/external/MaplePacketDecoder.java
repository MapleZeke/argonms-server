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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public final class MaplePacketDecoder extends ByteToMessageDecoder {
	private static final int HEADER_LENGTH = 4;

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		ClientSession<?> session = NettyClientListener.getSession(ctx.channel());
		if (session == null || in.readableBytes() < HEADER_LENGTH) {
			return;
		}

		in.markReaderIndex();
		byte[] header = new byte[HEADER_LENGTH];
		in.readBytes(header);
		if (!ClientEncryption.checkPacket(header, session.getRecvIv())) {
			session.close("Failed packet test");
			return;
		}

		int packetLength = ClientEncryption.getPacketLength(header);
		if (in.readableBytes() < packetLength) {
			in.resetReaderIndex();
			return;
		}

		byte[] packet = new byte[packetLength];
		in.readBytes(packet);
		byte[] iv = session.advanceRecvIv();
		ClientEncryption.aesOfbCrypt(packet, iv);
		ClientEncryption.mapleDecrypt(packet);
		out.add(packet);
	}
}
