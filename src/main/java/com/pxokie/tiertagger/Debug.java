package com.pxokie.tiertagger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes one line to the log the first time each hook is used. Clients like Lunar skip some of
 * Minecraft's normal code, so this shows in the log which hooks actually run there.
 */
public final class Debug {
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    public static void hit(String hook, String detail) {
        if (SEEN.add(hook)) {
            String d = detail == null ? "" : detail.replace('\n', ' ');
            if (d.length() > 80) d = d.substring(0, 80);
            System.out.println("[Pxokie's TierTagger] hook active: " + hook + " (" + d + ")");
        }
    }

    private Debug() {
    }
}
