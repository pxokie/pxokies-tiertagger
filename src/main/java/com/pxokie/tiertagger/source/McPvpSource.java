package com.pxokie.tiertagger.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * mcpvp.com. It can only search by name (fuzzy), so the results are matched back to the UUID.
 * Uses high / middle / low tiers (HT3, MT3, LT3).
 */
public class McPvpSource implements TierSource {
    private static final String SEARCH = "https://www.mcpvp.com/tiers/search?kit=overall&include_retired=1&q=";

    private static final List<GameMode> MODES = List.of(
            new GameMode("sword", "Sword"), new GameMode("mace", "Mace"), new GameMode("spear", "Spear"),
            new GameMode("shield", "Shield"), new GameMode("pot", "Pot"), new GameMode("netherite-pot", "Netherite Pot"),
            new GameMode("uhc", "UHC"), new GameMode("smp", "SMP"), new GameMode("diamond-smp", "Diamond SMP"),
            new GameMode("crystal", "Crystal"), new GameMode("cart", "Cart"), new GameMode("creeper", "Creeper"),
            new GameMode("bow", "Bow"), new GameMode("early-game", "Early Game"), new GameMode("late-game", "Late Game"),
            new GameMode("end-game", "End Game"));

    @Override
    public CompletableFuture<List<GameMode>> fetchModes() {
        return CompletableFuture.completedFuture(MODES);
    }

    @Override
    public CompletableFuture<@Nullable PlayerInfo> fetchProfile(@Nullable UUID uuid, @Nullable String name) {
        CompletableFuture<String> nameFuture = name != null ? CompletableFuture.completedFuture(name)
                : uuid != null ? Mojang.nameOf(uuid) : CompletableFuture.completedFuture(null);

        return nameFuture.thenCompose(n -> {
            if (n == null || !n.matches("[A-Za-z0-9_]{1,16}")) return CompletableFuture.completedFuture(null);

            return Http.getJson(SEARCH + Http.encode(n)).thenApply(json -> {
                if (json == null || !json.isJsonObject() || !(json.getAsJsonObject().get("players") instanceof JsonArray players)) {
                    return null;
                }
                JsonObject match = find(players, uuid, n);
                return match == null ? null : parse(match);
            });
        });
    }

    private static @Nullable JsonObject find(JsonArray players, @Nullable UUID uuid, String name) {
        JsonObject byName = null;
        for (JsonElement e : players) {
            if (!e.isJsonObject()) continue;
            JsonObject p = e.getAsJsonObject();
            UUID id = Http.parseUUID(Tiers.str(p, "uuid"));
            if (uuid != null && uuid.equals(id)) return p;
            if (byName == null && name.equalsIgnoreCase(Tiers.str(p, "name"))) byName = p;
        }
        // when we know the UUID, only trust an exact UUID match (names can change hands)
        return uuid == null ? byName : null;
    }

    private static PlayerInfo parse(JsonObject p) {
        Map<String, PlayerInfo.Ranking> rankings = new LinkedHashMap<>();
        JsonObject ranks = Tiers.obj(p, "kitRanks");
        JsonObject peaks = Tiers.obj(p, "kitPeakRanks");
        JsonObject retired = Tiers.obj(p, "kitRetired");

        if (ranks != null) {
            for (String kit : ranks.keySet()) {
                PlayerInfo.Ranking r = Tiers.ranking(Tiers.str(ranks, kit), Tiers.str(peaks, kit), Tiers.bool(retired, kit), 0);
                if (r != null) rankings.put(kit, r);
            }
        }

        UUID uuid = Http.parseUUID(Tiers.str(p, "uuid"));
        return new PlayerInfo(uuid == null ? Tiers.str(p, "uuid") : uuid.toString(), Tiers.str(p, "name"), rankings,
                Tiers.str(p, "region"), (int) Math.round(Tiers.num(p, "points")), (int) Tiers.num(p, "rank"),
                List.of(), false).sanitised();
    }
}
