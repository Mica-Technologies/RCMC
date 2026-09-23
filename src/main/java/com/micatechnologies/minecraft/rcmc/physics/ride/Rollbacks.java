package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds trains rolling backwards on a lift — what a real lift's anti-rollback dogs are there for.
 *
 * <p>A chain only ever drives a train up, so a train moving back down a lift has come from above:
 * it failed to clear a hill after the crest and fell back over the top. Left alone the chain would
 * catch it, carry it up and over, and it would fail the same hill again, forever. A real ride's
 * dogs catch the train and the ride stops; the world tick does the same with every section this
 * reports, and the e-stop layer holds the train where it was caught.</p>
 *
 * <p>A train counts as on the lift from the lift's start until its last car has left the top — the
 * stretch where a car is still over the dogs.</p>
 */
public final class Rollbacks {

    /** Backward speed that counts as rolling back, blocks/s — well clear of a stopped train's jitter. */
    static final double ROLLBACK_SPEED = 0.3D;

    private Rollbacks() {
    }

    /** Sections with a coaster train rolling back down one of their lifts, ascending. */
    public static Set<Integer> sectionsRollingBack(Map<Integer, Train> trains, RideElementSet elements) {
        Set<Integer> rolling = new TreeSet<>();
        for (Train train : trains.values()) {
            if (train.reference() != null && rollingBack(train, elements)) {
                rolling.add(train.reference().sectionId());
            }
        }
        return rolling;
    }

    static boolean rollingBack(Train train, RideElementSet elements) {
        int section = train.reference().sectionId();
        double d = train.reference().distance();
        double length = train.spec().totalLength();
        for (RideElement element : elements.elements()) {
            if (!(element instanceof ChainLift) || element.sectionId() != section) {
                continue;
            }
            ChainLift lift = (ChainLift) element;
            boolean up = lift.chainSpeed() >= 0.0D;
            boolean onIt = up
                ? d >= lift.startDistance() && d <= lift.endDistance() + length
                : d <= lift.endDistance() && d >= lift.startDistance() - length;
            double backward = up ? -train.velocity() : train.velocity();
            if (onIt && backward > ROLLBACK_SPEED) {
                return true;
            }
        }
        return false;
    }
}
