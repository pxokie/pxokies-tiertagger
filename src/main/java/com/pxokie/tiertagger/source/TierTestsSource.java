package com.pxokie.tiertagger.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * api.tiertests.com. Tiers are letter grades (S, A+, A-, ... F), converted to HT1..LT5.
 * Modern and legacy gamemodes are combined into one list.
 */
public class TierTestsSource implements TierSource {
    private static final String BASE = "https://api.tiertests.com/v1";

    @Override
    public CompletableFuture<List<GameMode>> fetchModes() {
        CompletableFuture<List<GameMode>> modern = fetchModes("modern");
        CompletableFuture<List<GameMode>> legacy = fetchModes("legacy").exceptionally(t -> List.of());

        return modern.thenCombine(legacy, (a, b) -> {
            List<GameMode> all = new ArrayList<>(a);
            all.addAll(b);
            return all;
        });
    }

    private CompletableFuture<List<GameMode>> fetchModes(String edition) {
        return Http.getJson(BASE + "/gamemodes/" + edition).thenApply(json -> {
            List<GameMode> modes = new ArrayList<>();
            if (json == null || !json.isJsonObject() || !(json.getAsJsonObject().get("data") instanceof JsonArray data)) return modes;

            for (JsonElement e : data) {
                if (!e.isJsonObject()) continue;
                JsonObject mode = e.getAsJsonObject();
                String id = Tiers.str(mode, "name");
                if (id == null || Tiers.bool(mode, "archived")) continue;

                String title = Tiers.str(mode, "beautifiedName");
                if ("legacy".equals(edition)) title = (title == null ? GameMode.prettify(id) : title) + " (Legacy)";
                modes.add(new GameMode(id, title == null ? GameMode.prettify(id) : title));
            }
            return modes;
        });
    }

    @Override
    public CompletableFuture<@Nullable PlayerInfo> fetchProfile(@Nullable UUID uuid, @Nullable String name) {
        CompletableFuture<UUID> id = uuid != null ? CompletableFuture.completedFuture(uuid) : Mojang.uuidOf(name == null ? "" : name);

        return id.thenCompose(u -> {
            if (u == null) return CompletableFuture.completedFuture(null);

            CompletableFuture<JsonArray> modern = fetchTiers(u, "MODERN");
            CompletableFuture<JsonArray> legacy = fetchTiers(u, "LEGACY").exceptionally(t -> null);

            return modern.thenCombine(legacy, (m, l) -> build(u, m, l));
        });
    }

    private CompletableFuture<@Nullable JsonArray> fetchTiers(UUID uuid, String version) {
        // TierTests only accepts dashed UUIDs
        return Http.getJson(BASE + "/tiers/current/all?minecraftUuid=" + Http.dashed(uuid) + "&version=" + version)
                .thenApply(json -> {
                    if (json == null || !json.isJsonObject()) return null;
                    JsonElement data = json.getAsJsonObject().get("data");
                    return data != null && data.isJsonArray() ? data.getAsJsonArray() : null;
                });
    }

    private @Nullable PlayerInfo build(UUID uuid, @Nullable JsonArray modern, @Nullable JsonArray legacy) {
        Map<String, PlayerInfo.Ranking> rankings = new LinkedHashMap<>();
        String name = null;
        String region = null;
        int points = 0;
        int rank = 0;

        for (JsonArray array : new JsonArray[]{modern, legacy}) {
            if (array == null) continue;

            for (JsonElement e : array) {
                if (!e.isJsonObject()) continue;
                JsonObject entry = e.getAsJsonObject();
                JsonObject user = Tiers.obj(entry, "user");
                JsonObject gamemode = Tiers.obj(entry, "gamemode");
                String modeId = Tiers.str(gamemode, "name");

                if (name == null) name = Tiers.str(user, "username");
                if (region == null) region = Tiers.str(user, "region");
                if (array == modern && points == 0) {
                    JsonObject r = Tiers.obj(entry, "rankModern");
                    points = (int) Math.round(Tiers.num(r, "points"));
                    rank = (int) Tiers.num(r, "rank");
                }

                PlayerInfo.Ranking ranking = Tiers.ranking(Tiers.str(entry, "tier"), null,
                        Tiers.bool(entry, "retired"), parseDate(Tiers.str(entry, "createdAt")));
                if (modeId != null && ranking != null) rankings.put(modeId, ranking);
            }
        }

        if (rankings.isEmpty() && name == null) return null;
        return new PlayerInfo(uuid.toString(), name, rankings, region, points, rank, List.of(), false).sanitised();
    }

    private static long parseDate(@Nullable String s) {
        try {
            return s == null ? 0 : Instant.parse(s).getEpochSecond();
        } catch (Exception e) {
            return 0;
        }
    }
}
