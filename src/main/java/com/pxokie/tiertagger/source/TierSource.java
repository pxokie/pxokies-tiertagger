package com.pxokie.tiertagger.source;

import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One tierlist website. Every implementation turns the site's own API into the
 * TierTagger data model, so the rest of the mod does not care where tiers come from.
 */
public interface TierSource {
    /**
     * The gamemodes (kits) this tierlist ranks, in display order.
     */
    CompletableFuture<List<GameMode>> fetchModes();

    /**
     * The full profile of a player, or {@code null} when the player is not on this tierlist.
     *
     * @param uuid the player's UUID (may be null when only the name is known)
     * @param name the player's username (may be null when only the UUID is known)
     */
    CompletableFuture<@Nullable PlayerInfo> fetchProfile(@Nullable UUID uuid, @Nullable String name);
}
