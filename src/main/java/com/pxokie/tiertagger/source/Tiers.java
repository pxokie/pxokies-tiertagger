package com.pxokie.tiertagger.source;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pxokie.tiertagger.model.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Converts the tier notations of the different sites to TierTagger's (tier, pos).
 * pos: 0 = high (HT), 1 = low (LT), 2 = middle (MT, only used by MCPvP).
 */
public final class Tiers {
    public static final int HIGH = 0;
    public static final int LOW = 1;
    public static final int MIDDLE = 2;

    /**
     * TierTests letter grades, mapped onto the HT1..LT5 scale.
     */
    private static final Map<String, int[]> LETTER_GRADES = Map.of(
            "S", new int[]{1, HIGH},
            "A_PLUS", new int[]{1, LOW},
            "A_MINUS", new int[]{2, HIGH},
            "B_PLUS", new int[]{2, LOW},
            "B_MINUS", new int[]{3, HIGH},
            "C_PLUS", new int[]{3, LOW},
            "C_MINUS", new int[]{4, HIGH},
            "D_PLUS", new int[]{4, LOW},
            "D_MINUS", new int[]{5, HIGH},
            "F", new int[]{5, LOW}
    );

    /**
     * Parses "HT3", "MT2", "LT5" or a TierTests letter grade. Returns {tier, pos} or null.
     */
    public static int @Nullable [] parse(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT).replace('+', '_');

        int[] grade = LETTER_GRADES.get(t);
        if (grade != null) return grade;

        if (t.length() == 3 && t.charAt(1) == 'T' && Character.isDigit(t.charAt(2))) {
            int tier = t.charAt(2) - '0';
            if (tier < 1 || tier > 5) return null;
            return switch (t.charAt(0)) {
                case 'H' -> new int[]{tier, HIGH};
                case 'L' -> new int[]{tier, LOW};
                case 'M' -> new int[]{tier, MIDDLE};
                default -> null;
            };
        }
        return null;
    }

    public static @Nullable PlayerInfo.Ranking ranking(@Nullable String tier, @Nullable String peak, boolean retired, long attained) {
        int[] t = parse(tier);
        if (t == null) return null;
        int[] p = parse(peak);
        return new PlayerInfo.Ranking(t[0], t[1], p == null ? null : p[0], p == null ? null : p[1], attained, retired);
    }

    // small JSON helpers that never throw

    public static @Nullable String str(@Nullable JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    public static @Nullable JsonObject obj(@Nullable JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static double num(@Nullable JsonObject o, String key) {
        try {
            String s = str(o, key);
            return s == null ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean bool(@Nullable JsonObject o, String key) {
        String s = str(o, key);
        return "true".equalsIgnoreCase(s);
    }

    private Tiers() {
    }
}
