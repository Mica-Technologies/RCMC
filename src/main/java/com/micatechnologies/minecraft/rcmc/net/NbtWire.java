package com.micatechnologies.minecraft.rcmc.net;

import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Compressed NBT on the wire, with no size cap, for the packets that carry a whole world's
 * authored state.
 *
 * <p>{@code ByteBufUtils.readTag} reads through vanilla's 2 MB {@code NBTSizeTracker}, which a
 * park of a few city-scale metro lines outgrows (each node costs a couple of hundred tracked
 * bytes). Past it the client throws reading the packet and never receives the track at all. The
 * sender is the server and the payload is the server's own saved state, so the cap guards against
 * nothing here. Gzip on top, because node coordinates compress well and this is sent to every
 * player in the dimension on every edit; Forge splits a large payload across packets itself.</p>
 */
public final class NbtWire {

    private NbtWire() {
        throw new AssertionError("No instances.");
    }

    /** Writes {@code tag}, which may be {@code null}. */
    public static void write(ByteBuf buf, NBTTagCompound tag) {
        if (tag == null) {
            buf.writeInt(-1);
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            CompressedStreamTools.writeCompressed(tag, bytes);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        buf.writeInt(bytes.size());
        buf.writeBytes(bytes.toByteArray());
    }

    /** Reads what {@link #write} wrote; {@code null} if it wrote {@code null}. */
    public static NBTTagCompound read(ByteBuf buf) {
        int length = buf.readInt();
        if (length < 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        try {
            // readCompressed reads without a size tracker; see the class javadoc for why that is safe.
            return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
