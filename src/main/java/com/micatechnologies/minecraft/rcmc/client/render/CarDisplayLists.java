package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Compiled geometry for cars, one entry per car entity, so a car is built when what it looks like
 * changes rather than every frame.
 *
 * <p>A car's model is a function of a handful of things — its train's spec, where it is in the
 * train, the wire over it, whether its doors are open and its lights on — and none of them change
 * from one frame to the next except while doors travel. The renderer used to rebuild every quad of
 * every car in view every frame regardless, which with a few trains at a station was a third of
 * the frame. Each entry remembers what its lists were built from; the renderer asks whether that
 * still holds and rebuilds only when it does not.</p>
 *
 * <p>Display lists, as the track renderer uses where VBOs are off: the colours are baked into the
 * vertices and the light comes from the lightmap coordinate current when a list is called, so one
 * list serves a car in sunlight and the same car in a tunnel.</p>
 *
 * <p>Render thread only. Entries are dropped, and their lists freed, once the car has not been
 * drawn for a while: the entity went out of range, was removed, or the world was left.</p>
 */
final class CarDisplayLists {

    /** Frames a car may go undrawn before its lists are freed. */
    private static final long IDLE_FRAMES = 1200L;

    /** Frames between sweeps for idle entries. */
    private static final long SWEEP_EVERY = 300L;

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static long lastSweep;

    private CarDisplayLists() {
        throw new AssertionError("No instances.");
    }

    /** One car's lists and what they were compiled from. */
    static final class Entry {
        int exterior = -1;
        int interior = -1;
        int glass = -1;
        /** A metro car's door leaves, compiled apart from its body: they are what moves. */
        int doorsExterior = -1;
        int doorsInterior = -1;
        int doorsGlass = -1;
        long lastUsed;

        /**
         * How wide the gap to the platform is at each doorway, {@code [door * 2 + (right ? 0 : 1)]},
         * negative where there is no platform; and the place the car stood when they were measured.
         */
        final float[] gaps = {-1.0F, -1.0F, -1.0F, -1.0F};
        double gapsAtX = Double.NaN;
        double gapsAtY;
        double gapsAtZ;

        private boolean built;
        private boolean metro;
        private TrainSpec spec;
        private boolean coupling;
        private boolean lead;
        private boolean pantograph;
        private float wire;
        private boolean thirdRail;
        private boolean doorsBuilt;
        private float right;
        private float left;
        private boolean lights;
        private boolean outerFront;
        private boolean outerRear;

        /**
         * Whether the lists are a metro car as described. The spec is compared by identity: a
         * train's spec is immutable, and repainting or resizing a train gives it a new one.
         */
        boolean isMetro(TrainSpec spec, boolean coupling, boolean pantograph, float wire, boolean thirdRail,
                        boolean lights, boolean outerFront, boolean outerRear) {
            return built && metro && this.spec == spec && this.coupling == coupling
                && this.pantograph == pantograph && this.wire == wire && this.thirdRail == thirdRail
                && this.lights == lights && this.outerFront == outerFront && this.outerRear == outerRear;
        }

        /** Whether the door lists are the leaves at these positions, for the body the lists hold. */
        boolean hasDoors(float right, float left) {
            return doorsBuilt && this.right == right && this.left == left;
        }

        void setDoors(float right, float left) {
            this.doorsBuilt = true;
            this.right = right;
            this.left = left;
        }

        /** Frees the door lists alone. */
        void releaseDoors() {
            doorsExterior = free(doorsExterior);
            doorsInterior = free(doorsInterior);
            doorsGlass = free(doorsGlass);
            doorsBuilt = false;
        }

        void setMetro(TrainSpec spec, boolean coupling, boolean pantograph, float wire, boolean thirdRail,
                      boolean lights, boolean outerFront, boolean outerRear) {
            this.built = true;
            this.metro = true;
            this.spec = spec;
            this.coupling = coupling;
            this.pantograph = pantograph;
            this.wire = wire;
            this.thirdRail = thirdRail;
            this.lights = lights;
            this.outerFront = outerFront;
            this.outerRear = outerRear;
        }

        /** Whether the lists are a coaster car as described; {@code spec} may be {@code null}. */
        boolean isCoaster(TrainSpec spec, boolean lead, boolean coupling) {
            return built && !metro && this.spec == spec && this.lead == lead && this.coupling == coupling;
        }

        void setCoaster(TrainSpec spec, boolean lead, boolean coupling) {
            this.built = true;
            this.metro = false;
            this.spec = spec;
            this.lead = lead;
            this.coupling = coupling;
        }

        /** Frees the lists; the entry can be compiled into again. */
        void release() {
            exterior = free(exterior);
            interior = free(interior);
            glass = free(glass);
            releaseDoors();
            built = false;
            spec = null;
        }

        private static int free(int list) {
            if (list >= 0) {
                GLAllocation.deleteDisplayLists(list);
            }
            return -1;
        }
    }

    /** The entry for the car entity {@code entityId}, marked as used this frame. */
    static Entry entryFor(int entityId) {
        long frame = RiddenTrainRenderer.currentFrame();
        if (frame - lastSweep >= SWEEP_EVERY || frame < lastSweep) {
            lastSweep = frame;
            sweep(frame);
        }
        Entry entry = ENTRIES.get(entityId);
        if (entry == null) {
            entry = new Entry();
            ENTRIES.put(entityId, entry);
        }
        entry.lastUsed = frame;
        return entry;
    }

    private static void sweep(long frame) {
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (frame - entry.lastUsed > IDLE_FRAMES || entry.lastUsed > frame) {
                entry.release();
                it.remove();
            }
        }
    }

    /** Frees everything: the world was left, and its entity ids mean nothing in the next. */
    static void releaseAll() {
        for (Entry entry : ENTRIES.values()) {
            entry.release();
        }
        ENTRIES.clear();
    }

    /** Starts compiling a new list and returns it; everything drawn until {@link #end} goes in. */
    static int begin() {
        int list = GLAllocation.generateDisplayLists(1);
        GlStateManager.glNewList(list, GL11.GL_COMPILE);
        return list;
    }

    static void end() {
        GlStateManager.glEndList();
    }
}
