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

package io.github.moulberry.notenoughupdates.miscgui.minionhelper.loaders;

import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.Minion;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.MinionHelperManager;
import io.github.moulberry.notenoughupdates.util.ItemUtils;
import io.github.moulberry.notenoughupdates.util.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MinionHelperInventoryLoader {
	private final MinionHelperManager manager;
	private final List<String> pagesSeenAlready = new ArrayList<>();
	private boolean dirty = true;

	private int ticks = 0;

	public MinionHelperInventoryLoader(MinionHelperManager manager) {
		this.manager = manager;
	}

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		manager.setLocalPelts(-1);
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (!NotEnoughUpdates.INSTANCE.hasSkyblockScoreboard()) return;
		if (!NotEnoughUpdates.INSTANCE.config.minionHelper.gui) return;
		if (!manager.isReadyToUse()) return;
		ticks++;

		if (ticks % 5 != 0) return;

		if (manager.inCraftedMinionsInventory()) {
			checkInventory();
		} else {
			pagesSeenAlready.clear();
			dirty = true;
		}
	}

	private void checkInventory() {
		Container openContainer = Minecraft.getMinecraft().thePlayer.openContainer;
		if (openContainer instanceof ContainerChest) {
			if (dirty) {
				dirty = false;
				checkNextSlot(openContainer);
				checkLocalPelts();
			}
			loadMinionData(openContainer);
		}
	}

	private void checkLocalPelts() {
		int pelts = -1;
		for (String name : TabListUtils.getTabList()) {
			if (name.startsWith("§r §r§fPelts: ")) {
				name = name.replaceAll(Pattern.quote("§r"), "");
				String rawNumber = name.split("§5")[1];
				pelts = Integer.parseInt(rawNumber);
				break;
			}
		}

		manager.setLocalPelts(pelts);
	}

	private void checkNextSlot(Container openContainer) {
		Slot informationSlot = openContainer.inventorySlots.get(50);
		if (informationSlot.getHasStack()) {
			ItemStack informationStack = informationSlot.getStack();
			for (String line : ItemUtils.getLore(informationStack)) {
				if (line.contains("§aunique")) {
					String[] split = line.split(" ");
					int needForNextSlot = Integer.parseInt(StringUtils.cleanColour(split[1]));
					manager.setNeedForNextSlot(needForNextSlot);
					return;
				}
			}
		}
	}

	private void loadMinionData(Container openContainer) {
		Slot firstSlot = openContainer.inventorySlots.get(10);
		boolean shouldLoad = false;
		if (firstSlot != null) {
			if (firstSlot.getHasStack()) {
				ItemStack stack = firstSlot.getStack();
				String displayName = stack.getDisplayName();
				if (!pagesSeenAlready.contains(displayName)) {
					pagesSeenAlready.add(displayName);
					shouldLoad = true;
				}
			}
		}

		if (!shouldLoad) return;

		int crafted = 0;
		for (Slot slot : openContainer.inventorySlots) {
			if (!slot.getHasStack()) continue;
			ItemStack stack = slot.getStack();
			if (stack == null) continue;
			if (slot.slotNumber != slot.getSlotIndex()) continue;

			String displayName = stack.getDisplayName();
			if (!displayName.contains(" Minion")) continue;

			displayName = StringUtils.cleanColour(displayName);
			int index = 0;
			for (String line : ItemUtils.getLore(stack)) {
				index++;
				if (!line.contains("Tier")) {
					continue;
				}
				if (line.contains("§a")) {
					Minion minion = manager.getMinionByName(displayName, index);
					if (!minion.isCrafted()) {
						minion.setCrafted(true);
						crafted++;
					}
				}
			}
		}
		if (crafted > 0) {
			manager.getOverlay().resetCache();
		}
	}

	public void onProfileSwitch() {
		dirty = true;
	}
}