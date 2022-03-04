/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.crafting.serializers;

import blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.IERecipeSerializer;
import blusunrize.immersiveengineering.api.crafting.IngredientWithSize;
import blusunrize.immersiveengineering.api.crafting.StackWithChance;
import blusunrize.immersiveengineering.common.config.IEServerConfig;
import blusunrize.immersiveengineering.common.crafting.ArcRecyclingRecipe;
import blusunrize.immersiveengineering.common.network.PacketUtils;
import blusunrize.immersiveengineering.common.register.IEBlocks.Multiblocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;

public class ArcFurnaceRecipeSerializer extends IERecipeSerializer<ArcFurnaceRecipe>
{
	@Override
	public ItemStack getIcon()
	{
		return new ItemStack(Multiblocks.ARC_FURNACE);
	}

	@Override
	public ArcFurnaceRecipe readFromJson(ResourceLocation recipeId, JsonObject json)
	{
		JsonArray results = json.getAsJsonArray("results");
		NonNullList<ItemStack> outputs = NonNullList.withSize(results.size(), ItemStack.EMPTY);
		for(int i = 0; i < results.size(); i++)
			outputs.set(i, readOutput(results.get(i)));

		IngredientWithSize input = IngredientWithSize.deserialize(json.get("input"));

		JsonArray additives = json.getAsJsonArray("additives");
		IngredientWithSize[] ingredients = new IngredientWithSize[additives.size()];
		for(int i = 0; i < additives.size(); i++)
			ingredients[i] = IngredientWithSize.deserialize(additives.get(i));

		ItemStack slag = ItemStack.EMPTY;
		if(json.has("slag"))
			slag = readOutput(json.get("slag"));

		int time = GsonHelper.getAsInt(json, "time");
		int energy = GsonHelper.getAsInt(json, "energy");
		JsonArray array = json.getAsJsonArray("secondaries");
		List<StackWithChance> secondaries = new ArrayList<>();
		if(array!=null)
			for(int i = 0; i < array.size(); i++)
			{
				StackWithChance secondary = readConditionalStackWithChance(array.get(i));
				if(secondary!=null)
					secondaries.add(secondary);
			}
		return IEServerConfig.MACHINES.arcFurnaceConfig.apply(
				new ArcFurnaceRecipe(recipeId, outputs, slag, secondaries, time, energy, input, ingredients)
		);
	}

	@Nullable
	@Override
	public ArcFurnaceRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer)
	{
		List<ItemStack> outputNullable = PacketUtils.readList(buffer, FriendlyByteBuf::readItem);
		NonNullList<ItemStack> outputs = NonNullList.of(ItemStack.EMPTY, outputNullable.toArray(ItemStack[]::new));
		List<StackWithChance> secondaries = PacketUtils.readList(buffer, StackWithChance::read);
		IngredientWithSize input = IngredientWithSize.read(buffer);
		IngredientWithSize[] additives = PacketUtils.readList(buffer, IngredientWithSize::read)
				.toArray(new IngredientWithSize[0]);
		ItemStack slag = buffer.readItem();
		int time = buffer.readInt();
		int energy = buffer.readInt();
		if(!buffer.readBoolean())
			return new ArcFurnaceRecipe(recipeId, outputs, slag, secondaries, time, energy, input, additives);
		else
		{
			final int numOutputs = buffer.readVarInt();
			Map<ItemStack, Double> recyclingOutputs = new HashMap<>(numOutputs);
			for(int i = 0; i < numOutputs; ++i)
				recyclingOutputs.put(buffer.readItem(), buffer.readDouble());
			return new ArcRecyclingRecipe(
					recipeId, () -> Minecraft.getInstance().getConnection().registryAccess(), recyclingOutputs, input, time, energy
			);
		}
	}

	@Override
	public void toNetwork(FriendlyByteBuf buffer, ArcFurnaceRecipe recipe)
	{
		PacketUtils.writeListReverse(buffer, recipe.output, FriendlyByteBuf::writeItem);
		PacketUtils.writeList(buffer, recipe.secondaryOutputs, StackWithChance::write);
		recipe.input.write(buffer);
		PacketUtils.writeList(buffer, Arrays.asList(recipe.additives), IngredientWithSize::write);
		buffer.writeItem(recipe.slag);
		buffer.writeInt(recipe.getTotalProcessTime());
		buffer.writeInt(recipe.getTotalProcessEnergy());
		buffer.writeBoolean(recipe instanceof ArcRecyclingRecipe);
		if(recipe instanceof ArcRecyclingRecipe recyclingRecipe)
		{
			Map<ItemStack, Double> outputs = recyclingRecipe.getOutputs();
			buffer.writeVarInt(outputs.size());
			for(Entry<ItemStack, Double> e : outputs.entrySet())
			{
				buffer.writeItem(e.getKey());
				buffer.writeDouble(e.getValue());
			}
		}
	}
}
