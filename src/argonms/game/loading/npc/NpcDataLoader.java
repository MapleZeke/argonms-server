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

package argonms.game.loading.npc;

import argonms.common.loading.DataFileType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class NpcDataLoader {
	private static NpcDataLoader instance;

	protected final Set<Integer> loaded;
	protected final Map<Integer, NpcStorageKeeper> storageCosts;
	protected final Map<Integer, String> scriptNames;

	protected NpcDataLoader() {
		loaded = new HashSet<>();
		storageCosts = new HashMap<>();
		scriptNames = new HashMap<>();
	}

	protected abstract void load(int npcId);

	public abstract boolean loadAll();

	public NpcStorageKeeper getStorageById(int npcId) {
		if (!loaded.contains(Integer.valueOf(npcId))) {
			load(npcId);
		}
		return storageCosts.get(Integer.valueOf(npcId));
	}

	public String getScriptName(int npcId) {
		if (!loaded.contains(Integer.valueOf(npcId))) {
			load(npcId);
		}
		return scriptNames.get(Integer.valueOf(npcId));
	}

	public static void setInstance(DataFileType wzType, String wzPath) {
		if (instance == null) {
			if (wzType == DataFileType.KVJ) {
				instance = new KvjNpcDataLoader(wzPath);
			} else {
				instance = new DefaultNpcDataLoader();
			}
		}
	}

	public static NpcDataLoader getInstance() {
		return instance;
	}
}
