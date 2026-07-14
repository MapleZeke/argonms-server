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
 * A helper class with static methods that accept job id parameters and check
 * various conditions.
 * @author GoldenKevin
 */
public final class PlayerJob {
	public static final int CLASS_BEGINNER = 0;
	public static final int CLASS_WARRIOR = 1;
	public static final int CLASS_MAGICIAN = 2;
	public static final int CLASS_BOWMAN = 3;
	public static final int CLASS_THIEF = 4;
	public static final int CLASS_PIRATE = 5;
	public static final int CLASS_GAMEMASTER = 9;

	public static final short JOB_BEGINNER = 0;
	public static final short JOB_SWORDMAN = 100;
	public static final short JOB_FIGHTER = 110;
	public static final short JOB_CRUSADER = 111;
	public static final short JOB_HERO = 112;
	public static final short JOB_PAGE = 120;
	public static final short JOB_WHITE_KNIGHT = 121;
	public static final short JOB_PALADIN = 122;
	public static final short JOB_SPEARMAN = 130;
	public static final short JOB_DRAGON_KNIGHT = 131;
	public static final short JOB_DARK_KNIGHT = 132;
	public static final short JOB_MAGICIAN = 200;
	public static final short JOB_FP_WIZARD = 210;
	public static final short JOB_FP_MAGE = 211;
	public static final short JOB_FP_ARCH_MAGE = 212;
	public static final short JOB_IL_WIZARD = 220;
	public static final short JOB_IL_MAGE = 221;
	public static final short JOB_IL_ARCH_MAGE = 222;
	public static final short JOB_CLERIC = 230;
	public static final short JOB_PRIEST = 231;
	public static final short JOB_BISHOP = 232;
	public static final short JOB_ARCHER = 300;
	public static final short JOB_HUNTER = 310;
	public static final short JOB_RANGER = 311;
	public static final short JOB_BOWMASTER = 312;
	public static final short JOB_CROSSBOWMAN = 320;
	public static final short JOB_SNIPER = 321;
	public static final short JOB_MARKSMAN = 322;
	public static final short JOB_ROUGE = 400;
	public static final short JOB_ASSASSIN = 410;
	public static final short JOB_HERMIT = 411;
	public static final short JOB_NIGHT_LORD = 412;
	public static final short JOB_BANDIT = 420;
	public static final short JOB_CHIEF_BANDIT = 421;
	public static final short JOB_SHADOWER = 422;
	public static final short JOB_PIRATE = 500;
	public static final short JOB_BRAWLER = 510;
	public static final short JOB_MARAUDER = 511;
	public static final short JOB_BUCCANEER = 512;
	public static final short JOB_GUNSLINGER = 520;
	public static final short JOB_OUTLAW = 521;
	public static final short JOB_CORSAIR = 522;
	public static final short JOB_GM = 900;
	public static final short JOB_SUPER_GM = 910;

	public static byte ALL_JOBS_BITSTRING = 0x3F; //1 + 2 + 4 + 8 + 16 + 32

	public static boolean isJobInBitString(short job, short bitstring) {
		int jobPath = getJobPath(job);
		if (jobPath == CLASS_GAMEMASTER) {
			return true;
		}
		return (bitstring & (1 << jobPath)) != 0;
	}

	public static int getAdvancement(short jobid) {
		if (jobid == JOB_BEGINNER) {
			return 0; //no job advancement
		}
		if (jobid % 100 == 0) {
			return 1; //first job advancement
		}
		switch (jobid % 10) {
			case 0:
				return 2; //second job advancement
			case 1:
				return 3; //third job advancement
			case 2:
				return 4; //fourth job advancement
		}
		return -1;
	}

	public static int getJobPath(short jobid) {
		return jobid / 100;
	}

	public static boolean isBeginner(short jobid) {
		return getJobPath(jobid) == CLASS_BEGINNER;
	}

	public static boolean isWarrior(short jobid) {
		return getJobPath(jobid) == CLASS_WARRIOR;
	}

	public static boolean isMage(short jobid) {
		return getJobPath(jobid) == CLASS_MAGICIAN;
	}

	public static boolean isArcher(short jobid) {
		return getJobPath(jobid) == CLASS_BOWMAN;
	}

	public static boolean isThief(short jobid) {
		return getJobPath(jobid) == CLASS_THIEF;
	}

	public static boolean isPirate(short jobid) {
		return getJobPath(jobid) == CLASS_PIRATE;
	}

	public static boolean isGameMaster(short jobid) {
		return getJobPath(jobid) == CLASS_GAMEMASTER;
	}

	private PlayerJob() {
		//uninstantiable...
	}
}
