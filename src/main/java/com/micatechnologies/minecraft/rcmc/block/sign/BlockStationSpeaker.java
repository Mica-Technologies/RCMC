package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A station announcement speaker: a wall- or ceiling-mounted PA that speaks approaching-train
 * announcements to players near it.
 *
 * <p>The model and texture are the Valcom speaker from the sibling mod CSM, reused with the owner's
 * permission (their own artwork — distinct from the reference-only third-party assets the project
 * rule covers). Unlike the transit signs, this block has real facing: it mounts on the surface you
 * click, so it needs a {@code facing} blockstate property and the six-way placement below.</p>
 *
 * <p>The behaviour — link to the nearest station, watch approaching services, announce — lives in
 * {@link TileStationSpeaker}, which reuses {@link TileTransitSignBase}'s auto-linking. The audio
 * itself is the client's job (CSM's TTS when present, a subtitle otherwise); see
 * {@code com.micatechnologies.minecraft.rcmc.client.TtsBridge}.</p>
 */
public class BlockStationSpeaker extends Block {

    public static final String NAME = "station_speaker";

    /** All six faces, so a speaker can sit on a wall or hang from a ceiling. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public BlockStationSpeaker() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(1.5F);
        setResistance(8.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileStationSpeaker();
    }

    /** Mount on the surface that was clicked, exactly like CSM's speakers. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileStationSpeaker) {
            // Immediate link on placement; the tile also retries on its own if no station is near
            // yet, so a speaker placed before its station still comes alive.
            ((TileStationSpeaker) tile).linkToNearestStation();
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        // The panel is a thin model that does not fill its block; cutout keeps its edges clean.
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
