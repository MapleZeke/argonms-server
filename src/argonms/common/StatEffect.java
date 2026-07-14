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

package argonms.common;

/**
 *
 * @author GoldenKevin
 */
public final class StatEffect {
	public static final byte STR = 0;
	public static final byte DEX = 1;
	public static final byte INT = 2;
	public static final byte LUK = 3;
	public static final byte PAD = 4;
	public static final byte PDD = 5;
	public static final byte MAD = 6;
	public static final byte MDD = 7;
	public static final byte ACC = 8;
	public static final byte EVA = 9;
	public static final byte MHP = 10;
	public static final byte MMP = 11;
	public static final byte Speed = 12;
	public static final byte Jump = 13;
	public static final byte Level = 14;
	public static final byte MaxLevel = 15;

	private StatEffect() {
		//uninstantiable...
	}
}
