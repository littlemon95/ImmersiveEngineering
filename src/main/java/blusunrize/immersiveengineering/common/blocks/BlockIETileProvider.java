package blusunrize.immersiveengineering.common.blocks;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.energy.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler;
import blusunrize.immersiveengineering.client.models.IOBJModelCallback;
import blusunrize.immersiveengineering.common.CommonProxy;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.*;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.IEInventoryHandler;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.obj.OBJModel.OBJState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

public abstract class BlockIETileProvider<E extends Enum<E> & BlockIEBase.IBlockEnum> extends BlockIEBase<E> implements ITileEntityProvider
{
	public BlockIETileProvider(String name, Material material, PropertyEnum<E> mainProperty, Class<? extends ItemBlockIEBase> itemBlock, Object... additionalProperties)
	{
		super(name, material, mainProperty, itemBlock, additionalProperties);
	}

	@Override
	public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune)
	{
		return super.getDrops(world, pos, state, fortune);
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state)
	{
		TileEntity tile = world.getTileEntity(pos);
		if(!(tile instanceof ITileDrop) && tile != null && tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null))
		{
			IItemHandler h = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
			if (h instanceof IEInventoryHandler)
				for (int i = 0;i<h.getSlots();i++)
					if (h.getStackInSlot(i)!=null)
					{
						spawnAsEntity(world, pos, h.getStackInSlot(i));
						((IEInventoryHandler) h).setStackInSlot(i, null);
					}
		}
		if(tile instanceof IHasDummyBlocks)
		{
			((IHasDummyBlocks)tile).breakDummies(pos, state);
		}
		if(tile instanceof IImmersiveConnectable && world.getGameRules().getBoolean("doTileDrops"))
		{
			if(world!=null&&(!world.isRemote||!Minecraft.getMinecraft().isSingleplayer()))
				ImmersiveNetHandler.INSTANCE.clearAllConnectionsFor(Utils.toCC(tile),world, !world.isRemote);
		}
		super.breakBlock(world, pos, state);
		world.removeTileEntity(pos);
	}

	@Override
	public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, TileEntity tile, ItemStack stack)
	{
		if(tile instanceof ITileDrop)
		{
			ItemStack s = ((ITileDrop)tile).getTileDrop(player, state);
			if(s!=null)
			{
				spawnAsEntity(world, pos, s);
				return;
			}
		}
		super.harvestBlock(world, player, pos, state, tile, stack);
	}
	@Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player)
	{
		TileEntity tile = world.getTileEntity(pos);
		if(tile instanceof ITileDrop)
		{
			ItemStack s = ((ITileDrop)tile).getTileDrop(player, world.getBlockState(pos));
			if(s!=null)
				return s;
		}
		Item item = Item.getItemFromBlock(this);
		return item == null ? null : new ItemStack(item, 1, this.damageDropped(world.getBlockState(pos)));
	}


	@Override
	public boolean eventReceived(IBlockState state, World worldIn, BlockPos pos, int eventID, int eventParam)
	{
		super.eventReceived(state, worldIn, pos, eventID, eventParam);
		TileEntity tileentity = worldIn.getTileEntity(pos);
		return tileentity != null && tileentity.receiveClientEvent(eventID, eventParam);
	}

	@Override
	public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		state = super.getActualState(state, world, pos);
		TileEntity tile = world.getTileEntity(pos);

		if(tile instanceof IAttachedIntegerProperies)
		{
			for(String s : ((IAttachedIntegerProperies)tile).getIntPropertyNames())
				state = applyProperty(state, ((IAttachedIntegerProperies)tile).getIntProperty(s),  ((IAttachedIntegerProperies)tile).getIntPropertyValue(s));
		}

		if(tile instanceof IDirectionalTile)
		{
			PropertyDirection prop = state.getPropertyNames().contains(IEProperties.FACING_HORIZONTAL)?IEProperties.FACING_HORIZONTAL: state.getPropertyNames().contains(IEProperties.FACING_VERTICAL)?IEProperties.FACING_VERTICAL: IEProperties.FACING_ALL;
			state = applyProperty(state, prop, ((IDirectionalTile)tile).getFacing());
		}
		else if(state.getPropertyNames().contains(IEProperties.FACING_HORIZONTAL))
			state = state.withProperty(IEProperties.FACING_HORIZONTAL, EnumFacing.NORTH);
		else if(state.getPropertyNames().contains(IEProperties.FACING_ALL))
			state = state.withProperty(IEProperties.FACING_ALL, EnumFacing.NORTH);

		if(tile instanceof IConfigurableSides)
			for(int i=0; i<6; i++)
				if(state.getPropertyNames().contains(IEProperties.SIDECONFIG[i]))
					state = state.withProperty(IEProperties.SIDECONFIG[i], ((IConfigurableSides)tile).getSideConfig(i));

		if(tile instanceof IActiveState)
			state = applyProperty(state, ((IActiveState)tile).getBoolProperty(IActiveState.class), ((IActiveState)tile).getIsActive());

		if(tile instanceof IDualState)
			state = applyProperty(state, ((IDualState)tile).getBoolProperty(IDualState.class), ((IDualState)tile).getIsSecondState());

		if(tile instanceof TileEntityMultiblockPart)
			state = applyProperty(state, IEProperties.MULTIBLOCKSLAVE, ((TileEntityMultiblockPart)tile).isDummy());
		else if(tile instanceof IHasDummyBlocks)
			state = applyProperty(state, IEProperties.MULTIBLOCKSLAVE, ((IHasDummyBlocks)tile).isDummy());

		if(tile instanceof IMirrorAble)
			state = applyProperty(state, ((IMirrorAble)tile).getBoolProperty(IMirrorAble.class), ((IMirrorAble)tile).getIsMirrored());

		return state;
	}

	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		state = super.getExtendedState(state, world, pos);
		if(state instanceof IExtendedBlockState)
		{
			TileEntity te = world.getTileEntity(pos);
			if(te instanceof IAdvancedHasObjProperty)
				state = ((IExtendedBlockState)state).withProperty(Properties.AnimationProperty, ((IAdvancedHasObjProperty)te).getOBJState());
			else if(te instanceof IHasObjProperty)
				state = ((IExtendedBlockState)state).withProperty(Properties.AnimationProperty, new OBJState(((IHasObjProperty)te).compileDisplayList(), true));
			if(te instanceof IDynamicTexture)
				state = ((IExtendedBlockState)state).withProperty(IEProperties.OBJ_TEXTURE_REMAP, ((IDynamicTexture)te).getTextureReplacements());
			if(te instanceof IOBJModelCallback)
				state = ((IExtendedBlockState)state).withProperty(IEProperties.OBJ_MODEL_CALLBACK, (IOBJModelCallback)te);
		}

		return state;
	}

	@Override
	public void onIEBlockPlacedBy(World world, BlockPos pos, IBlockState state, EnumFacing side, float hitX, float hitY, float hitZ, EntityLivingBase placer, ItemStack stack)
	{
		TileEntity tile = world.getTileEntity(pos);

		if(tile instanceof IDirectionalTile)
		{
			EnumFacing f = EnumFacing.DOWN;
			int limit = ((IDirectionalTile)tile).getFacingLimitation();

			if(limit==0)
				f = side;
			else if(limit==1)
				f = BlockPistonBase.getFacingFromEntity(pos, placer);
			else if(limit==2)
				f = EnumFacing.fromAngle(placer.rotationYaw);
			else if(limit==3)
				f = (side!=EnumFacing.DOWN&&(side==EnumFacing.UP||hitY<=.5))?EnumFacing.UP : EnumFacing.DOWN;
			else if(limit==4)
			{
				f = EnumFacing.fromAngle(placer.rotationYaw);
				if(f==EnumFacing.SOUTH || f==EnumFacing.WEST)
					f = f.getOpposite();
			}
			else
				f = EnumFacing.getFront(limit-5);
			((IDirectionalTile)tile).setFacing( ((IDirectionalTile)tile).mirrorFacingOnPlacement(placer)?f.getOpposite():f );
			if(tile instanceof IAdvancedDirectionalTile)
				((IAdvancedDirectionalTile)tile).onDirectionalPlacement(side, hitX, hitY, hitZ, placer);
		}
		if(tile instanceof IHasDummyBlocks)
		{
			((IHasDummyBlocks)tile).placeDummies(pos, state, side, hitX, hitY, hitZ);
		}
		if(tile instanceof ITileDrop)
		{
			((ITileDrop)tile).readOnPlacement(placer, stack);
		}
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ)
	{
		if(hand!=EnumHand.MAIN_HAND)
			return false;
		TileEntity tile = world.getTileEntity(pos);

		if(tile instanceof IConfigurableSides && Utils.isHammer(heldItem) && !world.isRemote)
		{
			int iSide = player.isSneaking()?side.getOpposite().ordinal():side.ordinal();
			if(!world.isRemote&&((IConfigurableSides)tile).toggleSide(iSide, player))
				return true;
		}
		if(tile instanceof IDirectionalTile && Utils.isHammer(heldItem) && ((IDirectionalTile)tile).canHammerRotate(side, hitX, hitY, hitZ, player) && !world.isRemote)
		{
			EnumFacing f = ((IDirectionalTile)tile).getFacing();
			int limit = ((IDirectionalTile)tile).getFacingLimitation();

			if(limit==0)
				f = player.isSneaking()?side.getOpposite():side;
			else if(limit==1)
				f = player.isSneaking()?f.rotateAround(side.getAxis()).getOpposite():f.rotateAround(side.getAxis());
			else if(limit==2)
				f = player.isSneaking()?f.rotateYCCW():f.rotateY();
			((IDirectionalTile)tile).setFacing(f);
			tile.markDirty();
			world.notifyBlockUpdate(pos,state,state,3);
			world.addBlockEvent(tile.getPos(), tile.getBlockType(), 255, 0);
			return true;
		}
		if(tile instanceof IHammerInteraction && Utils.isHammer(heldItem))
		{
			boolean b = ((IHammerInteraction)tile).hammerUseSide(side, player, hitX, hitY, hitZ);
			if(b)
				return b;
		}
		if(tile instanceof IPlayerInteraction)
		{
			boolean b = ((IPlayerInteraction)tile).interact(side, player, hand, heldItem, hitX, hitY, hitZ);
			if(b)
				return b;
		}
		if(tile instanceof IGuiTile && !player.isSneaking() && ((IGuiTile)tile).canOpenGui())
		{
			TileEntity master = ((IGuiTile)tile).getGuiMaster();
			if(!world.isRemote)
				CommonProxy.openGuiForTile(player,(TileEntity & IGuiTile)master);
			return true;
		}
		return false;
	}

	@Override
	public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbour)
	{
		TileEntity tile = world.getTileEntity(pos);
		if(tile instanceof INeighbourChangeTile && !tile.getWorld().isRemote)
			((INeighbourChangeTile)tile).onNeighborBlockChange(pos, neighbour);
	}

	@Override
	public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof ILightValue)
			return ((ILightValue)te).getLightValue();
		return 0;
	}

