/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 *
 */

package blusunrize.immersiveengineering.common.crafting;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import javax.annotation.Nonnull;

public class RecipeCachingReloadListener implements ResourceManagerReloadListener
{
	@Override
	public void onResourceManagerReload(@Nonnull ResourceManager resourceManager)
	{
		// TODO RecipeReloadListener.buildRecipeLists(dataPackRegistries.getRecipeManager());
	}
}
