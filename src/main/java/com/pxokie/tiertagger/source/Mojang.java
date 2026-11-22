package com.pxokie.tiertagger.source;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name &lt;-&gt; UUID lookups, for tierlists that only accept one of the two.
 */
public final class Mojang {
    private static final Map<String, UUID> NAME_TO_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, String> UUID_TO_NAME = new ConcurrentHashMap<>();

    public static CompletableFuture<@Nullable UUID> uuidOf(String name) {
        UUID cached = NAME_TO_UUID.get(name.toLowerCase());
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return Http.getJson("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + Http.encode(name))
                .thenApply(json -> {
                    if (json == null || !json.isJsonObject()) return null;
                    JsonObject obj = json.getAsJsonObject();
                    UUID uuid = obj.has("id") ? Http.parseUUID(obj.get("id").getAsString()) : null;
                    if (uuid != null) remember(uuid, obj.has("name") ? obj.get("name").getAsString() : name);
                    return uuid;
                });
    }

    public static CompletableFuture<@Nullable String> nameOf(UUID uuid) {
        String cached = UUID_TO_NAME.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        // players on the server you're on: the tab list already knows the name
        try {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(uuid);
                if (info != null && info.getProfile().name() != null) {
                    String name = info.getProfile().name();
                    remember(uuid, name);
                    return CompletableFuture.completedFuture(name);
                }
            }
        } catch (Exception ignored) {
            // fall back to Mojang below
        }

        return Http.getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + Http.undashed(uuid))
                .thenApply(json -> {
                    if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("name")) return null;
                    String name = json.getAsJsonObject().get("name").getAsString();
                    remember(uuid, name);
                    return name;
                });
    }

    public static void remember(UUID uuid, String name) {
        NAME_TO_UUID.put(name.toLowerCase(), uuid);
        UUID_TO_NAME.put(uuid, name);
    }

    private Mojang() {
    }
}
