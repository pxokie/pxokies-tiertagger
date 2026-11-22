package com.pxokie.tiertagger.source;

import com.google.gson.JsonObject;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The API format used by MCTiers and by the tierlists that share its backend
 * ({@code /v2/mode/list}, {@code /v2/profile/<uuid>}, {@code /v2/profile/by-name/<name>}).
 * This is the exact format the original TierTagger reads.
 */
public class McTiersSource implements TierSource {
    private final String baseUrl;

    public McTiersSource(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public CompletableFuture<List<GameMode>> fetchModes() {
        return Http.getJson(baseUrl + "/v2/mode/list").thenApply(json -> {
            if (json == null || !json.isJsonObject()) return List.of();
            JsonObject obj = json.getAsJsonObject();

            return obj.entrySet().stream().map(e -> {
                String title = e.getKey();
                if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("title")) {
                    title = e.getValue().getAsJsonObject().get("title").getAsString();
                }
                return new GameMode(e.getKey(), title);
            }).toList();
        });
    }

    @Override
    public CompletableFuture<@Nullable PlayerInfo> fetchProfile(@Nullable UUID uuid, @Nullable String name) {
        String url = uuid != null
                ? baseUrl + "/v2/profile/" + Http.dashed(uuid)
                : baseUrl + "/v2/profile/by-name/" + Http.encode(name == null ? "" : name);

        return Http.getJson(url).thenApply(json -> {
            if (json == null || !json.isJsonObject()) return null;
            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("rankings")) return null;

            PlayerInfo info = Http.GSON.fromJson(obj, PlayerInfo.class);
            return info == null ? null : info.sanitised();
        });
    }
}
