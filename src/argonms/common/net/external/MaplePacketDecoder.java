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

import argonms.common.GlobalConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public final class MaplePacketDecoder extends ByteToMessageDecoder {
	private static final int HEADER_LENGTH = 4;

	/**
	 * Maximum allowed packet body size (64 KB). Packets exceeding this size
	 * are rejected to prevent memory exhaustion attacks.
	 */
	private static final int MAX_PACKET_SIZE = 65_536;

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		ClientSession<?> session = NettyClientListener.getSession(ctx.channel());
		if (session == null || in.readableBytes() < HEADER_LENGTH) {
			return;
		}

		byte[] iv = NettyClientListener.getRecvIv(ctx.channel());
		if (iv == null) {
			session.close("Missing receive IV");
			return;
		}
		int readerIndex = in.readerIndex();
		if (!isValidHeader(in, readerIndex, iv)) {
			session.close("Failed packet test");
			return;
		}

		int packetLength = readPacketLength(in, readerIndex);
		if (packetLength > MAX_PACKET_SIZE) {
			session.close("Packet size " + packetLength + " exceeds maximum " + MAX_PACKET_SIZE);
			return;
		}

		int availableBodyBytes = in.readableBytes() - HEADER_LENGTH;
		if (availableBodyBytes < packetLength) {
			return;
		}

		in.skipBytes(HEADER_LENGTH);
		byte[] packet = new byte[packetLength];
		in.readBytes(packet);
		NettyClientListener.advanceRecvIv(ctx.channel());
		ClientEncryption.aesOfbCrypt(packet, iv);
		ClientEncryption.mapleDecrypt(packet);
		out.add(packet);
	}

	private static boolean isValidHeader(ByteBuf in, int readerIndex, byte[] iv) {
		return (((in.getByte(readerIndex) ^ iv[2]) & 0xFF) == (GlobalConstants.MAPLE_VERSION & 0xFF))
				&& (((in.getByte(readerIndex + 1) ^ iv[3]) & 0xFF) == ((GlobalConstants.MAPLE_VERSION >>> 8) & 0xFF));
	}

	private static int readPacketLength(ByteBuf in, int readerIndex) {
		int versionMask = in.getUnsignedShortLE(readerIndex);
		int maskedLength = in.getUnsignedShortLE(readerIndex + Short.BYTES);
		return (versionMask ^ maskedLength) & 0xFFFF;
	}
}
