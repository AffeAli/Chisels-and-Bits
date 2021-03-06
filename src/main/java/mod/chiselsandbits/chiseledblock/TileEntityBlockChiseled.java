package mod.chiselsandbits.chiseledblock;

import java.util.Collection;
import java.util.Collections;

import mod.chiselsandbits.api.EventFullBlockRestoration;
import mod.chiselsandbits.api.ItemType;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob.BlobStats;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.chiseledblock.data.VoxelNeighborRenderTracker;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.api.BitAccess;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.integration.mcmultipart.MCMultipartProxy;
import mod.chiselsandbits.interfaces.IChiseledTileContainer;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockSmartModel;
import mod.chiselsandbits.render.chiseledblock.tesr.ChisledBlockRenderChunkTESR;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileEntityBlockChiseled extends TileEntity implements IChiseledTileContainer
{

	private IExtendedBlockState state;
	public IChiseledTileContainer occlusionState;

	boolean isNormalCube = false;
	int sideState = 0;
	int lightlevel = 0;

	public TileEntityBlockChiseled()
	{

	}

	public IChiseledTileContainer getTileContainer()
	{
		if ( occlusionState != null )
		{
			return occlusionState;
		}

		return this;
	}

	@Override
	public boolean isBlobOccluded(
			final VoxelBlob blob )
	{
		return false;
	}

	@Override
	public void saveData()
	{
		super.markDirty();
	}

	@Override
	public void sendUpdate()
	{
		ModUtil.sendUpdate( worldObj, pos );
	}

	public void copyFrom(
			final TileEntityBlockChiseled src )
	{
		state = src.state;
		isNormalCube = src.isNormalCube;
		sideState = src.sideState;
		lightlevel = src.lightlevel;
	}

	public IExtendedBlockState getBasicState()
	{
		return getState( false, 0 );
	}

	public IExtendedBlockState getRenderState()
	{
		return getState( true, 1 );
	}

	protected IExtendedBlockState getState(
			final boolean updateNeightbors,
			final int updateCost )
	{
		if ( state == null )
		{
			return (IExtendedBlockState) ChiselsAndBits.getBlocks().getChiseledDefaultState();
		}

		if ( updateNeightbors )
		{
			final boolean isDyanmic = this instanceof TileEntityBlockChiseledTESR;

			final VoxelNeighborRenderTracker vns = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );
			if ( vns == null )
			{
				return state;
			}

			vns.update( isDyanmic, worldObj, pos );

			tesrUpdate( vns );

			final TileEntityBlockChiseled self = this;
			if ( vns.isAboveLimit() && !isDyanmic )
			{
				ChisledBlockRenderChunkTESR.addNextFrameTask( new Runnable() {

					@Override
					public void run()
					{
						if ( self.worldObj != null && self.pos != null )
						{
							final TileEntity current = self.worldObj.getTileEntity( self.pos );
							final TileEntityBlockChiseled dat = MCMultipartProxy.proxyMCMultiPart.getChiseledTileEntity( self.worldObj, self.pos, false );

							if ( current == null || self.isInvalid() )
							{
								return;
							}

							if ( current == self )
							{
								current.invalidate();
								final TileEntityBlockChiseledTESR TESR = new TileEntityBlockChiseledTESR();
								TESR.copyFrom( self );
								self.worldObj.removeTileEntity( self.pos );
								self.worldObj.setTileEntity( self.pos, TESR );
								self.worldObj.markBlockRangeForRenderUpdate( self.pos, self.pos );
								vns.unlockDynamic();
							}
							else if ( dat == self )
							{
								current.invalidate();
								MCMultipartProxy.proxyMCMultiPart.convertTo( current, new TileEntityBlockChiseledTESR() );
								vns.unlockDynamic();
							}
						}
					}

				} );
			}
			else if ( !vns.isAboveLimit() && isDyanmic )
			{
				ChisledBlockRenderChunkTESR.addNextFrameTask( new Runnable() {

					@Override
					public void run()
					{
						if ( self.worldObj != null && self.pos != null )
						{
							final TileEntity current = self.worldObj.getTileEntity( self.pos );
							final TileEntityBlockChiseled dat = MCMultipartProxy.proxyMCMultiPart.getChiseledTileEntity( self.worldObj, self.pos, false );

							if ( current == null || self.isInvalid() )
							{
								return;
							}

							if ( current == self )
							{
								current.invalidate();
								final TileEntityBlockChiseled nonTesr = new TileEntityBlockChiseled();
								nonTesr.copyFrom( self );
								self.worldObj.removeTileEntity( self.pos );
								self.worldObj.setTileEntity( self.pos, nonTesr );
								self.worldObj.markBlockRangeForRenderUpdate( self.pos, self.pos );
								vns.unlockDynamic();
							}
							else if ( dat == self )
							{
								current.invalidate();
								MCMultipartProxy.proxyMCMultiPart.convertTo( current, new TileEntityBlockChiseled() );
								vns.unlockDynamic();
							}
						}
					}

				} );
			}
		}

		return state;
	}

	protected void tesrUpdate(
			final VoxelNeighborRenderTracker vns )
	{

	}

	public BlockBitInfo getBlockInfo(
			final Block alternative )
	{
		return BlockBitInfo.getBlockInfo( getBlockState( alternative ) );
	}

	public IBlockState getBlockState(
			final Block alternative )
	{
		final Integer stateID = getBasicState().getValue( BlockChiseled.UProperty_Primary_BlockState );

		if ( stateID != null )
		{
			final IBlockState state = Block.getStateById( stateID );
			if ( state != null )
			{
				return state;
			}
		}

		return alternative.getDefaultState();
	}

	public void setState(
			final IExtendedBlockState state )
	{
		this.state = state;
	}

	@Override
	public boolean shouldRefresh(
			final World world,
			final BlockPos pos,
			final IBlockState oldState,
			final IBlockState newState )
	{
		return oldState.getBlock() != newState.getBlock();
	}

	@Override
	public SPacketUpdateTileEntity getUpdatePacket()
	{
		final NBTTagCompound nbttagcompound = new NBTTagCompound();
		writeChisleData( nbttagcompound );

		if ( nbttagcompound.hasNoTags() )
		{
			return null;
		}

		return new SPacketUpdateTileEntity( pos, 255, nbttagcompound );
	}

	@Override
	public NBTTagCompound getUpdateTag()
	{
		final NBTTagCompound nbttagcompound = new NBTTagCompound();
		writeToNBT( nbttagcompound );
		return nbttagcompound;
	}

	@Override
	public void onDataPacket(
			final NetworkManager net,
			final SPacketUpdateTileEntity pkt )
	{
		final boolean changed = readChisleData( pkt.getNbtCompound() );

		if ( worldObj != null && changed )
		{
			worldObj.markBlockRangeForRenderUpdate( pos, pos );
			triggerDynamicUpdates();
		}
	}

	/**
	 * look at near by TESRs and re-render them.
	 */
	private void triggerDynamicUpdates()
	{
		if ( worldObj.isRemote && state != null )
		{
			final VoxelNeighborRenderTracker vns = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );

			// will it update anyway?
			if ( vns != null && vns.isDynamic() )
			{
				return;
			}

			for ( final EnumFacing f : EnumFacing.VALUES )
			{
				final BlockPos p = getPos().offset( f );
				if ( worldObj.isBlockLoaded( p ) )
				{
					final TileEntity te = worldObj.getTileEntity( p );
					if ( te instanceof TileEntityBlockChiseledTESR )
					{
						final TileEntityBlockChiseledTESR tesr = (TileEntityBlockChiseledTESR) te;

						if ( tesr.getRenderChunk() != null )
						{
							tesr.getRenderChunk().rebuild( false );
						}
					}
				}
			}
		}
	}

	public boolean readChisleData(
			final NBTTagCompound tag )
	{
		final boolean changed = new NBTBlobConverter( false, this ).readChisleData( tag );

		final VoxelNeighborRenderTracker vns = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );

		if ( vns != null )
		{
			vns.triggerUpdate();
		}

		return changed;
	}

	public void writeChisleData(
			final NBTTagCompound tag )
	{
		new NBTBlobConverter( false, this ).writeChisleData( tag, false );
	}

	@Override
	public NBTTagCompound writeToNBT(
			final NBTTagCompound compound )
	{
		super.writeToNBT( compound );
		writeChisleData( compound );
		return compound;
	}

	@Override
	public void readFromNBT(
			final NBTTagCompound compound )
	{
		super.readFromNBT( compound );
		readChisleData( compound );
	}

	public void fillWith(
			final IBlockState blockType )
	{
		final int ref = Block.getStateId( blockType );

		sideState = 0xff;
		lightlevel = DeprecationHelper.getLightValue( blockType );
		isNormalCube = ModUtil.isNormalCube( blockType );

		IExtendedBlockState state = getBasicState()
				.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( Block.getStateId( blockType ), getPositionRandom( pos ) ) );

		final VoxelNeighborRenderTracker tracker = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );

		if ( tracker == null )
		{
			state = state.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() );
		}
		else
		{
			tracker.isDynamic();
		}

		// required for placing bits
		if ( ref != 0 )
		{
			state = state.withProperty( BlockChiseled.UProperty_Primary_BlockState, ref );
		}

		setState( state );

		getTileContainer().saveData();
	}

	private long getPositionRandom(
			final BlockPos pos )
	{
		if ( pos != null && FMLCommonHandler.instance().getSide() == Side.CLIENT )
		{
			return MathHelper.getPositionRandom( pos );
		}

		return 0;
	}

	public VoxelBlobStateReference getBlobStateReference()
	{
		return getBasicState().getValue( BlockChiseled.UProperty_VoxelBlob );
	}

	public VoxelBlob getBlob()
	{
		VoxelBlob vb = null;
		final VoxelBlobStateReference vbs = getBlobStateReference();

		if ( vbs != null )
		{
			vb = vbs.getVoxelBlob();

			if ( vb == null )
			{
				vb = new VoxelBlob();
				vb.fill( Block.getStateId( Blocks.COBBLESTONE.getDefaultState() ) );
			}
		}
		else
		{
			vb = new VoxelBlob();
		}

		return vb;
	}

	public IBlockState getPreferedBlock()
	{
		return ChiselsAndBits.getBlocks().getConversionWithDefault( getBlockState( Blocks.STONE ) ).getDefaultState();
	}

	public void setBlob(
			final VoxelBlob vb )
	{
		setBlob( vb, true );
	}

	public boolean updateBlob(
			final NBTBlobConverter converter,
			final boolean triggerUpdates )
	{
		final int oldLV = getLightValue();
		final boolean oldNC = isNormalCube();
		final int oldSides = sideState;

		final VoxelBlobStateReference originalRef = getBasicState().getValue( BlockChiseled.UProperty_VoxelBlob );

		sideState = converter.getSideState();
		final int b = converter.getPrimaryBlockStateID();
		lightlevel = converter.getLightValue();
		isNormalCube = converter.isNormalCube();
		final VoxelBlobStateReference voxelRef = converter.getVoxelRef( VoxelBlob.VERSION_COMPACT, getPositionRandom( pos ) );

		IExtendedBlockState newstate = getBasicState()
				.withProperty( BlockChiseled.UProperty_Primary_BlockState, b )
				.withProperty( BlockChiseled.UProperty_VoxelBlob, voxelRef );

		final VoxelNeighborRenderTracker tracker = newstate.getValue( BlockChiseled.UProperty_VoxelNeighborState );

		if ( tracker == null )
		{
			newstate = newstate.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() );
		}
		else
		{
			tracker.isDynamic();
		}

		setState( newstate );

		if ( hasWorldObj() && triggerUpdates )
		{
			if ( oldLV != getLightValue() || oldNC != isNormalCube() )
			{
				worldObj.checkLight( pos );
			}

			if ( oldSides != sideState )
			{
				worldObj.notifyNeighborsOfStateChange( pos, worldObj.getBlockState( pos ).getBlock() );
			}
		}

		return voxelRef != null ? !voxelRef.equals( originalRef ) : true;
	}

	public void setBlob(
			final VoxelBlob vb,
			final boolean triggerUpdates )
	{
		final Integer olv = getLightValue();
		final Boolean oldNC = isNormalCube();

		final BlobStats common = vb.getVoxelStats();
		final float light = common.blockLight;
		final boolean nc = common.isNormalBlock;
		final int lv = Math.max( 0, Math.min( 15, (int) ( light * 15 ) ) );

		// are most of the bits in the center solid?
		final int sideFlags = vb.getSideFlags( 5, 11, 4 * 4 );

		if ( worldObj == null )
		{
			if ( common.mostCommonState == 0 )
			{
				common.mostCommonState = getBasicState().getValue( BlockChiseled.UProperty_Primary_BlockState );
			}

			sideState = sideFlags;
			lightlevel = lv;
			isNormalCube = nc;

			IExtendedBlockState newState = getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( vb.blobToBytes( VoxelBlob.VERSION_COMPACT ), getPositionRandom( pos ) ) )
					.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() )
					.withProperty( BlockChiseled.UProperty_Primary_BlockState, common.mostCommonState );

			final VoxelNeighborRenderTracker tracker = newState.getValue( BlockChiseled.UProperty_VoxelNeighborState );

			if ( tracker == null )
			{
				newState = newState.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() );
			}
			else
			{
				tracker.isDynamic();
			}

			setState( newState );
			return;
		}

		if ( common.isFullBlock )
		{
			setState( getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( common.mostCommonState, getPositionRandom( pos ) ) ) );

			final IBlockState newState = Block.getStateById( common.mostCommonState );
			if ( !MinecraftForge.EVENT_BUS.post( new EventFullBlockRestoration( worldObj, pos, newState ) ) )
			{
				worldObj.setBlockState( pos, newState, triggerUpdates ? 3 : 0 );
			}
		}
		else if ( common.mostCommonState != 0 )
		{
			sideState = sideFlags;
			lightlevel = lv;
			isNormalCube = nc;

			setState( getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( vb.blobToBytes( VoxelBlob.VERSION_COMPACT ), getPositionRandom( pos ) ) )
					.withProperty( BlockChiseled.UProperty_Primary_BlockState, common.mostCommonState ) );

			getTileContainer().saveData();
			getTileContainer().sendUpdate();

			// since its possible for bits to occlude parts.. update every time.
			final Block blk = worldObj.getBlockState( pos ).getBlock();
			MCMultipartProxy.proxyMCMultiPart.triggerPartChange( worldObj.getTileEntity( pos ) );
			worldObj.notifyBlockOfStateChange( pos, blk );

			if ( triggerUpdates )
			{
				worldObj.notifyNeighborsOfStateChange( pos, blk );
			}
		}
		else
		{
			setState( getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( 0, getPositionRandom( pos ) ) ) );

			ModUtil.removeChisledBlock( worldObj, pos );
		}

		if ( olv != lv || oldNC != nc )
		{
			worldObj.checkLight( pos );
		}
	}

	static private class ItemStackGeneratedCache
	{
		public ItemStackGeneratedCache(
				final ItemStack itemstack,
				final VoxelBlobStateReference blobStateReference,
				final int rotations2 )
		{
			out = itemstack == null ? null : itemstack.copy();
			ref = blobStateReference;
			rotations = rotations2;
		}

		final ItemStack out;
		final VoxelBlobStateReference ref;
		final int rotations;
	};

	/**
	 * prevent mods that constantly ask for pick block from killing the
	 * client... ( looking at you waila )
	 **/
	private ItemStackGeneratedCache pickcache = null;

	public ItemStack getItemStack(
			final EntityPlayer player )
	{
		final ItemStackGeneratedCache cache = pickcache;

		if ( player != null )
		{
			EnumFacing enumfacing = ModUtil.getPlaceFace( player );
			final int rotations = ModUtil.getRotationIndex( enumfacing );

			if ( cache != null && cache.rotations == rotations && cache.ref == getBlobStateReference() && cache.out != null )
			{
				return cache.out.copy();
			}

			VoxelBlob vb = getBlob();

			int countDown = rotations;
			while ( countDown > 0 )
			{
				countDown--;
				enumfacing = enumfacing.rotateYCCW();
				vb = vb.spin( Axis.Y );
			}

			final BitAccess ba = new BitAccess( null, null, vb, VoxelBlob.NULL_BLOB );
			final ItemStack itemstack = ba.getBitsAsItem( enumfacing, ItemType.CHISLED_BLOCK, false );

			pickcache = new ItemStackGeneratedCache( itemstack, getBlobStateReference(), rotations );
			return itemstack;
		}
		else
		{
			if ( cache != null && cache.rotations == 0 && cache.ref == getBlobStateReference() )
			{
				return cache.out.copy();
			}

			final BitAccess ba = new BitAccess( null, null, getBlob(), VoxelBlob.NULL_BLOB );
			final ItemStack itemstack = ba.getBitsAsItem( null, ItemType.CHISLED_BLOCK, false );

			pickcache = new ItemStackGeneratedCache( itemstack, getBlobStateReference(), 0 );
			return itemstack;
		}
	}

	public boolean isNormalCube()
	{
		return isNormalCube;
	}

	public boolean isSideSolid(
			final EnumFacing side )
	{
		return ( sideState & 1 << side.ordinal() ) != 0;
	}

	@SideOnly( Side.CLIENT )
	public boolean isSideOpaque(
			final EnumFacing side )
	{
		final VoxelNeighborRenderTracker vns = state != null ? state.getValue( BlockChiseled.UProperty_VoxelNeighborState ) : null;
		if ( vns != null && vns.isDynamic() )
		{
			return false;
		}

		final Integer sideFlags = ChiseledBlockSmartModel.getSides( this );
		return ( sideFlags & 1 << side.ordinal() ) != 0;
	}

	public void completeEditOperation(
			final VoxelBlob vb )
	{
		final VoxelBlobStateReference before = getBlobStateReference();
		setBlob( vb );
		final VoxelBlobStateReference after = getBlobStateReference();

		if ( worldObj != null )
		{
			worldObj.markBlockRangeForRenderUpdate( pos, pos );
			triggerDynamicUpdates();
		}

		UndoTracker.getInstance().add( getWorld(), getPos(), before, after );
	}

	public void rotateBlock(
			final EnumFacing axis )
	{
		final VoxelBlob occluded = new VoxelBlob();
		MCMultipartProxy.proxyMCMultiPart.addFiller( getWorld(), getPos(), occluded );

		VoxelBlob postRotation = getBlob();
		int maxRotations = 4;
		while ( --maxRotations > 0 )
		{
			postRotation = postRotation.spin( axis.getAxis() );

			if ( occluded.canMerge( postRotation ) )
			{
				setBlob( postRotation );
				return;
			}
		}
	}

	public boolean canMerge(
			final VoxelBlob voxelBlob )
	{
		final VoxelBlob vb = getBlob();
		final IChiseledTileContainer occ = getTileContainer();

		if ( vb.canMerge( voxelBlob ) && !occ.isBlobOccluded( voxelBlob ) )
		{
			return true;
		}

		return false;
	}

	public Collection<AxisAlignedBB> getBoxes(
			final BoxType type )
	{
		final VoxelBlobStateReference ref = getBlobStateReference();

		if ( ref != null )
		{
			return ref.getBoxes( type );
		}
		else
		{
			return Collections.emptyList();
		}
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		final BlockPos p = getPos();
		return new AxisAlignedBB( p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1 );
	}

	public void setNormalCube(
			final boolean b )
	{
		isNormalCube = b;
	}

	public int getLightValue()
	{
		return lightlevel;
	}

	@Override
	public void invalidate()
	{
		if ( worldObj != null )
		{
			triggerDynamicUpdates();
		}
	}

}
