package com.micatechnologies.minecraft.rcmc.client.render.track;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Draws every {@link TrackSection} in the current world's {@link TrackNetwork}.
 *
 * <p><b>Why {@code RenderWorldLastEvent}, not a TESR.</b> A {@code TileEntitySpecialRenderer} is
 * bound to one {@code TileEntity}'s chunk: it re-tessellates on the render thread whenever that
 * chunk rebuilds, and a chunk boundary has nothing to do with where a track section starts or
 * ends. A 200-block circuit routinely crosses a dozen chunks, and forcing that geometry to be
 * chunk-bound would mean either splitting one section's mesh across many fake tile entities or
 * re-tessellating the whole section whenever any one chunk it passes through reloads. Track
 * geometry is deliberately not chunk-bound — it belongs to the network, not to a block position —
 * so it is drawn once per frame directly from {@code RenderWorldLastEvent}, after terrain and
 * entities, using whatever the network currently contains.</p>
 *
 * <p><b>Caching.</b> Building a section's mesh (curvature-adaptive tessellation over its whole
 * length, then a world-light sample per quad) is not a per-frame operation — see
 * {@link TrackMeshBuilder}'s own javadoc for why. This class caches one baked
 * {@link CachedSection} per section id and only rebuilds it when the cached
 * {@link TrackSection} instance is not the same object as the one currently in the network.
 * That identity check (not {@code equals}) is deliberate and only valid because
 * {@code TrackSection} is immutable and every edit path (see {@code TrackSection.withNode} and
 * friends) produces a new instance rather than mutating in place — so "different reference" and
 * "content changed" are exactly the same fact here, with no {@code equals}/{@code hashCode} to
 * define or keep in sync. Sections removed from the network are pruned from the cache; a network
 * swap (e.g. changing dimension) invalidates it wholesale.</p>
 *
 * <p><b>Lighting — approximation, explicitly.</b> There is no cheap way to get true dynamic
 * per-vertex world lighting for hand-built, non-block geometry in 1.12.2 without hooking the
 * lightmap texture unit and re-baking constantly. What this class actually does: when a section's
 * mesh is (re)built, it samples {@link World#getLightBrightness(BlockPos)} once per quad (at the
 * quad's centroid block) and multiplies that into the quad's already flat-shaded color (see
 * {@link TrackMeshBuilder#shadeFor}), then never touches it again until the section changes. That
 * means lighting is <b>correct at the moment the track is built or edited</b>, but it will
 * <b>not</b> react to a torch placed nearby, a day/night transition, or terrain changing around
 * the rail afterwards — it is baked once, not live. This is a real limitation, not a nitpick: a
 * long-lived park's track will not visibly darken at night the way blocks do. Revisit if that
 * turns out to matter more than the cost of re-baking periodically.</p>
 *
 * <p><b>Fog.</b> Deliberately untouched. {@code RenderWorldLastEvent} fires from inside
 * {@code RenderGlobal}'s world render pass, which has already enabled {@code GL_FOG} with the
 * correct color and density for the current render distance; re-deriving that here would either
 * fight the existing state or duplicate logic that already ran a moment earlier in the same
 * frame. Not disabling it is what makes rails fade into the distance instead of popping through
 * fog like a HUD element.</p>
 *
 * <p><b>Rendering approach — immediate mode, not a VBO.</b> Each cached {@link CachedSection}
 * stores plain {@link MeshQuad} geometry plus baked-per-quad colors; every frame, every visible
 * section is replayed into a fresh {@code BufferBuilder}/{@code Tessellator} draw call
 * ({@link DefaultVertexFormats#POSITION_COLOR}), the same pattern {@code RenderCoasterCar} already
 * uses. The expensive work (tessellation, light baking) is cached and only redone when a section
 * changes; what happens every frame is a CPU-side float copy plus one draw call per visible
 * section, which is cheap next to vanilla terrain rendering. A persistent
 * {@code net.minecraft.client.renderer.vertex.VertexBuffer} (VBO) would remove even that copy, and
 * is the natural next step — it was not attempted here because building one correctly means
 * managing raw {@code glVertexPointer}/{@code glColorPointer} client-state by hand in 1.12.2's
 * fixed-function pipeline, and getting an offset or stride wrong there corrupts geometry silently
 * rather than failing to compile. Given the choice between an unverified low-level path and a
 * well-understood one that is already proven correct elsewhere in this codebase, this class took
 * the immediate-mode path and is flagging the upgrade explicitly rather than claiming it.</p>
 *
 * <p>Registered once from {@code RcmcClientProxy.preInit}, like every other client-only
 * event handler in this mod.</p>
 */
public final class TrackRenderer {

    /**
     * Sections farther than this from the camera (nearest-point distance to their bounding box)
     * are skipped entirely. Hardcoded rather than a config option: {@code RcmcConfig} is
     * out of scope for this change, and this is a reasonable fixed default until a client-side
     * "track render distance" setting exists to override it.
     */
    private static final double MAX_RENDER_DISTANCE = 256.0D;
    /**
     * Arc-length spacing between support columns, in blocks. Roughly matches the bent spacing on
     * real steel coasters; close enough to look structural, far enough apart not to become a wall.
     */
    private static final double SUPPORT_SPACING = 9.0D;

    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    private final Map<Integer, CachedSection> cache = new HashMap<>();

    /** Detects a network swap (e.g. a dimension change swaps in a different world's
     *  {@code RcmcWorldState}) so the whole cache is dropped rather than risking a section id
     *  collision between two unrelated networks. */
    private TrackNetwork lastNetwork;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.world;
        if (world == null) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return;
        }
        TrackNetwork network = state.network();
        if (network.isEmpty()) {
            return;
        }
        if (network != lastNetwork) {
            cache.clear();
            lastNetwork = network;
        }
        pruneRemovedSections(network);

        RenderManager renderManager = minecraft.getRenderManager();
        double camX = renderManager.viewerPosX;
        double camY = renderManager.viewerPosY;
        double camZ = renderManager.viewerPosZ;

        // Frustum reads the live GL projection/modelview matrices at construction time, which are
        // exactly this frame's camera — that is why it is built here rather than cached.
        ICamera frustum = new Frustum();
        frustum.setPosition(camX, camY, camZ);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Sampled once per frame rather than per quad: it is the same value everywhere in the
        // world, and it is what makes the whole layout darken together at dusk.
        float sunBrightness = world.getSunBrightness(event.getPartialTicks());

        for (TrackSection section : network.sections()) {
            CachedSection cached = cacheFor(section, world);
            if (cached == null || cached.quads.length == 0) {
                continue;
            }
            if (!withinRenderDistance(cached.bounds, camX, camY, camZ)) {
                continue;
            }
            if (!frustum.isBoundingBoxInFrustum(cached.bounds)) {
                continue;
            }
            draw(buffer, tessellator, cached, camX, camY, camZ, world, sunBrightness);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void pruneRemovedSections(TrackNetwork network) {
        cache.keySet().removeIf(id -> network.section(id) == null);
    }

    private CachedSection cacheFor(TrackSection section, World world) {
        CachedSection existing = cache.get(section.id());
        // Identity comparison, not equals(): see class javadoc for why this is a valid and
        // sufficient staleness check for an immutable TrackSection.
        if (existing != null && existing.section == section) {
            return existing;
        }
        CachedSection built = bake(section, TrackMeshBuilder.build(section,
            RcmcWorldState.of(world).elementSpans(), supportPoints(section, world)), world);
        cache.put(section.id(), built);
        return built;
    }

    /**
     * Support columns under {@code section}, as {@code {x, z, bottomY, topY}}.
     *
     * <p>Delegates to {@link com.micatechnologies.minecraft.rcmc.world.TrackSupports} rather than
     * computing them here. They were computed here originally, which put them on the client only —
     * so a dedicated server had nothing to collide against once they needed to be solid. One
     * source, both sides, same answer.</p>
     */
    private static java.util.List<double[]> supportPoints(TrackSection section, World world) {
        java.util.List<double[]> points = new java.util.ArrayList<>();
        for (com.micatechnologies.minecraft.rcmc.world.TrackSupports.Column column
            : com.micatechnologies.minecraft.rcmc.world.TrackSupports.columnsFor(section, world)) {
            points.add(new double[] { column.x, column.z, column.bottomY, column.topY, column.attachX,
                column.attachZ });
        }
        return points;
    }

    /**
     * Converts pure geometry into the form actually drawn: quads, the sky and block light LEVELS
     * sampled once at their centroids, and a real {@link AxisAlignedBB}.
     *
     * <p>The two light sources are stored <b>separately and unresolved</b>, rather than baking the
     * single combined brightness {@code World#getLightBrightness} returns. That combined value has
     * the current time of day already folded into it, so track built at noon would still be lit
     * like noon at midnight — glowing against a dark world, which is exactly the "renders
     * fullbright, looks instantly wrong" failure this kind of hand-built geometry is prone to.
     * Keeping the levels apart lets {@link #brightnessOf} re-resolve them against the current sun
     * every frame, for the cost of an array lookup per quad.</p>
     *
     * <p>What is still baked, and still a real limitation: the light LEVELS themselves. A torch
     * placed next to finished track will not light it until that section is edited.</p>
     */
    private static CachedSection bake(TrackSection section, TrackMesh mesh, World world) {
        int n = mesh.quads.size();
        MeshQuad[] quads = mesh.quads.toArray(new MeshQuad[0]);
        byte[] sky = new byte[n];
        byte[] block = new byte[n];

        for (int i = 0; i < n; i++) {
            MeshQuad q = quads[i];
            double cx = (q.a.x + q.b.x + q.c.x + q.d.x) / 4.0D;
            double cy = (q.a.y + q.b.y + q.c.y + q.d.y) / 4.0D;
            double cz = (q.a.z + q.b.z + q.c.z + q.d.z) / 4.0D;
            BlockPos pos = new BlockPos(cx, cy, cz);
            if (world.isBlockLoaded(pos)) {
                sky[i] = (byte) world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, pos);
                block[i] = (byte) world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos);
            }
            else {
                // Assume open sky rather than darkness: track in an unloaded chunk briefly
                // rendering bright is far less jarring than it flashing black.
                sky[i] = 15;
                block[i] = 0;
            }
        }

        AxisAlignedBB bounds = new AxisAlignedBB(mesh.minX, mesh.minY, mesh.minZ, mesh.maxX, mesh.maxY, mesh.maxZ);
        return new CachedSection(section, quads, sky, block, bounds);
    }

    /**
     * Resolves a baked sky/block light pair against the current time of day.
     *
     * <p>Mirrors how vanilla combines the two: whichever contributes more wins, with skylight
     * scaled by the sun. A torch therefore keeps track lit through the night while open track goes
     * dark, which is the behaviour a player expects without having to think about it.</p>
     */
    private static float brightnessOf(World world, int skyLevel, int blockLevel, float sunBrightness) {
        int effectiveSky = Math.round(skyLevel * sunBrightness);
        int level = Math.max(blockLevel, effectiveSky);
        float[] table = world.provider.getLightBrightnessTable();
        return table[Math.max(0, Math.min(table.length - 1, level))];
    }

    private static void draw(BufferBuilder buffer, Tessellator tessellator, CachedSection cached,
                              double camX, double camY, double camZ, World world, float sunBrightness) {
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        MeshQuad[] quads = cached.quads;
        for (int i = 0; i < quads.length; i++) {
            MeshQuad q = quads[i];
            // Resolved per frame, not baked: this is what makes track darken at dusk instead of
            // glowing at the brightness it happened to be built at.
            float light = brightnessOf(world, cached.skyLight[i], cached.blockLight[i], sunBrightness);
            float red = q.red * light;
            float green = q.green * light;
            float blue = q.blue * light;
            // Subtract the camera position in double precision here, before any value reaches
            // BufferBuilder, rather than translating the modelview matrix by the raw absolute
            // camera position: the vertex format backing this draw call is float32
            // (POSITION_COLOR), so precision already lost truncating a large absolute world
            // coordinate to float cannot be recovered by a later matrix transform. Subtracting
            // first keeps every value handed to the GPU small, which is the same reason vanilla
            // chunk rendering stores chunk-local rather than world-absolute vertex data.
            buffer.pos(q.a.x - camX, q.a.y - camY, q.a.z - camZ).color(red, green, blue, 1.0F).endVertex();
            buffer.pos(q.b.x - camX, q.b.y - camY, q.b.z - camZ).color(red, green, blue, 1.0F).endVertex();
            buffer.pos(q.c.x - camX, q.c.y - camY, q.c.z - camZ).color(red, green, blue, 1.0F).endVertex();
            buffer.pos(q.d.x - camX, q.d.y - camY, q.d.z - camZ).color(red, green, blue, 1.0F).endVertex();
        }
        tessellator.draw();
    }

    private static boolean withinRenderDistance(AxisAlignedBB bounds, double camX, double camY, double camZ) {
        double nearX = clampToRange(camX, bounds.minX, bounds.maxX);
        double nearY = clampToRange(camY, bounds.minY, bounds.maxY);
        double nearZ = clampToRange(camZ, bounds.minZ, bounds.maxZ);
        double dx = nearX - camX;
        double dy = nearY - camY;
        double dz = nearZ - camZ;
        return dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQ;
    }

    private static double clampToRange(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** One section's baked, GPU-ready-ish geometry, keyed in {@link #cache} by section id. */
    private static final class CachedSection {

        final TrackSection section;
        final MeshQuad[] quads;
        final byte[] skyLight;
        final byte[] blockLight;
        final AxisAlignedBB bounds;

        CachedSection(TrackSection section, MeshQuad[] quads, byte[] skyLight, byte[] blockLight,
                      AxisAlignedBB bounds) {
            this.section = section;
            this.quads = quads;
            this.skyLight = skyLight;
            this.blockLight = blockLight;
            this.bounds = bounds;
        }
    }
}
