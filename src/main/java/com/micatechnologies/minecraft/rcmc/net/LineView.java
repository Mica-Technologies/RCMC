package com.micatechnologies.minecraft.rcmc.net;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Everything the line control desk shows about one metro line, as the server sees it right now.
 *
 * <p>Plain data, as {@link RideView} is for the coaster panel: the server builds it, the packet
 * carries it, the GUI only reads it.</p>
 */
public final class LineView {

    /** One train in service on the line. */
    public static final class TrainRow {
        public final int trainId;
        public final String direction;
        public final String doing;
        public final double speed;
        public final boolean held;
        /** Why it is not just running — head-on, or stopping for a train ahead — or empty. */
        public final String why;
        public final boolean headOn;

        public TrainRow(int trainId, String direction, String doing, double speed, boolean held, String why,
                        boolean headOn) {
            this.trainId = trainId;
            this.direction = direction;
            this.doing = doing;
            this.speed = speed;
            this.held = held;
            this.why = why;
            this.headOn = headOn;
        }
    }

    /** The line shown; empty when there are no lines at all. */
    public final String line;
    /** Which of how many lines this is, for the header: 1-based. */
    public final int index;
    public final int lineCount;
    /** "loop", "shuttle", "turnback". */
    public final String kind;
    /** Its stations in order, joined for display. */
    public final String route;
    public final int dwellSeconds;
    /** 0 for off. */
    public final int headwaySeconds;
    /** Signal blocks on the line; 0 for unsignalled. */
    public final int blocks;
    /** Cars a train added from the desk gets. */
    public final int cars;
    public final List<TrainRow> trains;
    /** The server's answer to the last press; empty for none. */
    public final String message;

    public LineView(String line, int index, int lineCount, String kind, String route, int dwellSeconds,
                    int headwaySeconds, int blocks, int cars, List<TrainRow> trains, String message) {
        this.line = line;
        this.index = index;
        this.lineCount = lineCount;
        this.kind = kind;
        this.route = route;
        this.dwellSeconds = dwellSeconds;
        this.headwaySeconds = headwaySeconds;
        this.blocks = blocks;
        this.cars = cars;
        this.trains = Collections.unmodifiableList(new ArrayList<>(trains));
        this.message = message;
    }

    public void write(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, line);
        buf.writeShort(index);
        buf.writeShort(lineCount);
        ByteBufUtils.writeUTF8String(buf, kind);
        ByteBufUtils.writeUTF8String(buf, route);
        buf.writeInt(dwellSeconds);
        buf.writeInt(headwaySeconds);
        buf.writeShort(blocks);
        buf.writeByte(cars);
        buf.writeShort(trains.size());
        for (TrainRow row : trains) {
            buf.writeInt(row.trainId);
            ByteBufUtils.writeUTF8String(buf, row.direction);
            ByteBufUtils.writeUTF8String(buf, row.doing);
            buf.writeDouble(row.speed);
            buf.writeBoolean(row.held);
            ByteBufUtils.writeUTF8String(buf, row.why);
            buf.writeBoolean(row.headOn);
        }
        ByteBufUtils.writeUTF8String(buf, message);
    }

    public static LineView read(ByteBuf buf) {
        String line = ByteBufUtils.readUTF8String(buf);
        int index = buf.readShort();
        int lineCount = buf.readShort();
        String kind = ByteBufUtils.readUTF8String(buf);
        String route = ByteBufUtils.readUTF8String(buf);
        int dwell = buf.readInt();
        int headway = buf.readInt();
        int blocks = buf.readShort();
        int cars = buf.readByte();
        int count = buf.readShort();
        List<TrainRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new TrainRow(buf.readInt(), ByteBufUtils.readUTF8String(buf), ByteBufUtils.readUTF8String(buf),
                buf.readDouble(), buf.readBoolean(), ByteBufUtils.readUTF8String(buf), buf.readBoolean()));
        }
        return new LineView(line, index, lineCount, kind, route, dwell, headway, blocks, cars, rows,
            ByteBufUtils.readUTF8String(buf));
    }
}
