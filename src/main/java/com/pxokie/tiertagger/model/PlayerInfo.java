package com.pxokie.tiertagger.model;

import com.google.gson.annotations.SerializedName;
import com.pxokie.tiertagger.TierCache;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record PlayerInfo(String uuid, String name, Map<String, Ranking> rankings, @Nullable String region, int points,
                         int overall, List<Badge> badges, @SerializedName("combat_master") boolean combatMaster) {
    public record Ranking(int tier, int pos, @Nullable @SerializedName("peak_tier") Integer peakTier,
                          @Nullable @SerializedName("peak_pos") Integer peakPos, long attained,
                          boolean retired) {

        /**
         * Lower is better.
         */
        public int comparableTier() {
            return comparable(tier, pos);
        }

        /**
         * Lower is better.
         */
        public int comparablePeak() {
            if (peakTier == null || peakPos == null) {
                return Integer.MAX_VALUE;
            } else {
                return comparable(peakTier, peakPos);
            }
        }

        /**
         * pos 0 = high, 2 = middle (MCPvP only), 1 = low.
         */
        private static int comparable(int tier, int pos) {
            int order = switch (pos) {
                case 0 -> 0;
                case 2 -> 1;
                default -> 2;
            };
            return tier * 3 + order;
        }

        public NamedRanking asNamed(GameMode mode) {
            return new NamedRanking(mode, this);
        }
    }

    public record NamedRanking(@Nullable GameMode mode, Ranking ranking) {
    }

    public record Badge(String title, String desc) {
    }

    private static final Map<String, Integer> REGION_COLORS = Map.of(
            "NA", 0xff6a6e,
            "EU", 0x6aff6e,
            "SA", 0xff9900,
            "AU", 0xf6b26b,
            "ME", 0xffd966,
            "AS", 0xc27ba0,
            "AF", 0x674ea7
    );

    /**
     * Replaces nulls left behind by JSON parsing with safe defaults, and drops invalid tiers.
     */
    public PlayerInfo sanitised() {
        Map<String, Ranking> clean = new LinkedHashMap<>();
        if (rankings != null) {
            rankings.forEach((k, v) -> {
                if (k != null && v != null && v.tier() >= 1 && v.tier() <= 5) clean.put(k, v);
            });
        }

        return new PlayerInfo(uuid, name == null ? "?" : name, clean, region, points, overall,
                badges == null ? List.of() : badges, combatMaster);
    }

    public boolean hasRegion() {
        return region != null && !region.isBlank();
    }

    public int getRegionColor() {
        return hasRegion() ? REGION_COLORS.getOrDefault(this.region.toUpperCase(Locale.ROOT), 0xffffff) : 0xffffff;
    }

    public static Optional<NamedRanking> getHighestRanking(TierList list, Map<String, Ranking> rankings) {
        return rankings.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .min(Comparator.comparingInt(e -> e.getValue().comparableTier()))
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(list, e.getKey())));
    }

    @Getter
    @AllArgsConstructor
    public enum PointInfo {
        COMBAT_GRANDMASTER("Combat Grandmaster", 0xE6C622, 0xFDE047),
        COMBAT_MASTER("Combat Master", 0xFBB03B, 0xFFD13A),
        COMBAT_ACE("Combat Ace", 0xCD285C, 0xD65474),
        COMBAT_SPECIALIST("Combat Specialist", 0xAD78D8, 0xC7A3E8),
        COMBAT_CADET("Combat Cadet", 0x9291D9, 0xADACE2),
        COMBAT_NOVICE("Combat Novice", 0x9291D9, 0xFFFFFF),
        ROOKIE("Rookie", 0x6C7178, 0x8B979C),
        UNRANKED("Unranked", 0xFFFFFF, 0xFFFFFF);

        private final String title;
        private final int color;
        private final int accentColor;
    }

    public PointInfo getPointInfo() {
        if (this.points >= 400) {
            return PointInfo.COMBAT_GRANDMASTER;
        } else if (this.points >= 250) {
            return PointInfo.COMBAT_MASTER;
        } else if (this.points >= 100) {
            return PointInfo.COMBAT_ACE;
        } else if (this.points >= 50) {
            return PointInfo.COMBAT_SPECIALIST;
        } else if (this.points >= 20) {
            return PointInfo.COMBAT_CADET;
        } else if (this.points >= 10) {
            return PointInfo.COMBAT_NOVICE;
        } else if (this.points >= 1) {
            return PointInfo.ROOKIE;
        } else {
            return PointInfo.UNRANKED;
        }
    }

    public List<NamedRanking> getSortedTiers(TierList list) {
        List<NamedRanking> tiers = new ArrayList<>(this.rankings.entrySet().stream()
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(list, e.getKey())))
                .toList());

        tiers.sort(Comparator.comparing((NamedRanking a) -> a.ranking.retired, Boolean::compare)
                .thenComparingInt(a -> a.ranking.comparableTier()));

        return tiers;
    }
}
