package com.micatechnologies.minecraft.rcmc.net;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

/**
 * What the track editor screen shows: one section, one node of it, and the span that starts there.
 * Built by the server, which owns the track; the screen only ever displays it and sends presses back.
 */
public final class TrackEditView {

    public final int sectionId;
    public final int nodeIndex;
    public final int nodeCount;
    public final boolean closed;
    public final double length;
    public final double x;
    public final double y;
    public final double z;
    public final double bank;
    /** {@code TrackBuildSession.SegmentType} ordinal of the span from this node, or -1 if none. */
    public final int spanType;
    /** {@code TrackPalette.Part} ordinal being painted, and that part's colour's label. */
    public final int paintPart;
    public final String paintColour;
    /** The server's answer to the last press; empty for none. */
    public final String message;

    public TrackEditView(int sectionId, int nodeIndex, int nodeCount, boolean closed, double length,
                         double x, double y, double z, double bank, int spanType, int paintPart,
                         String paintColour, String message) {
        this.sectionId = sectionId;
        this.nodeIndex = nodeIndex;
        this.nodeCount = nodeCount;
        this.closed = closed;
        this.length = length;
        this.x = x;
        this.y = y;
        this.z = z;
        this.bank = bank;
        this.spanType = spanType;
        this.paintPart = paintPart;
        this.paintColour = paintColour == null ? "" : paintColour;
        this.message = message == null ? "" : message;
    }

    void write(ByteBuf buf) {
        buf.writeInt(sectionId);
        buf.writeShort(nodeIndex);
        buf.writeShort(nodeCount);
        buf.writeBoolean(closed);
        buf.writeDouble(length);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(bank);
        buf.writeByte(spanType);
        buf.writeByte(paintPart);
        writeString(buf, paintColour);
        writeString(buf, message);
    }

    static TrackEditView read(ByteBuf buf) {
        return new TrackEditView(buf.readInt(), buf.readShort(), buf.readShort(), buf.readBoolean(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readByte(), buf.readByte(), readString(buf), readString(buf));
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
