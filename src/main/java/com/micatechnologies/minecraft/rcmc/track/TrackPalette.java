package com.micatechnologies.minecraft.rcmc.track;

/**
 * The colours a section's steelwork is painted in.
 *
 * <p>Recolouring is most of what makes a park look like someone's rather than like a tech demo, and
 * it is a large part of RollerCoaster Tycoon's identity. Colour lives on the section, beside
 * {@code styleId}, because it is <em>authored</em> data: a builder chose it, so it belongs in the
 * save file and travels to clients with the geometry.</p>
 *
 * <p><b>A fixed palette, not a colour picker.</b> RCT parks look coherent rather than garish
 * largely because the game never offered arbitrary RGB. A short list of colours that were chosen to
 * sit together does more for the result than freedom would.</p>
 *
 * <p>Immutable, and free of Minecraft types like everything else in {@code track}.</p>
 */
public final class TrackPalette {

    /** The parts of a track section that can be coloured independently. */
    public enum Part {
        RAIL,
        SPINE,
        TIE,
        SUPPORT
    }

    /**
     * The available colours. Named rather than free RGB, and deliberately a short list — see the
     * class javadoc.
     */
    public enum Colour {
        STEEL("Steel", 0.62F, 0.62F, 0.66F),
        GRAPHITE("Graphite", 0.24F, 0.25F, 0.27F),
        WHITE("White", 0.90F, 0.90F, 0.88F),
        RED("Red", 0.72F, 0.16F, 0.14F),
        ORANGE("Orange", 0.88F, 0.48F, 0.13F),
        YELLOW("Yellow", 0.90F, 0.78F, 0.20F),
        GREEN("Green", 0.22F, 0.55F, 0.26F),
        TEAL("Teal", 0.16F, 0.56F, 0.56F),
        BLUE("Blue", 0.20F, 0.36F, 0.70F),
        PURPLE("Purple", 0.44F, 0.26F, 0.60F),
        PINK("Pink", 0.85F, 0.45F, 0.62F),
        BROWN("Brown", 0.42F, 0.31F, 0.20F);

        private final String label;
        private final float red;
        private final float green;
        private final float blue;

        Colour(String label, float red, float green, float blue) {
            this.label = label;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public String label() {
            return label;
        }

        public float red() {
            return red;
        }

        public float green() {
            return green;
        }

        public float blue() {
            return blue;
        }

        public Colour next() {
            Colour[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        /** Looks a colour up by name, falling back to {@code fallback} for an unknown one. */
        public static Colour byName(String name, Colour fallback) {
            if (name == null) {
                return fallback;
            }
            for (Colour colour : values()) {
                if (colour.name().equalsIgnoreCase(name)) {
                    return colour;
                }
            }
            // An unrecognised colour comes from a newer version or a removed entry. Falling back
            // beats refusing to load a park over a paint job.
            return fallback;
        }
    }

    /** What track looks like when nobody has painted it. */
    public static final TrackPalette DEFAULT =
        new TrackPalette(Colour.STEEL, Colour.GRAPHITE, Colour.BROWN, Colour.STEEL);

    private final Colour rail;
    private final Colour spine;
    private final Colour tie;
    private final Colour support;

    public TrackPalette(Colour rail, Colour spine, Colour tie, Colour support) {
        this.rail = rail == null ? Colour.STEEL : rail;
        this.spine = spine == null ? Colour.GRAPHITE : spine;
        this.tie = tie == null ? Colour.BROWN : tie;
        this.support = support == null ? Colour.STEEL : support;
    }

    public Colour of(Part part) {
        switch (part) {
            case SPINE:
                return spine;
            case TIE:
                return tie;
            case SUPPORT:
                return support;
            case RAIL:
            default:
                return rail;
        }
    }

    /** A copy with one part recoloured. */
    public TrackPalette with(Part part, Colour colour) {
        switch (part) {
            case SPINE:
                return new TrackPalette(rail, colour, tie, support);
            case TIE:
                return new TrackPalette(rail, spine, colour, support);
            case SUPPORT:
                return new TrackPalette(rail, spine, tie, colour);
            case RAIL:
            default:
                return new TrackPalette(colour, spine, tie, support);
        }
    }

    /** True if this is the untouched default, so persistence can skip writing it. */
    public boolean isDefault() {
        return rail == Colour.STEEL && spine == Colour.GRAPHITE
            && tie == Colour.BROWN && support == Colour.STEEL;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TrackPalette)) {
            return false;
        }
        TrackPalette o = (TrackPalette) obj;
        return rail == o.rail && spine == o.spine && tie == o.tie && support == o.support;
    }

    @Override
    public int hashCode() {
        return ((rail.ordinal() * 31 + spine.ordinal()) * 31 + tie.ordinal()) * 31 + support.ordinal();
    }

    @Override
    public String toString() {
        return "TrackPalette{rail=" + rail + ", spine=" + spine
            + ", tie=" + tie + ", support=" + support + '}';
    }
}
