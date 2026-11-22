package com.pxokie.tiertagger.source;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Http {
    public static final Gson GSON = new Gson();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String USER_AGENT = "PxokiesTierTagger/1.0 (Minecraft mod)";

    /**
     * GETs a JSON document. Completes with {@code null} for 404 / empty bodies (player not ranked),
     * and exceptionally for other HTTP errors.
     */
    public static CompletableFuture<@Nullable JsonElement> getJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    int code = r.statusCode();
                    if (code == 404 || code == 204) return null;
                    if (code >= 400) throw new IllegalStateException("HTTP " + code + " from " + url);

                    String body = r.body();
                    if (body == null || body.isBlank()) return null;
                    JsonElement json = Http.GSON.fromJson(body, JsonElement.class);
                    return json == null || json.isJsonNull() ? null : json;
                });
    }

    public static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String dashed(UUID uuid) {
        return uuid.toString();
    }

    public static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    @Nullable
    public static UUID parseUUID(@Nullable String uuid) {
        if (uuid == null) return null;
        try {
            if (uuid.length() == 32) {
                long most = Long.parseUnsignedLong(uuid.substring(0, 16), 16);
                long least = Long.parseUnsignedLong(uuid.substring(16), 16);
                return new UUID(most, least);
            }
            return UUID.fromString(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    private Http() {
    }
}
