package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/**
 * Station platform decking — the surface passengers wait and walk on.
 *
 * <p>A plain full block, deliberately. The interesting one is {@link BlockPlatformEdge}, which
 * carries the tactile warning strip along the platform edge; everything behind that line is just
 * floor, and a floor that tried to be clever would only get in the way of a builder laying out a
 * station of their own shape.</p>
 *
 * <p><b>Why platforms matter mechanically and not only visually.</b> A metro car's floor sits
 * {@code CarSeating.METRO_FLOOR_HEIGHT} above the track, which is well over a player's 0.6 step
 * height — so without a platform level with that floor, boarding on foot means jumping into the
 * doorway. A platform at the right height turns boarding into walking, which is the whole point.
 * {@code /rcmc platform} builds one at exactly that height so nobody has to count blocks.</p>
 */
public class BlockPlatform extends Block {

    public static final String NAME = "platform";

    public BlockPlatform() {
        super(Material.ROCK);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(1.8F);
        setResistance(10.0F);
        setSoundType(SoundType.STONE);
    }
}