//	@Override
//	public int colorMultiplier(IBlockAccess world, BlockPos pos, int renderPass)
//	{
//		super.recolorBlock()
//		TileEntity te = world.getTileEntity(pos);
//		if(te instanceof IColouredTile)
//			((IColouredTile)te).getRenderColour();
//		return 16777215;
//	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		if(world.getBlockState(pos).getBlock()!=this)
			return FULL_BLOCK_AABB;
		else
		{
			TileEntity te = world.getTileEntity(pos);
			if(te instanceof IBlockBounds)
			{
				float[] bounds = ((IBlockBounds)te).getBlockBounds();
				if(bounds!=null)
					return new AxisAlignedBB(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]);
			}
		}
		return super.getBoundingBox(state, world, pos);
	}
	@Override
	public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB mask, List<AxisAlignedBB> list, Entity ent)
	{
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof IAdvancedCollisionBounds)
		{
			List<AxisAlignedBB> bounds = ((IAdvancedCollisionBounds)te).getAdvancedColisionBounds();
			if(bounds!=null && !bounds.isEmpty())
			{
				for(AxisAlignedBB aabb : bounds)
					if(aabb!=null && mask.intersectsWith(aabb))
						list.add(aabb);
				return;
			}
		}
		super.addCollisionBoxToList(state, world, pos, mask, list, ent);
	}
	@Override
	public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end)
	{
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof IAdvancedSelectionBounds)
		{
			List<AxisAlignedBB> list = ((IAdvancedSelectionBounds)te).getAdvancedSelectionBounds();
			if(list!=null && !list.isEmpty())
			{
				for(AxisAlignedBB aabb : list)
				{
					RayTraceResult mop = this.rayTrace(pos, start, end, aabb.offset(-pos.getX(),-pos.getY(),-pos.getZ()));
					if(mop!=null)
						return mop;
				}
				return null;
			}
		}
		return super.collisionRayTrace(state, world, pos, start, end);
	}
