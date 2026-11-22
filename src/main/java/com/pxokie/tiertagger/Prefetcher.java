package com.pxokie.tiertagger;

import com.pxokie.tiertagger.config.TierTaggerConfig;
import com.pxokie.tiertagger.model.TierList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Looks up the tiers of everyone on the server in the background, a few players at a time, so the
 * tiers are already known when someone chats or you open the Tab list.
 */
public final class Prefetcher {
    /**
     * Ticks between two batches (20 ticks = 1 second).
     */
    private static final int INTERVAL_TICKS = 10;
    /**
     * New players looked up per batch, to go easy on the tierlist websites.
     */
    private static final int PLAYERS_PER_BATCH = 1;

    private static int ticks = 0;

    public static void tick(Minecraft mc) {
        if (++ticks < INTERVAL_TICKS) return;
        ticks = 0;

        TierTaggerConfig config = TierTagger.getManager().getConfig();
        if (!config.isEnabled()) return;

        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;

        // the tierlists that are shown somewhere without the player being on screen
        Set<TierList> lists = EnumSet.noneOf(TierList.class);
        if (config.isChatTiers()) lists.addAll(config.chatTierLists());
        if (config.isPlayerList()) lists.addAll(config.tabTierLists());
        lists.addAll(config.shownTierLists());
        if (lists.isEmpty()) return;

        int started = 0;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            if (started >= PLAYERS_PER_BATCH) break;
            if (info.getProfile() == null) continue;

            UUID uuid = info.getProfile().id();
            if (uuid == null || uuid.version() != 4) continue;

            boolean any = false;
            for (TierList list : lists) {
                if (!TierCache.isKnown(list, uuid)) {
                    TierCache.getPlayerRankings(list, uuid); // starts the lookup
                    any = true;
                }
            }
            if (any) started++;
        }
    }

    private Prefetcher() {
    }
}
