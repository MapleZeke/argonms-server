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

package argonms.game.command;

import argonms.common.GlobalConstants;
import argonms.common.UserPrivileges;
import java.util.Collections;
import java.util.List;

public class StatCommandHandler extends AbstractCommandDefinition<CommandCaller> {
	@Override
	public String getHelpMessage() {
		return "Change the value of a player's stat.";
	}

	@Override
	public String getUsage() {
		return "Usage: !stat [-t <target>] set|add str|dex|int|luk|ap|sp|level|exp|job|maxhp|maxmp|hp|mp|fame|meso <amount>";
	}

	@Override
	public byte minPrivilegeLevel() {
		return UserPrivileges.GM;
	}

	@Override
	public void execute(CommandCaller caller, CommandArguments args, CommandOutput resp) {
		String targetName = args.extractOptionalTarget(caller.getName());
		if (targetName == null) {
			resp.printErr(getUsage());
			return;
		}
		CommandTarget target = args.getTargetByName(targetName, caller);
		if (target == null) {
			resp.printErr("The character " + targetName + " does not exist.");
			resp.printErr(getUsage());
			return;
		}

		if (!args.hasNext()) {
			resp.printErr(getUsage());
			return;
		}
		String option = args.next();

		if (!args.hasNext()) {
			resp.printErr(getUsage());
			return;
		}
		String stat = args.next();

		if (!args.hasNext()) {
			resp.printErr(getUsage());
			return;
		}
		String param = args.next();
		int val;
		try {
			val = Integer.parseInt(param);
		} catch (NumberFormatException e) {
			resp.printErr(getUsage());
			return;
		}

		List<CommandTarget.CharacterManipulation> updated;
		if ("set".equalsIgnoreCase(option)) {
			if ("str".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_STR, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("dex".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_DEX, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("int".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_INT, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("luk".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_LUK, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("ap".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_AP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("sp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_SP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("level".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_LEVEL, Short.valueOf((short) Math.min(val, GlobalConstants.MAX_LEVEL))));
			} else if ("exp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_EXP, Integer.valueOf(val)));
			} else if ("job".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_JOB, Short.valueOf((short) val)));
			} else if ("maxhp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_MAX_HP, Short.valueOf((short) Math.min(val, 30000))));
			} else if ("maxmp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_MAX_MP, Short.valueOf((short) Math.min(val, 30000))));
			} else if ("hp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_HP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("mp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_MP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("fame".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_FAME, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("meso".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.SET_MESO, Integer.valueOf(val)));
			} else {
				resp.printErr("Invalid stat " + stat + ". Valid choices: str, dex,"
						+ "int, luk, ap, sp, level, exp, job, maxhp, maxmp, hp, mp, fame, meso");
				return;
			}
		} else if ("add".equalsIgnoreCase(option)) {
			if ("str".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_STR, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("dex".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_DEX, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("int".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_INT, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("luk".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_LUK, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("ap".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_AP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("sp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_SP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("level".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_LEVEL, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("exp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_EXP, Integer.valueOf(val)));
			} else if ("maxhp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_MAX_HP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("maxmp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_MAX_MP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("hp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_HP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("mp".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_MP, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("fame".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_FAME, Short.valueOf((short) Math.min(val, Short.MAX_VALUE))));
			} else if ("meso".equalsIgnoreCase(stat)) {
				updated = Collections.singletonList(new CommandTarget.CharacterManipulation(CommandTarget.CharacterManipulationKey.ADD_MESO, Integer.valueOf(val)));
			} else {
				resp.printErr("Invalid stat " + stat + ". Valid choices: str, dex,"
						+ "int, luk, ap, sp, level, exp, maxhp, maxmp, hp, mp, fame, meso");
				return;
			}
		} else {
			resp.printErr(getUsage());
			return;
		}
		target.mutate(updated);
	}
}
