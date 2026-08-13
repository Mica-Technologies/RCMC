package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The transit signage's own font: a dot-matrix face, baked into a glyph sheet and read by a
 * {@link FontRenderer} of our own rather than the game's.
 *
 * <h2>Why a second FontRenderer and not a resource pack</h2>
 *
 * <p>Replacing {@code textures/font/ascii.png} would work and would also change every other piece
 * of text in the game — chat, tooltips, the pause menu. A departure board is the one place this
 * face belongs. {@code FontRenderer} takes the sheet it reads as a constructor argument, so a
 * private instance pointed at a private texture is the whole mechanism; nothing else in the game
 * ever sees it.</p>
 *
 * <h2>Built lazily, once</h2>
 *
 * <p>Constructing one reads its texture immediately, so this cannot happen during pre-init when the
 * resource manager has nothing in it yet. First use is the earliest safe moment, and by then a
 * world is being rendered. It is also registered for resource reloads, so switching resource packs
 * re-reads the sheet instead of leaving a stale one bound — and registered only here, on the one
 * construction, because a listener added per call would accumulate for the life of the game.</p>
 */
@SideOnly(Side.CLIENT)
public final class RcmcFonts {

    private static final ResourceLocation DOT_MATRIX_SHEET =
        new ResourceLocation(RcmcConstants.MOD_NAMESPACE, "textures/font/dotmatrix.png");

    private static FontRenderer dotMatrix;

    private RcmcFonts() {
        throw new AssertionError("No instances.");
    }

    /** The dot-matrix font every transit display draws in. */
    public static FontRenderer dotMatrix() {
        if (dotMatrix == null) {
            Minecraft mc = Minecraft.getMinecraft();
            FontRenderer created = new FontRenderer(mc.gameSettings, DOT_MATRIX_SHEET,
                mc.getTextureManager(), false);
            // The constructor does not read the sheet; the reload does, and that is also what
            // derives each glyph's advance from its rightmost lit column.
            created.onResourceManagerReload(mc.getResourceManager());
            if (mc.getResourceManager() instanceof IReloadableResourceManager) {
                ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(created);
            }
            dotMatrix = created;
        }
        return dotMatrix;
    }
}
