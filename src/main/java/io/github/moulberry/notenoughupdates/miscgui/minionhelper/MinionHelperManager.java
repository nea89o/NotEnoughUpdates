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

package io.github.moulberry.notenoughupdates.miscgui.minionhelper;

import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.loaders.MinionHelperApiLoader;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.loaders.MinionHelperRepoLoader;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.requirements.CollectionRequirement;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.requirements.CustomRequirement;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.requirements.MinionRequirement;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.requirements.ReputationRequirement;
import io.github.moulberry.notenoughupdates.miscgui.minionhelper.requirements.SlayerRequirement;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinionHelperManager {
	private static MinionHelperManager instance = null;
	private final Map<String, Minion> minions = new HashMap<>();
	private ApiData apiData = null;
	private boolean shouldNotifyNoCollectionApi = false;

	private final MinionHelperPriceCalculation priceCalculation = new MinionHelperPriceCalculation(this);

	public static MinionHelperManager getInstance() {
		if (instance == null) {
			instance = new MinionHelperManager();
		}
		return instance;
	}

	private MinionHelperManager() {
		MinecraftForge.EVENT_BUS.register(priceCalculation);
	}

	public boolean inCraftedMinionsInventory() {
		if (!NotEnoughUpdates.INSTANCE.isOnSkyblock()) return false;

		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null || minecraft.thePlayer == null) return false;

		Container inventoryContainer = minecraft.thePlayer.openContainer;
		if (!(inventoryContainer instanceof ContainerChest)) return false;
		ContainerChest containerChest = (ContainerChest) inventoryContainer;
		return containerChest.getLowerChestInventory().getDisplayName()
												 .getUnformattedText().equalsIgnoreCase("Crafted Minions");
	}

	public boolean isReadyToUse() {
		return MinionHelperRepoLoader.getInstance().isRepoReadyToUse() &&
			MinionHelperApiLoader.getInstance().isApiReadyToUse();
	}

	public Minion getMinionById(String internalName) {
		if (minions.containsKey(internalName)) {
			return minions.get(internalName);
		} else {
			System.err.println("Cannot get minion for id '" + internalName + "'!");
			return null;
		}
	}

	public Minion getMinionByName(String displayName, int tier) {
		for (Minion minion : minions.values()) {
			if (displayName.equals(minion.getDisplayName())) {
				if (minion.getTier() == tier) {
					return minion;
				}
			}
		}
		System.err.println("Cannot get minion for display name '" + displayName + "'!");
		return null;
	}

	public void createMinion(String internalName, int tier) {
		minions.put(internalName, new Minion(internalName, tier));
	}

	public Map<String, Minion> getAllMinions() {
		return minions;
	}

	public List<MinionRequirement> getRequirements(Minion minion) {
		if (!minion.getRequirements().isEmpty()) {
			return minion.getRequirements();
		}

		Minion parent = minion.getParent();
		if (parent != null) {
			return getRequirements(parent);
		}

		return Collections.emptyList();
	}

	public boolean meetAllRequirements(Minion minion) {
		List<MinionRequirement> list = getRequirements(minion);
		for (MinionRequirement requirement : list) {
			if (!meetRequirement(minion, requirement)) {
				return false;
			}
		}

		return true;
	}

	public boolean meetRequirement(Minion minion, MinionRequirement requirement) {
		if (apiData == null) return false;

		if (requirement instanceof CollectionRequirement) {
			if (apiData.isCollectionApiDisabled()) return true;

			CollectionRequirement collectionRequirement = (CollectionRequirement) requirement;
			String collection = collectionRequirement.getCollection();
			String internalName = formatInternalName(collection);

			int need = collectionRequirement.getLevel();
			Map<String, Integer> highestCollectionTier = apiData.getHighestCollectionTier();
			if (highestCollectionTier.containsKey(internalName)) {
				int has = highestCollectionTier.get(internalName);

				return has >= need;
			}

		} else if (requirement instanceof SlayerRequirement) {
			SlayerRequirement slayerRequirement = (SlayerRequirement) requirement;
			String slayer = slayerRequirement.getSlayer();
			int need = slayerRequirement.getLevel();
			Map<String, Integer> slayerTiers = apiData.getSlayerTiers();
			if (slayerTiers.containsKey(slayer)) {
				return slayerTiers.get(slayer) >= need;
			}

		} else if (requirement instanceof ReputationRequirement) {
			ReputationRequirement reputationRequirement = (ReputationRequirement) requirement;
			int need = reputationRequirement.getReputation();
			String reputationType = reputationRequirement.getReputationType();
			if (reputationType.equals("BARBARIAN")) {
				return apiData.getBarbariansReputation() >= need;
			} else if (reputationType.equals("MAGE")) {
				return apiData.getMagesReputation() >= need;
			} else {
				Utils.addChatMessage("§c[NEU] Minion Helper: Unknown reputation type: '" + reputationType + "'");
				return false;
			}
		} else if (requirement instanceof CustomRequirement) {
			return minion.isCrafted();
		}

		return false;
	}

	public String formatInternalName(String text) {
		return text.toUpperCase().replace(" ", "_");
	}

	public void setApiData(ApiData apiData) {
		this.apiData = apiData;
	}

	public void reloadRequirements() {
		for (Minion minion : minions.values()) {
			minion.setMeetRequirements(meetAllRequirements(minion));
		}
	}

	public boolean isCollectionApiDisabled() {
		return apiData != null && apiData.isCollectionApiDisabled();
	}

	public void setShouldNotifyNoCollectionApi(boolean shouldNotifyNoCollectionApi) {
		this.shouldNotifyNoCollectionApi = shouldNotifyNoCollectionApi;
	}

	public boolean isShouldNotifyNoCollectionApi() {
		return shouldNotifyNoCollectionApi;
	}

	public void handleCommand(String[] args) {
		if (args.length == 2) {
			String parameter = args[1];
			if (parameter.equals("clearminion")) {
				minions.clear();
				Utils.addChatMessage("minion map cleared");
				return;
			}
			if (parameter.equals("reloadrepo")) {
				MinionHelperRepoLoader.getInstance().setDirty();
				Utils.addChatMessage("repo reload requested");
				return;
			}
			if (parameter.equals("reloadapi")) {
				apiData = null;
				MinionHelperApiLoader.getInstance().setDirty();
				Utils.addChatMessage("api reload requested");
				return;
			}
			if (parameter.equals("clearapi")) {
				apiData = null;
				Utils.addChatMessage("api data cleared");
				return;
			}
		}

		Utils.addChatMessage("");
		Utils.addChatMessage("§3NEU Minion Helper commands: §c((for testing only!)");
		Utils.addChatMessage("§6/neudevtest minion clearminion §7Clears the minion map");
		Utils.addChatMessage("§6/neudevtest minion reloadrepo §7Manually loading the data from repo");
		Utils.addChatMessage("§6/neudevtest minion reloadapi §7Manually loading the data from api");
		Utils.addChatMessage("§6/neudevtest minion clearapi §7Clears the api data");
		Utils.addChatMessage("");
	}

	public List<Minion> getChildren(Minion minion) {
		List<Minion> list = new ArrayList<>();
		for (Minion other : minions.values()) {
			if (minion == other.getParent()) {
				list.add(other);
				list.addAll(getChildren(other));
				break;
			}
		}
		return list;
	}

	public void onProfileSwitch() {
		//TODO check if the feature is enabled
		for (Minion minion : minions.values()) {
			minion.setCrafted(false);
			minion.setMeetRequirements(false);
		}
		apiData = null;

		MinionHelperApiLoader.getInstance().onProfileSwitch();
	}

	public void reloadCraftedMinions() {
		if (apiData != null) {
			for (String minion : apiData.getCraftedMinions()) {
				getMinionById(minion).setCrafted(true);
			}
		}
	}

	public MinionHelperPriceCalculation getPriceCalculation() {
		return priceCalculation;
	}
}
