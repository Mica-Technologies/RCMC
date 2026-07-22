package com.micatechnologies.minecraft.rcmc.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;

/**
 * Speaks a station announcement, borrowing CSM's text-to-speech engine when CSM is installed and
 * falling back to an on-screen subtitle when it is not.
 *
 * <h3>Coexistence without a dependency</h3>
 *
 * <p>CSM ships MaryTTS (a heavyweight synthesiser) already shaded into its jar. Rather than bundle
 * a second copy — which would collide on the classpath and load the engine twice — RCMC detects
 * CSM at runtime ({@code Loader.isModLoaded("csm")}) and calls its client-side
 * {@code CsmTts.say(text, voice)} through <b>reflection</b>. No compile-time reference, so RCMC
 * neither depends on CSM nor breaks when CSM is absent, and CSM stays free of any RCMC reference —
 * the coexistence contract the project owner set. The reflected handle is resolved once and cached;
 * the resolution and every call are defensive, because another mod's internals are not a stable API.</p>
 *
 * <p><b>Fallback.</b> With no CSM present there is no synthesiser, so the announcement is shown as a
 * subtitle (the action-bar overlay message) instead of spoken. A standalone MaryTTS bundled into
 * RCMC — full package relocation so it can coexist with CSM's copy — is the deferred path recorded
 * in the plan; until then, text is the honest degraded mode.</p>
 *
 * <p>Client-only, and reached exclusively through {@code RcmcClientProxy.speakTts}. Nothing in
 * common code names this class.</p>
 */
public final class TtsBridge {

    private static boolean resolved;
    private static Method sayMethod;
    private static Method defaultVoiceMethod;

    private TtsBridge() {
        throw new AssertionError("No instances.");
    }

    /**
     * Speaks {@code text}. {@code voice} may be empty to use the engine's default voice. Never
     * throws — a failure to synthesise degrades to a subtitle rather than interrupting the client.
     */
    public static void speak(String text, String voice) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (resolveCsm()) {
            try {
                String chosen = voice == null || voice.isEmpty() ? defaultVoice() : voice;
                sayMethod.invoke(null, text, chosen);
                return;
            }
            catch (Throwable ignored) {
                // CSM present but its TTS refused — show the words instead of nothing.
            }
        }
        subtitle(text);
    }

    /** Resolves and caches CSM's {@code CsmTts.say} handle, or records its absence. */
    private static boolean resolveCsm() {
        if (resolved) {
            return sayMethod != null;
        }
        resolved = true;
        if (!Loader.isModLoaded("csm")) {
            return false;
        }
        try {
            Class<?> csmTts = Class.forName(
                "com.micatechnologies.minecraft.csm.codeutils.CsmTts");
            sayMethod = csmTts.getMethod("say", String.class, String.class);
            try {
                defaultVoiceMethod = csmTts.getMethod("getDefaultVoice");
            }
            catch (NoSuchMethodException ignored) {
                defaultVoiceMethod = null;
            }
            return true;
        }
        catch (Throwable t) {
            // CSM present but its TTS class moved or changed shape — treat as unavailable.
            sayMethod = null;
            return false;
        }
    }

    private static String defaultVoice() {
        if (defaultVoiceMethod != null) {
            try {
                Object result = defaultVoiceMethod.invoke(null);
                if (result instanceof String) {
                    return (String) result;
                }
            }
            catch (Throwable ignored) {
                // fall through to CSM's own documented default
            }
        }
        return "cmu-slt-hsmm";
    }

    private static void subtitle(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.ingameGUI != null) {
            mc.ingameGUI.setOverlayMessage(text, false);
        }
    }
}
