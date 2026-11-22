package com.pxokie.tiertagger.source;

import com.google.gson.JsonObject;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * pvptiers.com: same ranking objects as MCTiers, but different paths, undashed UUIDs only
 * and no lookup by name.
 */
public class PvpTiersSource implements TierSource {
    private static final String BASE = "https://pvptiers.com/api";

    @Override
    public CompletableFuture<List<GameMode>> fetchModes() {
        return Http.getJson(BASE + "/tierlists").thenApply(json -> {
            if (json == null || !json.isJsonObject()) return List.of();

            return json.getAsJsonObject().entrySet().stream().map(e -> {
                String title = GameMode.prettify(e.getKey());
                if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("title")) {
                    title = e.getValue().getAsJsonObject().get("title").getAsString();
                }
                return new GameMode(e.getKey(), title);
            }).toList();
        });
    }

    @Override
    public CompletableFuture<@Nullable PlayerInfo> fetchProfile(@Nullable UUID uuid, @Nullable String name) {
        CompletableFuture<UUID> id = uuid != null ? CompletableFuture.completedFuture(uuid) : Mojang.uuidOf(name == null ? "" : name);

        return id.thenCompose(u -> {
            if (u == null) return CompletableFuture.completedFuture(null);

            return Http.getJson(BASE + "/profile/" + Http.undashed(u)).thenApply(json -> {
                if (json == null || !json.isJsonObject()) return null;
                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("rankings")) return null;

                PlayerInfo info = Http.GSON.fromJson(obj, PlayerInfo.class);
                return info == null ? null : info.sanitised();
            });
        });
    }
}
