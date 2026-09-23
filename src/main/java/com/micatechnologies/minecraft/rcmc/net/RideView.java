package com.micatechnologies.minecraft.rcmc.net;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the ride operator panel shows about one ride, as the server sees it right now.
 *
 * <p>Common code, plain data: the server builds it, the packet carries it, the client GUI only reads
 * it. The GUI never holds live state of its own, so what it shows cannot drift from the ride.</p>
 */
public final class RideView {

    /** One train on the ride. */
    public static final class TrainRow {
        public final int trainId;
        public final int cars;
        public final double speed;
        public final String status;
        public final double distance;

        public TrainRow(int trainId, int cars, double speed, String status, double distance) {
            this.trainId = trainId;
            this.cars = cars;
            this.speed = speed;
            this.status = status;
            this.distance = distance;
        }
    }

    /** One adjustable value: which element, which parameter (by ordinal), its current value. */
    public static final class SettingRow {
        public final int elementIndex;
        public final int parameter;
        public final double value;

        public SettingRow(int elementIndex, int parameter, double value) {
            this.elementIndex = elementIndex;
            this.parameter = parameter;
            this.value = value;
        }
    }

    public final int sectionId;
    public final String name;
    /** {@code RideController.State} ordinal. */
    public final int state;
    /** {@code RideController.DispatchMode} ordinal. */
    public final int dispatchMode;
    public final boolean emergencyStopped;
    /** {@code RideController.StopCause} name while stopped, else empty. */
    public final String stopCause;
    public final int carsPerTrain;
    public final int maxTrains;
    public final int blockCount;
    /** The server's answer to the last action, shown on the panel; empty for none. */
    public final String message;
    public final List<TrainRow> trains;
    public final List<SettingRow> settings;

    /** The ride's transfer table: none, laid but not linked to storage, or linked. */
    public static final int NO_TRANSFER = -1;
    public static final int TRANSFER_UNLINKED = 0;
    public static final int TRANSFER_LINKED = 1;

    public int transfer = NO_TRANSFER;
    /** {@code RideController.TransferRequest} ordinal. */
    public int transferRequest;
    /** The train in storage, or {@code -1}. */
    public int storedTrain = -1;

    /** This view with its transfer table's state filled in. */
    public RideView withTransfer(int transfer, int request, int storedTrain) {
        this.transfer = transfer;
        this.transferRequest = request;
        this.storedTrain = storedTrain;
        return this;
    }

    public RideView(int sectionId, String name, int state, int dispatchMode,
                    boolean emergencyStopped, int carsPerTrain, int maxTrains, int blockCount,
                    String message, List<TrainRow> trains, List<SettingRow> settings) {
        this(sectionId, name, state, dispatchMode, emergencyStopped, "", carsPerTrain, maxTrains,
            blockCount, message, trains, settings);
    }

    public RideView(int sectionId, String name, int state, int dispatchMode,
                    boolean emergencyStopped, String stopCause, int carsPerTrain, int maxTrains,
                    int blockCount, String message, List<TrainRow> trains, List<SettingRow> settings) {
        this.stopCause = stopCause == null ? "" : stopCause;
        this.sectionId = sectionId;
        this.name = name == null ? "" : name;
        this.state = state;
        this.dispatchMode = dispatchMode;
        this.emergencyStopped = emergencyStopped;
        this.carsPerTrain = carsPerTrain;
        this.maxTrains = maxTrains;
        this.blockCount = blockCount;
        this.message = message == null ? "" : message;
        this.trains = Collections.unmodifiableList(new ArrayList<>(trains));
        this.settings = Collections.unmodifiableList(new ArrayList<>(settings));
    }

    void write(ByteBuf buf) {
        buf.writeInt(sectionId);
        writeString(buf, name);
        buf.writeByte(state);
        buf.writeByte(dispatchMode);
        buf.writeBoolean(emergencyStopped);
        writeString(buf, stopCause);
        buf.writeByte(carsPerTrain);
        buf.writeShort(maxTrains);
        buf.writeShort(blockCount);
        writeString(buf, message);
        buf.writeShort(trains.size());
        for (TrainRow row : trains) {
            buf.writeInt(row.trainId);
            buf.writeByte(row.cars);
            buf.writeDouble(row.speed);
            writeString(buf, row.status);
            buf.writeDouble(row.distance);
        }
        buf.writeShort(settings.size());
        for (SettingRow row : settings) {
            buf.writeInt(row.elementIndex);
            buf.writeByte(row.parameter);
            buf.writeDouble(row.value);
        }
        buf.writeByte(transfer);
        buf.writeByte(transferRequest);
        buf.writeInt(storedTrain);
    }

    static RideView read(ByteBuf buf) {
        int sectionId = buf.readInt();
        String name = readString(buf);
        int state = buf.readByte();
        int mode = buf.readByte();
        boolean stopped = buf.readBoolean();
        String cause = readString(buf);
        int cars = buf.readByte();
        int maxTrains = buf.readShort();
        int blocks = buf.readShort();
        String message = readString(buf);
        int trainCount = buf.readShort();
        List<TrainRow> trains = new ArrayList<>(trainCount);
        for (int i = 0; i < trainCount; i++) {
            trains.add(new TrainRow(buf.readInt(), buf.readByte(), buf.readDouble(), readString(buf),
                buf.readDouble()));
        }
        int settingCount = buf.readShort();
        List<SettingRow> settings = new ArrayList<>(settingCount);
        for (int i = 0; i < settingCount; i++) {
            settings.add(new SettingRow(buf.readInt(), buf.readByte(), buf.readDouble()));
        }
        int transfer = buf.readByte();
        int request = buf.readByte();
        int storedTrain = buf.readInt();
        return new RideView(sectionId, name, state, mode, stopped, cause, cars, maxTrains, blocks, message,
            trains, settings).withTransfer(transfer, request, storedTrain);
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        byte[] bytes = new byte[buf.readShort()];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
