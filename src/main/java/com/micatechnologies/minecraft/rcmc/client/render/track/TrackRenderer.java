package com.micatechnologies.minecraft.rcmc.client.render.track;

import com.micatechnologies.minecraft.rcmc.track.ElementSpan;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * Draws every {@link TrackSection} in the current world's {@link TrackNetwork}.
 *
 * <p><b>Why {@code RenderWorldLastEvent}, not a TESR.</b> A {@code TileEntitySpecialRenderer} is
 * bound to one {@code TileEntity}'s chunk, and a chunk boundary has nothing to do with where a
 * track section starts or ends. Track geometry belongs to the network, not to a block position, so
 * it is drawn once per frame from {@code RenderWorldLastEvent}, after terrain and entities.</p>
 *
 * <p><b>Culled by cell, not by section.</b> Each section's mesh is split by
 * {@link TrackMeshCells} into 32-block cells with their own bounds, and each cell is culled by
 * distance and frustum on its own. Culling whole sections looked fine on coasters and was a
 * disaster on a city metro: a 23 km loop's box is the whole map, so it was never culled and every
 * quad of it was drawn every frame for every player (issue #8: 165 to 30 FPS). Cost now follows
 * the track near the camera, not the length of the section it belongs to.</p>
 *
 * <p><b>Each cell is a GPU buffer.</b> A cell is compiled once into a VBO (or a display list
 * where VBOs are off, the same choice vanilla makes for terrain) and drawn with one call per
 * frame; nothing is copied through a {@code BufferBuilder} per frame any more. Vertex colours
 * carry the light, so a cell is recompiled only when its light changes: when the sun's effect on
 * skylight moves to a different level (a handful of times through dusk and dawn), or when the
 * cell's light levels are re-sampled and come out different. Recompiles are budgeted per frame so
 * dusk over a big park is spread across frames instead of landing as one hitch; a cell waiting its
 * turn keeps drawing at its old light meanwhile.</p>
 *
 * <p><b>Light.</b> The sky and block light LEVELS are sampled at each quad's centroid and stored
 * apart, then resolved against the current sun when a cell compiles — vanilla's rule, whichever
 * contributes more wins. Sampling happens when a cell is first drawn, not when the section is
 * built, because that is when its chunk is actually loaded; sampling a 23 km line at bake time
 * found most of it unloaded and lit underground track as open sky. Visible cells are then
 * re-sampled round-robin, one per frame, so a torch placed by the track lights it within seconds
 * instead of never.</p>
 *
 * <p><b>Building is off the render thread.</b> Tessellating a section ({@link TrackMeshBuilder})
 * and splitting it into cells is pure geometry, run on a single background thread. Only the
 * support columns, which have to ask the world how high the ground is, are worked out here first.
 * Until a rebuild lands the previous mesh keeps drawing, so editing a long line does not freeze
 * the client while it re-tessellates. Staleness is by identity: {@code TrackSection} is immutable
 * and every edit produces a new instance, and the world state swaps its element-span list rather
 * than mutating it, so "different reference" is exactly "changed".</p>
 *
 * <p><b>Fog</b> is deliberately untouched: {@code RenderGlobal} has already set it up for this
 * frame, and it is what makes rails fade into the distance instead of popping.</p>
 *
 * <p><b>Precision.</b> Cell vertices are stored relative to the cell's origin and placed with a
 * modelview translate of {@code origin - camera}, computed in double. Both are small, so float32
 * stays exact, which is the same reason vanilla stores chunk-local vertex data.</p>
 *
 * <p>Registered once from {@code RcmcClientProxy.preInit}, like every other client-only
 * event handler in this mod. Everything here runs on the render thread except the mesher.</p>
 */
public final class TrackRenderer {

    private static final Logger LOG = LogManager.getLogger("RCMC/TrackRenderer");

    /**
     * Track is drawn out to the client's own render distance, with this floor and ceiling. With
     * per-cell culling the cost of a far cell is one draw call, so there is no reason to stop
     * short of the terrain a player can see; the ceiling keeps a 32-chunk client in a dense park
     * from drawing a whole region of track it can barely make out through the fog.
     */
    private static final double MIN_RENDER_DISTANCE = 64.0D;
    private static final double MAX_RENDER_DISTANCE = 512.0D;

    /**
     * Quads that may be compiled into GPU buffers in one frame, past the first cell (one cell is
     * always allowed, so progress never stalls). About a millisecond of upload; a dusk relight of
     * a large park spreads over a few frames instead of costing one long one.
     */
    private static final int COMPILE_BUDGET_QUADS = 40000;

    /** One mesher thread: sections queue rather than fight each other for cores the game needs. */
    private static final ExecutorService MESHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RCMC track mesher");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private final Map<Integer, SectionEntry> cache = new HashMap<>();

    /** Cells drawn last frame, in draw order; the round-robin light re-sampler walks this. */
    private final List<RenderCell> visible = new ArrayList<>();
    private int resampleCursor;

    /** Shared upload scratch. Grows on demand; sized for a typical cell so it rarely does. */
    private final BufferBuilder uploadBuffer = new BufferBuilder(1 << 18);
    private final WorldVertexBufferUploader listUploader = new WorldVertexBufferUploader();
    private final VertexBufferUploader vboUploader = new VertexBufferUploader();

    /**
     * Detects a network swap (e.g. a dimension change swaps in a different world's
     * {@code RcmcWorldState}) so the whole cache is dropped rather than risking a section id
     * collision between two unrelated networks.
     */
    private TrackNetwork lastNetwork;

    /** Releases GPU buffers once the player has left a world, when no render event will fire. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getMinecraft().world == null
            && !cache.isEmpty()) {
            releaseAll();
            lastNetwork = null;
        }
    }

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
        if (network != lastNetwork) {
            releaseAll();
            lastNetwork = network;
        }
        pruneRemovedSections(network);
        if (network.isEmpty()) {
            return;
        }

        RenderManager renderManager = minecraft.getRenderManager();
        double camX = renderManager.viewerPosX;
        double camY = renderManager.viewerPosY;
        double camZ = renderManager.viewerPosZ;
        double renderDistance = Math.max(MIN_RENDER_DISTANCE,
            Math.min(MAX_RENDER_DISTANCE, minecraft.gameSettings.renderDistanceChunks * 16.0D));
        double renderDistanceSq = renderDistance * renderDistance;

        // Frustum reads the live GL projection/modelview matrices at construction time, which are
        // exactly this frame's camera — that is why it is built here rather than cached.
        ICamera frustum = new Frustum();
        frustum.setPosition(camX, camY, camZ);

        // Sampled once per frame: it is the same everywhere, and it is what makes the whole
        // layout darken together at dusk.
        float sunBrightness = world.getSunBrightness(event.getPartialTicks());
        long skyKey = skyKey(sunBrightness);
        float[] lightTable = world.provider.getLightBrightnessTable();

        visible.clear();
        List<ElementSpan> spans = state.elementSpans();
        for (TrackSection section : network.sections()) {
            SectionEntry entry = entryFor(section, spans, world);
            if (entry.cells == null) {
                continue;
            }
            for (RenderCell cell : entry.cells) {
                if (!withinRenderDistance(cell.bounds, camX, camY, camZ, renderDistanceSq)) {
                    continue;
                }
                if (!frustum.isBoundingBoxInFrustum(cell.bounds)) {
                    continue;
                }
                visible.add(cell);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        resampleOne(world);

        boolean useVbo = OpenGlHelper.useVbo();
        int budget = COMPILE_BUDGET_QUADS;
        boolean compiledAny = false;
        for (RenderCell cell : visible) {
            if (cell.needsCompile(skyKey, useVbo) && (budget > 0 || !compiledAny)) {
                cell.compile(world, sunBrightness, skyKey, lightTable, useVbo);
                budget -= cell.geometry.quadCount();
                compiledAny = true;
            }
        }

        draw(camX, camY, camZ, useVbo);
    }

    private void draw(double camX, double camY, double camZ, boolean useVbo) {
        GlStateManager.disableTexture2D();
        // Double-sided: the profiles are wound consistently, but the ballast bed showed that some
        // outward faces were being culled by the ambient world-render cull state. Flat-shaded
        // solid geometry looks identical either way, and this guarantees every face shows.
        GlStateManager.disableCull();
        if (useVbo) {
            GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        }
        for (RenderCell cell : visible) {
            if (!cell.hasBuffer()) {
                continue;
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate((float) (cell.geometry.originX - camX),
                (float) (cell.geometry.originY - camY), (float) (cell.geometry.originZ - camZ));
            if (cell.vbo != null) {
                cell.vbo.bindBuffer();
                // POSITION_COLOR: three floats, then four colour bytes — a 16-byte stride.
                GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 16, 0);
                GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12);
                cell.vbo.drawArrays(GL11.GL_QUADS);
            }
            else {
                GlStateManager.callList(cell.displayList);
            }
            GlStateManager.popMatrix();
        }
        if (useVbo) {
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        }
        GlStateManager.resetColor();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    /**
     * Re-samples one visible cell's light per frame, so lighting changes made after the track
     * was built (a torch, a roof, a dug-out tunnel) reach it within seconds.
     */
    private void resampleOne(World world) {
        if (resampleCursor >= visible.size()) {
            resampleCursor = 0;
        }
        visible.get(resampleCursor++).resampleLight(world);
    }

    private SectionEntry entryFor(TrackSection section, List<ElementSpan> spans, World world) {
        SectionEntry entry = cache.get(section.id());
        if (entry == null) {
            entry = new SectionEntry();
            cache.put(section.id(), entry);
        }
        entry.collect();
        // Cheap when cached; also what fills in columns over chunks that have loaded since.
        List<com.micatechnologies.minecraft.rcmc.world.TrackSupports.Column> columns =
            com.micatechnologies.minecraft.rcmc.world.TrackSupports.columnsFor(section, world);
        if (entry.builtFor != section || entry.builtSpans != spans || entry.builtColumns != columns) {
            if (entry.pendingFor != section || entry.pendingSpans != spans
                || entry.pendingColumns != columns) {
                entry.submit(section, spans, columns);
            }
        }
        return entry;
    }

    private void pruneRemovedSections(TrackNetwork network) {
        cache.entrySet().removeIf(e -> {
            if (network.section(e.getKey()) != null) {
                return false;
            }
            e.getValue().release();
            return true;
        });
    }

    private void releaseAll() {
        for (SectionEntry entry : cache.values()) {
            entry.release();
        }
        cache.clear();
        visible.clear();
    }

    /**
     * Support columns under {@code section}, as {@code {x, z, bottomY, topY, attachX, attachZ}}.
     *
     * <p>Delegates to {@link com.micatechnologies.minecraft.rcmc.world.TrackSupports} so the
     * column you see and the column you bump into are the same one. Runs on the render thread,
     * because it reads the world; the mesh built from it does not.</p>
     */
    private static List<double[]> supportPoints(
        List<com.micatechnologies.minecraft.rcmc.world.TrackSupports.Column> columns) {
        List<double[]> points = new ArrayList<>();
        for (com.micatechnologies.minecraft.rcmc.world.TrackSupports.Column column
            : columns) {
            points.add(new double[] { column.x, column.z, column.bottomY, column.topY, column.attachX,
                column.attachZ });
        }
        return points;
    }

    /**
     * The sun's effect on every skylight level, packed four bits per level: two frames with the
     * same key light every quad identically, so a cell compiled under one need not recompile under
     * the other. This is what keeps recompiles to a handful per dusk rather than one per frame —
     * {@code getSunBrightness} drifts continuously, but rounded per level it steps.
     */
    static long skyKey(float sunBrightness) {
        long key = 0L;
        for (int level = 1; level <= 15; level++) {
            key = (key << 4) | (Math.round(level * sunBrightness) & 0xF);
        }
        return key;
    }

    /**
     * Resolves a sky/block light pair against the current sun, as vanilla combines them:
     * whichever contributes more wins, with skylight scaled by the sun. A torch therefore keeps
     * track lit through the night while open track goes dark.
     */
    private static float brightnessOf(float[] table, int skyLevel, int blockLevel, float sunBrightness) {
        int effectiveSky = Math.round(skyLevel * sunBrightness);
        int level = Math.max(blockLevel, effectiveSky);
        return table[Math.max(0, Math.min(table.length - 1, level))];
    }

    private static boolean withinRenderDistance(AxisAlignedBB bounds, double camX, double camY, double camZ,
                                                double maxDistanceSq) {
        double dx = Math.max(bounds.minX, Math.min(bounds.maxX, camX)) - camX;
        double dy = Math.max(bounds.minY, Math.min(bounds.maxY, camY)) - camY;
        double dz = Math.max(bounds.minZ, Math.min(bounds.maxZ, camZ)) - camZ;
        return dx * dx + dy * dy + dz * dz <= maxDistanceSq;
    }

    /** One section's cells, plus the background build that will replace them, if any. */
    private final class SectionEntry {

        /** What {@link #cells} was built from; {@code null} until the first build lands. */
        TrackSection builtFor;
        List<ElementSpan> builtSpans;
        List<com.micatechnologies.minecraft.rcmc.world.TrackSupports.Column> builtColumns;
        RenderCell[] cells;

        TrackSection pendingFor;
        List<ElementSpan> pendingSpans;
        List<com.micatechnologies.minecraft.rcmc.world.TrackSupports.Column> pendingColumns;
        Future<List<TrackMeshCells.Cell>> pending;

        void submit(TrackSection section, List<ElementSpan> spans,
                    List<com.micatechnologies.minecraft.rcmc.world.TrackSupports.Column> columns) {
            if (pending != null) {
                pending.cancel(false);
            }
            // The world was read on the render thread; the mesher sees only plain data.
            List<double[]> supports = supportPoints(columns);
            List<ElementSpan> spanSnapshot = new ArrayList<>(spans);
            pendingFor = section;
            pendingSpans = spans;
            pendingColumns = columns;
            pending = MESHER.submit(() -> TrackMeshCells.partition(
                TrackMeshBuilder.build(section, spanSnapshot, supports)));
        }

        /** Swaps in a finished build, if one has landed. Never blocks. */
        void collect() {
            if (pending == null || !pending.isDone()) {
                return;
            }
            Future<List<TrackMeshCells.Cell>> done = pending;
            pending = null;
            List<TrackMeshCells.Cell> built;
            try {
                built = done.get();
            }
            catch (Exception e) {
                // Keep drawing the previous mesh rather than nothing; a section that cannot be
                // tessellated is a bug to report, not a reason to make the track vanish.
                LOG.error("Could not build the mesh for track section #{}", pendingFor.id(), e);
                builtFor = pendingFor;
                builtSpans = pendingSpans;
                builtColumns = pendingColumns;
                return;
            }
            releaseCells();
            RenderCell[] next = new RenderCell[built.size()];
            for (int i = 0; i < next.length; i++) {
                next[i] = new RenderCell(built.get(i));
            }
            cells = next;
            builtFor = pendingFor;
            builtSpans = pendingSpans;
            builtColumns = pendingColumns;
        }

        void release() {
            if (pending != null) {
                pending.cancel(false);
                pending = null;
            }
            releaseCells();
        }

        private void releaseCells() {
            if (cells != null) {
                for (RenderCell cell : cells) {
                    cell.deleteBuffer();
                }
                cells = null;
            }
        }
    }

    /** One cell's geometry, its sampled light, and the GPU buffer compiled from them. */
    private final class RenderCell {

        final TrackMeshCells.Cell geometry;
        final AxisAlignedBB bounds;

        /** Per quad; {@code null} until first sampled, which is when the cell is first drawn. */
        byte[] skyLight;
        byte[] blockLight;
        /** Set when re-sampling found the light changed; forces a recompile. */
        boolean lightDirty;

        VertexBuffer vbo;
        int displayList = -1;
        long compiledSkyKey;

        RenderCell(TrackMeshCells.Cell geometry) {
            this.geometry = geometry;
            this.bounds = new AxisAlignedBB(geometry.minX, geometry.minY, geometry.minZ,
                geometry.maxX, geometry.maxY, geometry.maxZ);
        }

        boolean hasBuffer() {
            return vbo != null || displayList >= 0;
        }

        boolean needsCompile(long skyKey, boolean useVbo) {
            // A VBO toggle in video settings orphans the other kind of buffer.
            boolean wrongKind = useVbo ? vbo == null : displayList < 0;
            return wrongKind || lightDirty || compiledSkyKey != skyKey;
        }

        void resampleLight(World world) {
            if (skyLight == null) {
                return;
            }
            byte[] sky = new byte[skyLight.length];
            byte[] block = new byte[blockLight.length];
            sample(world, sky, block);
            if (!java.util.Arrays.equals(sky, skyLight) || !java.util.Arrays.equals(block, blockLight)) {
                skyLight = sky;
                blockLight = block;
                lightDirty = true;
            }
        }

        private void sample(World world, byte[] sky, byte[] block) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < sky.length; i++) {
                pos.setPos(geometry.centroidX(i), geometry.centroidY(i), geometry.centroidZ(i));
                if (world.isBlockLoaded(pos)) {
                    sky[i] = (byte) world.getLightFor(EnumSkyBlock.SKY, pos);
                    block[i] = (byte) world.getLightFor(EnumSkyBlock.BLOCK, pos);
                }
                else {
                    // Assume open sky rather than darkness until the chunk arrives; the
                    // round-robin re-sample corrects it within seconds once it has.
                    sky[i] = 15;
                    block[i] = 0;
                }
            }
        }

        void compile(World world, float sunBrightness, long skyKey, float[] lightTable, boolean useVbo) {
            if (skyLight == null) {
                skyLight = new byte[geometry.quadCount()];
                blockLight = new byte[geometry.quadCount()];
                sample(world, skyLight, blockLight);
            }
            BufferBuilder buffer = uploadBuffer;
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            float[] positions = geometry.positions;
            float[] colours = geometry.colours;
            int quads = geometry.quadCount();
            for (int q = 0; q < quads; q++) {
                float light = brightnessOf(lightTable, skyLight[q], blockLight[q], sunBrightness);
                int c = q * TrackMeshCells.COLOUR_FLOATS;
                float red = colours[c] * light;
                float green = colours[c + 1] * light;
                float blue = colours[c + 2] * light;
                int p = q * TrackMeshCells.POSITION_FLOATS;
                for (int corner = 0; corner < 4; corner++, p += 3) {
                    buffer.pos(positions[p], positions[p + 1], positions[p + 2])
                        .color(red, green, blue, 1.0F).endVertex();
                }
            }
            buffer.finishDrawing();

            deleteBuffer();
            if (useVbo) {
                vbo = new VertexBuffer(DefaultVertexFormats.POSITION_COLOR);
                vboUploader.setVertexBuffer(vbo);
                vboUploader.draw(buffer);
            }
            else {
                displayList = GLAllocation.generateDisplayLists(1);
                GlStateManager.glNewList(displayList, GL11.GL_COMPILE);
                listUploader.draw(buffer);
                GlStateManager.glEndList();
            }
            compiledSkyKey = skyKey;
            lightDirty = false;
        }

        void deleteBuffer() {
            if (vbo != null) {
                vbo.deleteGlBuffers();
                vbo = null;
            }
            if (displayList >= 0) {
                GLAllocation.deleteDisplayLists(displayList);
                displayList = -1;
            }
        }
    }
}
