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

    /**
     * The account for a name that was already looked up, only when the spelling matches the real
     * name exactly (so normal words in chat don't count as names). Never starts a lookup.
     */
    @Nullable
    public static UUID cachedExact(String name) {
        UUID uuid = NAME_TO_UUID.get(name.toLowerCase());
        if (uuid == null) return null;
        String real = UUID_TO_NAME.get(uuid);
        return name.equals(real) ? uuid : null;
    }

    /**
     * Names Mojang said don't exist, so they aren't looked up again.
     */
    private static final java.util.Set<String> UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> PENDING = ConcurrentHashMap.newKeySet();
    /**
     * At most one new lookup per this many milliseconds, to stay under Mojang's rate limit.
     */
    private static final long MIN_GAP_MS = 400;
    private static volatile long lastLookup = 0;

    /**
     * The UUID for a name if it was already looked up, without waiting. Starts a lookup in the
     * background when it wasn't (rate limited), so a later call can return it.
     */
    @Nullable
    public static UUID cachedOrRequest(String name) {
        String key = name.toLowerCase();
        UUID cached = NAME_TO_UUID.get(key);
        if (cached != null || UNKNOWN.contains(key) || PENDING.contains(key)) return cached;

        long now = System.currentTimeMillis();
        if (now - lastLookup < MIN_GAP_MS) return null;
        lastLookup = now;

        PENDING.add(key);
        uuidOf(name).whenComplete((uuid, t) -> {
            PENDING.remove(key);
            if (t == null && uuid == null) UNKNOWN.add(key);
        });
        return null;
    }

    /**
     * Looks a name up now (still respecting the cache), for chat messages that need the answer.
     */
    public static CompletableFuture<@Nullable UUID> lookup(String name) {
        String key = name.toLowerCase();
        UUID cached = NAME_TO_UUID.get(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        if (UNKNOWN.contains(key)) return CompletableFuture.completedFuture(null);

        lastLookup = System.currentTimeMillis();
        return uuidOf(name).whenComplete((uuid, t) -> {
            if (t == null && uuid == null) UNKNOWN.add(key);
        });
    }

    public static void remember(UUID uuid, String name) {
        NAME_TO_UUID.put(name.toLowerCase(), uuid);
        UUID_TO_NAME.put(uuid, name);
    }

    private Mojang() {
    }
}
