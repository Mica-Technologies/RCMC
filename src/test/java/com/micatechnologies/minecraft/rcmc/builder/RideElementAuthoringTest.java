package com.micatechnologies.minecraft.rcmc.builder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession.SegmentType;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Asserts that every kind of ride hardware can actually be placed in a world.
 *
 * <p><b>Why this test exists.</b> Three separate features have shipped complete, well-tested and
 * <em>unreachable</em> — ratings, the element palette and block signalling — and the pattern
 * recurred inside {@code physics.element} itself: {@code LaunchTrack} and {@code DriveTyres} were
 * both fully implemented, both round-tripped through {@code ElementCodec}, both covered by their
 * own unit tests, and neither could be created by anything a player could do. The only constructor
 * call for either in {@code src/main} was inside the save codec's read path, so they could exist
 * only by loading data that nothing could write.</p>
 *
 * <p>Every one of those was found by grepping call sites by hand, and the rule to do so is written
 * down in the project's plan. It has now been missed four times. A rule that depends on someone
 * remembering it is not a control; this test is, because it fails the build.</p>
 *
 * <p><b>What it does not claim.</b> Reachable from {@link SegmentElements} is not the same as
 * reachable in game — the tool has to offer the type, and the packet has to arrive. This catches
 * the specific, repeated failure of an element with no authoring path at all, which is the one
 * that has actually happened.</p>
 */
class RideElementAuthoringTest {

    /**
     * Element implementations known to have an authoring path when this test was written.
     *
     * <p>Guards the class scan below against passing vacuously. A scan that silently found nothing
     * — a renamed package, a run from a jar rather than a class directory — would otherwise report
     * "every element is reachable" while checking nothing at all, which is a worse failure than
     * the one being guarded against.</p>
     */
    private static final Set<String> KNOWN_ELEMENTS = new HashSet<>(java.util.Arrays.asList(
        "BrakeRun", "ChainLift", "DriveTyres", "LaunchTrack", "StationPlatform"));

    /** Long enough that a launch has room to do something measurable. */
    private static TrackSection straightSection() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            nodes.add(new TrackNode(new Vec3(i * 20.0D, 64.0D, 0.0D)));
        }
        return new TrackSection(1, nodes, false, null);
    }

    private static List<SegmentType> allOf(SegmentType type, int count) {
        List<SegmentType> types = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            types.add(type);
        }
        return types;
    }

    @Test
    @DisplayName("every concrete RideElement can be produced by some segment type")
    void everyElementIsAuthorable() throws Exception {
        Set<String> discovered = concreteElementClassNames();

        assertTrue(discovered.containsAll(KNOWN_ELEMENTS),
            "class scan did not find the elements known to exist — the scan is broken, not the "
                + "code. Found: " + new TreeSet<>(discovered) + " by scanning " + scanDiagnostic());

        // What SegmentElements can actually build, by simple name.
        Set<String> authorable = new HashSet<>();
        TrackSection section = straightSection();
        for (SegmentType type : SegmentType.values()) {
            for (RideElement element : SegmentElements.build(section,
                allOf(type, section.nodes().size()))) {
                authorable.add(element.getClass().getSimpleName());
            }
        }

        // Placed by a command rather than a segment, and still a player's to place: a storage berth
        // is what /rcmc transfer lays on the storage track when it links a transfer table.
        authorable.add("StorageBerth");

        Set<String> unreachable = new TreeSet<>(discovered);
        unreachable.removeAll(authorable);
        assertTrue(unreachable.isEmpty(),
            "ride hardware with no authoring path: " + unreachable
                + ". A player cannot place these, so they exist only in tests and save files. "
                + "Add a SegmentType for each, or delete it.");
    }

    @Test
    @DisplayName("segmentTypeOf inverts build, so the editor's cycle cannot skip a type")
    void segmentTypeRoundTrips() {
        // The track editor cycles a span's type by asking what is there and taking .next(). If this
        // mapping misses a type, pressing G on that span silently converts it into something else —
        // which is exactly what the editor's old hand-written table did to launches and tyres.
        TrackSection section = straightSection();
        for (SegmentType type : SegmentType.values()) {
            List<RideElement> built =
                SegmentElements.build(section, allOf(type, section.nodes().size()));
            if (type == SegmentType.PLAIN) {
                assertTrue(built.isEmpty(), "PLAIN is the absence of hardware, not a kind of it");
                continue;
            }
            assertFalse(built.isEmpty(), type + " produced no element");
            SegmentType recovered = SegmentElements.segmentTypeOf(built.get(0));
            assertNotNull(recovered, type + " built an element that maps back to no segment type");
            org.junit.jupiter.api.Assertions.assertEquals(type, recovered,
                "round trip changed the type");
        }
    }

    /**
     * Simple names of every concrete {@link RideElement} implementation on the classpath.
     *
     * <p>Scans the compiled package directory rather than taking a hard-coded list, because a
     * hard-coded list is the thing that goes stale — a new element added next year would be absent
     * from it and the test would pass. {@link #KNOWN_ELEMENTS} guards the scan itself.</p>
     */
    /** What the scan actually looked at, for a failure message that names the cause. */
    private static String scanDiagnostic() throws Exception {
        List<File> roots = packageDirectories();
        StringBuilder text = new StringBuilder();
        for (File directory : roots) {
            File[] files = directory.listFiles();
            text.append(text.length() == 0 ? "" : ", ").append(directory)
                .append(files == null ? " (not listable)" : " (" + files.length + " files)");
        }
        return text.length() == 0 ? "<nothing on the classpath>" : text.toString();
    }

    /**
     * Every classpath directory holding the element package.
     *
     * <p><b>Plural on purpose.</b> {@code getResource} returns the <em>first</em> match, and the
     * test tree has a {@code physics.element} package too — so a single lookup found the element
     * <em>tests</em>, scanned seven files, matched none of them, and reported that no ride elements
     * exist. Enumerating every root and taking the union is the only form of this that is not
     * silently dependent on classpath order.</p>
     */
    private static List<File> packageDirectories() throws Exception {
        String path = RideElement.class.getPackage().getName().replace('.', '/');
        List<File> directories = new ArrayList<>();
        java.util.Enumeration<URL> urls =
            RideElementAuthoringTest.class.getClassLoader().getResources(path);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            File directory = new File(java.net.URLDecoder.decode(url.getFile(), "UTF-8"));
            if (directory.isDirectory()) {
                directories.add(directory);
            }
        }
        return directories;
    }

    private static Set<String> concreteElementClassNames() throws Exception {
        String packageName = RideElement.class.getPackage().getName();
        List<File> roots = packageDirectories();
        assertFalse(roots.isEmpty(),
            "cannot locate " + packageName + " as a directory on the classpath");

        Set<String> names = new HashSet<>();
        for (File directory : roots) {
            File[] files = directory.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = file.getName();
                // Nested and synthetic classes (a lambda's holder, an inner Mode enum) are not
                // elements anyone places; the interface check below would reject them anyway, but
                // skipping them keeps the scan cheap and its intent obvious.
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                Class<?> candidate = Class.forName(
                    packageName + "." + name.substring(0, name.length() - ".class".length()));
                if (!RideElement.class.isAssignableFrom(candidate)) {
                    continue;
                }
                if (candidate.isInterface() || Modifier.isAbstract(candidate.getModifiers())) {
                    continue;
                }
                names.add(candidate.getSimpleName());
            }
        }
        return names;
    }
}
