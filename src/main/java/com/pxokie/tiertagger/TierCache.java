package com.pxokie.tiertagger;

import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import com.pxokie.tiertagger.model.TierList;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TierCache {
    /**
     * How long fetched tiers are trusted before they are fetched again, so that
     * nametags always show a player's most recent tiers.
     */
    private static final long REFRESH_AFTER_MS = 5 * 60 * 1000L;
    /**
     * Wait before retrying a tierlist that errored (site down, rate limited...).
     */
    private static final long RETRY_AFTER_ERROR_MS = 60 * 1000L;

    private static final List<String> MODE_ORDER = List.of(
            "crystal", "sword", "uhc", "pot", "nethpot", "smp", "axe", "mace", "spear");

    private static final Map<TierList, List<GameMode>> GAMEMODES = new ConcurrentHashMap<>();
    private static final Map<TierList, Map<UUID, Entry>> TIERS = new ConcurrentHashMap<>();

    private static final class Entry {
        volatile @Nullable PlayerInfo info;
        volatile long fetchedAt;
        volatile boolean loading;
        volatile boolean failed;
    }

    public static void init() {
        for (TierList list : TierList.values()) {
            loadModes(list);
        }
    }

    public static CompletableFuture<List<GameMode>> loadModes(TierList list) {
        return list.getSource().fetchModes()
                .thenApply(unsorted -> {
                    // some APIs return their gamemodes in a random order: use a fixed one
                    List<GameMode> modes = new ArrayList<>(unsorted);
                    modes.sort(Comparator.comparingInt(m -> {
                        int i = MODE_ORDER.indexOf(m.canonical());
                        return i < 0 ? MODE_ORDER.size() : i;
                    }));
                    if (!modes.isEmpty()) GAMEMODES.put(list, modes);
                    TierTagger.getLogger().info("[{}] Found {} gamemodes: {}", list.getName(), modes.size(), modes.stream().map(GameMode::id).toList());
                    System.out.println("[Pxokie's TierTagger] " + list.getName() + ": " + modes.size() + " gamemodes loaded");
                    return modes;
                })
                .exceptionally(t -> {
                    TierTagger.getLogger().error("[{}] Failed to load gamemodes: {}", list.getName(), t.toString());
                    System.out.println("[Pxokie's TierTagger] " + list.getName() + ": failed to load gamemodes: " + t);
                    return List.of();
                });
    }

    public static List<GameMode> getGamemodes(TierList list) {
        List<GameMode> modes = GAMEMODES.get(list);
        if (modes == null || modes.isEmpty()) {
            return Collections.singletonList(GameMode.NONE);
        } else {
            return modes;
        }
    }

    /**
     * Rankings of a player on a tierlist. Never blocks: returns what is cached and
     * (re)fetches in the background when the data is missing or older than 5 minutes.
     */
    public static Optional<Map<String, PlayerInfo.Ranking>> getPlayerRankings(TierList list, UUID uuid) {
        // only real (online-mode) accounts can be on a tierlist, skip NPCs and fake players
        if (uuid.version() != 4) return Optional.empty();

        Entry entry = TIERS.computeIfAbsent(list, l -> new ConcurrentHashMap<>()).computeIfAbsent(uuid, u -> new Entry());
        long age = System.currentTimeMillis() - entry.fetchedAt;
        long maxAge = entry.failed ? RETRY_AFTER_ERROR_MS : REFRESH_AFTER_MS;

        if (!entry.loading && (entry.fetchedAt == 0 || age > maxAge)) {
            entry.loading = true;
            list.getSource().fetchProfile(uuid, null).whenComplete((info, t) -> {
                if (t == null) {
                    entry.info = info;
                    entry.failed = false;
                } else {
                    entry.failed = true;
                    TierTagger.getLogger().debug("[{}] Error getting tiers of {}: {}", list.getName(), uuid, t.toString());
                }
                entry.fetchedAt = System.currentTimeMillis();
                entry.loading = false;
            });
        }

        PlayerInfo info = entry.info;
        return info == null ? Optional.empty() : Optional.of(info.rankings());
    }

    /**
     * Fetches fresh data from one tierlist (bypassing the cache) and stores it.
     */
    public static CompletableFuture<@Nullable PlayerInfo> fetchFresh(TierList list, @Nullable UUID uuid, @Nullable String name) {
        return list.getSource().fetchProfile(uuid, name).thenApply(info -> {
            UUID key = uuid;
            if (key == null && info != null) key = com.pxokie.tiertagger.source.Http.parseUUID(info.uuid());

            if (key != null) {
                Entry entry = TIERS.computeIfAbsent(list, l -> new ConcurrentHashMap<>()).computeIfAbsent(key, u -> new Entry());
                entry.info = info;
                entry.failed = false;
                entry.fetchedAt = System.currentTimeMillis();
            }
            return info;
        });
    }

    /**
     * Result of looking a player up on one tierlist, for the "all tiers" screen.
     */
    public record ListResult(TierList list, @Nullable PlayerInfo info, @Nullable Throwable error) {
    }

    /**
     * Fetches fresh data from every tierlist at once. Never completes exceptionally:
     * a site that is down is reported in its {@link ListResult} instead.
     */
    public static CompletableFuture<List<ListResult>> fetchAll(@Nullable UUID uuid, @Nullable String name) {
        List<CompletableFuture<ListResult>> futures = Arrays.stream(TierList.values())
                .map(list -> fetchFresh(list, uuid, name)
                        .thenApply(info -> new ListResult(list, info, null))
                        .exceptionally(t -> new ListResult(list, null, t)))
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    public static void clearCache() {
        TIERS.clear();
    }

    public static GameMode findNextMode(TierList list, GameMode current) {
        List<GameMode> modes = getGamemodes(list);
        if (modes.isEmpty() || modes.getFirst().isNone()) {
            return GameMode.NONE;
        } else {
            return modes.get((modes.indexOf(current) + 1) % modes.size());
        }
    }

    public static Optional<GameMode> findMode(TierList list, String id) {
        List<GameMode> modes = GAMEMODES.get(list);
        if (modes == null) return Optional.empty();
        return modes.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst();
    }

    /**
     * Finds the gamemode of a tierlist that is the same kit as {@code other} (e.g. MCTiers "vanilla" = PvPTiers "crystal").
     */
    public static Optional<GameMode> findSameKit(TierList list, GameMode other) {
        String kit = other.canonical();
        return getGamemodes(list).stream().filter(m -> !m.isNone() && m.canonical().equals(kit)).findFirst();
    }

    public static GameMode findModeOrUgly(TierList list, String id) {
        return findMode(list, id).orElseGet(() -> new GameMode(id, GameMode.prettify(id)));
    }

    private TierCache() {
    }
}
