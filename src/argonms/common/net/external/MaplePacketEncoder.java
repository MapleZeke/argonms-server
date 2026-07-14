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
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.Arrays;

public final class MaplePacketEncoder extends MessageToByteEncoder<byte[]> {
	@Override
	protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) {
		ClientSession<?> session = NettyClientListener.getSession(ctx.channel());
		if (session == null) {
			return;
		}

		byte[] iv = session.advanceSendIv();
		byte[] body = Arrays.copyOf(msg, msg.length);
		byte[] header = ClientEncryption.makePacketHeader(body.length, iv);
		ClientEncryption.mapleEncrypt(body);
		ClientEncryption.aesOfbCrypt(body, iv);
		out.writeBytes(header);
		out.writeBytes(body);
	}
}