//	public RayTraceResult doRaytrace(World world, BlockPos pos, Vec3d start, Vec3d end)
//	{
//		start = start.addVector((double)(-pos.getX()), (double)(-pos.getY()), (double)(-pos.getZ()));
//		end = end.addVector((double)(-pos.getX()), (double)(-pos.getY()), (double)(-pos.getZ()));
//		Vec3d vec3 = start.getIntermediateWithXValue(end, this.minX);
//		Vec3d vec31 = start.getIntermediateWithXValue(end, this.maxX);
//		Vec3d vec32 = start.getIntermediateWithYValue(end, this.minY);
//		Vec3d vec33 = start.getIntermediateWithYValue(end, this.maxY);
//		Vec3d vec34 = start.getIntermediateWithZValue(end, this.minZ);
//		Vec3d vec35 = start.getIntermediateWithZValue(end, this.maxZ);
//
//		if(!this.isVecInsideYZBounds(vec3))
//			vec3 = null;
//		if(!this.isVecInsideYZBounds(vec31))
//			vec31 = null;
//		if(!this.isVecInsideXZBounds(vec32))
//			vec32 = null;
//		if(!this.isVecInsideXZBounds(vec33))
//			vec33 = null;
//		if(!this.isVecInsideXYBounds(vec34))
//			vec34 = null;
//		if(!this.isVecInsideXYBounds(vec35))
//			vec35 = null;
//
//		Vec3d vec36 = null;
//
//		if(vec3 != null && (vec36 == null || start.squareDistanceTo(vec3) < start.squareDistanceTo(vec36)))
//			vec36 = vec3;
//		if(vec31 != null && (vec36 == null || start.squareDistanceTo(vec31) < start.squareDistanceTo(vec36)))
//			vec36 = vec31;
//		if(vec32 != null && (vec36 == null || start.squareDistanceTo(vec32) < start.squareDistanceTo(vec36)))
//			vec36 = vec32;
//		if(vec33 != null && (vec36 == null || start.squareDistanceTo(vec33) < start.squareDistanceTo(vec36)))
//			vec36 = vec33;
//		if(vec34 != null && (vec36 == null || start.squareDistanceTo(vec34) < start.squareDistanceTo(vec36)))
//			vec36 = vec34;
//		if(vec35 != null && (vec36 == null || start.squareDistanceTo(vec35) < start.squareDistanceTo(vec36)))
//			vec36 = vec35;
//
//		if (vec36 == null)
//			return null;
//		else
//		{
//			EnumFacing enumfacing = null;
//			if(vec36 == vec3)
//				enumfacing = EnumFacing.WEST;
//			if(vec36 == vec31)
//				enumfacing = EnumFacing.EAST;
//			if(vec36 == vec32)
//				enumfacing = EnumFacing.DOWN;
//			if(vec36 == vec33)
//				enumfacing = EnumFacing.UP;
//			if(vec36 == vec34)
//				enumfacing = EnumFacing.NORTH;
//			if(vec36 == vec35)
//				enumfacing = EnumFacing.SOUTH;
//			return new RayTraceResult(vec36.addVector((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()), enumfacing, pos);
//		}
//	}
//	protected boolean isVecInsideYZBounds(Vec3d point)
//	{
//		return point != null && (point.yCoord >= this.minY && point.yCoord <= this.maxY && point.zCoord >= this.minZ && point.zCoord <= this.maxZ);
//	}
//	protected boolean isVecInsideXZBounds(Vec3d point)
//	{
//		return point != null && (point.xCoord >= this.minX && point.xCoord <= this.maxX && point.zCoord >= this.minZ && point.zCoord <= this.maxZ);
//	}
//	protected boolean isVecInsideXYBounds(Vec3d point)
//	{
//		return point != null && (point.xCoord >= this.minX && point.xCoord <= this.maxX && point.yCoord >= this.minY && point.yCoord <= this.maxY);
//	}


	@Override
	public boolean hasComparatorInputOverride(IBlockState state)
	{
		return true;
	}
	@Override
	public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos)
	{
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof IEBlockInterfaces.IComparatorOverride)
			return ((IEBlockInterfaces.IComparatorOverride)te).getComparatorInputOverride();
		return 0;
	}

	@Override
	public void onEntityCollidedWithBlock(World world, BlockPos pos, IBlockState state, Entity entity)
	{
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof TileEntityIEBase)
			((TileEntityIEBase)te).onEntityCollision(world, entity);
	}
}