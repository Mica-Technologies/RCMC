package com.micatechnologies.minecraft.rcmc.sound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A timeline of every spoken announcement, from the server deciding to make it to the client
 * handing it to a speech engine.
 *
 * <p>Speech is heard, not seen, so nothing else shows whether an announcement went out late, twice,
 * on top of another, or never reached the synthesiser. Each stage logs one line stamped with the
 * wall clock in milliseconds ({@code t=}): an integrated server, its client and CSM's playback thread
 * share one clock, so lines from all three line up. CSM's engine logs the stages after the handoff
 * (synthesis, playback, and any message it drops) the same way under the {@code CSM-TTS} logger.</p>
 *
 * <p>Stages are logged at DEBUG, which Forge writes to {@code logs/debug.log} and keeps out of
 * {@code latest.log}: a park with many speakers would otherwise flood it. Grep for {@code [tts]}.</p>
 */
public final class TtsTrace {

    private static final Logger LOGGER = LogManager.getLogger("rcmc-tts");

    private TtsTrace() {
        throw new AssertionError("No instances.");
    }

    /**
     * Logs one stage of one announcement.
     *
     * @param stage  what happened: CHIME, SEND, RECV, HANDOFF
     * @param source where the announcement comes from, e.g. {@code train#3} or {@code speaker@10,64,-5}
     * @param detail anything else worth knowing at this stage, or empty
     * @param text   the words, or empty for a chime
     */
    public static void stage(String stage, String source, String detail, String text) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[tts] t={} {} {}{} \"{}\"", System.currentTimeMillis(), stage, source,
                detail == null || detail.isEmpty() ? "" : " " + detail, text == null ? "" : text);
        }
    }
}
