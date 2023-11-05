/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.api.multiblocks;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler.IMultiblock;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author BluSunrize - 04.07.2017
 */
public class MultiblockAdvancementTrigger implements CriterionTrigger<MultiblockAdvancementTrigger.Instance>
{
	private static final ResourceLocation ID = new ResourceLocation(Lib.MODID, "multiblock_formed");
	public static MultiblockAdvancementTrigger INSTANCE = CriteriaTriggers.register(
			ID.toString(), new MultiblockAdvancementTrigger()
	);
	private final Map<PlayerAdvancements, Listeners> listeners = Maps.newHashMap();

	@Override
	public void addPlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<MultiblockAdvancementTrigger.Instance> listener)
	{
		MultiblockAdvancementTrigger.Listeners listeners = this.listeners.get(playerAdvancements);
		if(listeners==null)
		{
			listeners = new MultiblockAdvancementTrigger.Listeners(playerAdvancements);
			this.listeners.put(playerAdvancements, listeners);
		}
		listeners.add(listener);
	}

	@Override
	public void removePlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<MultiblockAdvancementTrigger.Instance> listener)
	{
		MultiblockAdvancementTrigger.Listeners listeners = this.listeners.get(playerAdvancements);

		if(listeners!=null)
		{
			listeners.remove(listener);
			if(listeners.isEmpty())
				this.listeners.remove(playerAdvancements);
		}
	}

	@Override
	public void removePlayerListeners(PlayerAdvancements playerAdvancements)
	{
		this.listeners.remove(playerAdvancements);
	}

	@Override
	public Instance createInstance(JsonObject json, DeserializationContext context)
	{
		Optional<ContextAwarePredicate> and = EntityPredicate.fromJson(json, "player", context);
		Optional<ItemPredicate> itemPredicate = ItemPredicate.fromJson(json.get("item"));
		if(!itemPredicate.isPresent())
			throw new JsonSyntaxException("Failed to parse item predicate from "+json.get("item"));
		return new MultiblockAdvancementTrigger.Instance(
				new ResourceLocation(GsonHelper.getAsString(json, "multiblock")),
				itemPredicate.get(),
				and
		);
	}

	public void trigger(ServerPlayer player, IMultiblock multiblock, ItemStack hammer)
	{
		MultiblockAdvancementTrigger.Listeners listeners = this.listeners.get(player.getAdvancements());
		if(listeners!=null)
			listeners.trigger(multiblock, hammer);
	}

	public static Criterion<?> create(ResourceLocation multiblock, ItemPredicate hammer)
	{
		return INSTANCE.createCriterion(new Instance(multiblock, hammer, Optional.empty()));
	}

	public static class Instance extends AbstractCriterionTriggerInstance
	{
		private final ResourceLocation multiblock;
		private final ItemPredicate hammer;

		public Instance(ResourceLocation multiblock, ItemPredicate hammer, Optional<ContextAwarePredicate> and)
		{
			super(and);
			this.multiblock = multiblock;
			this.hammer = hammer;
		}

		public boolean test(IMultiblock multiblock, ItemStack hammer)
		{
			return this.multiblock.equals(multiblock.getUniqueName())&&this.hammer.matches(hammer);
		}

		@Override
		public JsonObject serializeToJson()
		{
			JsonObject jsonobject = super.serializeToJson();
			jsonobject.addProperty("multiblock", this.multiblock.toString());
			jsonobject.add("item", this.hammer.serializeToJson());
			return jsonobject;
		}
	}

	static class Listeners
	{
		private final PlayerAdvancements playerAdvancements;
		private final Set<Listener<Instance>> listeners = Sets.newHashSet();

		public Listeners(PlayerAdvancements playerAdvancementsIn)
		{
			this.playerAdvancements = playerAdvancementsIn;
		}

		public boolean isEmpty()
		{
			return this.listeners.isEmpty();
		}

		public void add(CriterionTrigger.Listener<MultiblockAdvancementTrigger.Instance> listener)
		{
			this.listeners.add(listener);
		}

		public void remove(CriterionTrigger.Listener<MultiblockAdvancementTrigger.Instance> listener)
		{
			this.listeners.remove(listener);
		}

		public void trigger(IMultiblock multiblock, ItemStack hammer)
		{
			List<Listener<Instance>> list = null;
			for(CriterionTrigger.Listener<MultiblockAdvancementTrigger.Instance> listener : this.listeners)
				if(listener.trigger().test(multiblock, hammer))
				{
					if(list==null)
						list = Lists.newArrayList();
					list.add(listener);
				}

			if(list!=null)
				for(CriterionTrigger.Listener<MultiblockAdvancementTrigger.Instance> listener1 : list)
					listener1.run(this.playerAdvancements);
		}
	}
}
