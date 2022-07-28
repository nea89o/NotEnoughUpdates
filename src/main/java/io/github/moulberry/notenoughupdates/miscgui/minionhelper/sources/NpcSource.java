/*
 * Copyright (C) 2022 NotEnoughUpdates contributors
 *
 * This file is part of NotEnoughUpdates.
 *
 * NotEnoughUpdates is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * NotEnoughUpdates is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with NotEnoughUpdates. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.moulberry.notenoughupdates.miscgui.minionhelper.sources;

import com.google.common.collect.ArrayListMultimap;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.Minion;

public class NpcSource extends MinionSource {
	private final String npcName;
	//	name -> amount
	private final ArrayListMultimap<String, Integer> items;
	private int coins = 0;

	public NpcSource(Minion minion, String npcName, int coins, ArrayListMultimap<String, Integer> items) {
		super(minion);
		this.npcName = npcName;
		this.coins = coins;
		this.items = items;
	}

	public String getNpcName() {
		return npcName;
	}

	public ArrayListMultimap<String, Integer> getItems() {
		return items;
	}

	public int getCoins() {
		return coins;
	}
}
