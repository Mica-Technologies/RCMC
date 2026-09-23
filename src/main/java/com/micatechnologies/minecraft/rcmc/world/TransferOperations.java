package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.TransferTrack;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.Transfers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Runs every ride's transfer table from the world tick: what the operator asked for on the panel,
 * carried out as soon as it safely can be.
 *
 * <p>Two halves, either side of the trains moving. {@link #armHolds} before, so a table asked to
 * store stops the next train that reaches it; {@link #carryOut} after, so a train that has come to
 * rest on it that tick is slid across straight away.</p>
 */
final class TransferOperations {

    private TransferOperations() {
    }

    /** Tells each table whether to stop the next train, from its ride's request. */
    static void armHolds(RcmcWorldState state) {
        for (RideElement element : state.elements().elements()) {
            if (element instanceof TransferTrack) {
                TransferTrack transfer = (TransferTrack) element;
                RideController ride = state.rides().get(transfer.sectionId());
                transfer.setHolding(transfer.isLinked() && ride != null
                    && ride.transferRequest() == RideController.TransferRequest.STORE
                    && Transfers.stored(transfer, state.trains().asMap()) == null);
            }
        }
    }

    /** Moves trains the requests are waiting on, once each move is safe. */
    static void carryOut(World world, RcmcWorldState state) {
        for (RideElement element : state.elements().elements()) {
            if (!(element instanceof TransferTrack) || !((TransferTrack) element).isLinked()) {
                continue;
            }
            TransferTrack transfer = (TransferTrack) element;
            RideController ride = state.rides().get(transfer.sectionId());
            if (ride == null || ride.transferRequest() == RideController.TransferRequest.NONE) {
                continue;
            }
            Integer stored = Transfers.stored(transfer, state.trains().asMap());
            if (ride.transferRequest() == RideController.TransferRequest.STORE) {
                Integer ready = stored == null ? Transfers.readyToStore(transfer, state.trains().asMap())
                    : null;
                if (ready != null) {
                    Transfers.store(state.trains().train(ready), transfer);
                    finished(world, state, ride, "Coaster #" + transfer.sectionId() + ": train #"
                        + ready + " moved to storage.");
                }
            }
            else if (stored != null) {
                Train train = state.trains().train(stored);
                if (Transfers.tableClear(transfer, state.trains().asMap(), train.spec().totalLength())) {
                    Transfers.retrieve(train, transfer);
                    finished(world, state, ride, "Coaster #" + transfer.sectionId() + ": train #"
                        + stored + " brought back from storage.");
                }
            }
        }
    }

    private static void finished(World world, RcmcWorldState state, RideController ride, String what) {
        ride.setTransferRequest(RideController.TransferRequest.NONE);
        state.markTrainsDirty(world);
        TextComponentString message = new TextComponentString(TextFormatting.AQUA + what);
        for (EntityPlayer player : world.playerEntities) {
            player.sendMessage(message);
        }
    }
}
