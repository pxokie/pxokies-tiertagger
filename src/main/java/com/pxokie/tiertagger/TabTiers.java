package com.pxokie.tiertagger;

import com.pxokie.tiertagger.config.TierTaggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Puts the tiers into the stored Tab list name of every entry. Clients with their own Tab list
 * (like Lunar) read that stored name directly, so changing it is the only way the tiers show up there.
 */
public final class TabTiers {
    /**
     * The name each entry had before the mod changed it (null = no custom name from the server).
     */
    private static final Map<PlayerInfo, Component> ORIGINAL = new WeakHashMap<>();
    private static final Map<PlayerInfo, Boolean> SEEN = new WeakHashMap<>();
    private static boolean applying = false;
    private static int ticks = 0;

    /**
     * Called when the server sets a Tab name: remember it as the original.
     */
    public static void onServerSet(PlayerInfo info, @Nullable Component name) {
        if (applying) return;
        ORIGINAL.put(info, name);
        SEEN.put(info, true);
    }

    public static void tick(Minecraft mc) {
        if (++ticks < 10) return;
        ticks = 0;

        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;

        TierTaggerConfig config = TierTagger.getManager().getConfig();
        boolean active = config.isEnabled() && config.isPlayerList();

        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            if (info.getProfile() == null) continue;

            if (!SEEN.containsKey(info)) {
                ORIGINAL.put(info, info.getTabListDisplayName());
                SEEN.put(info, true);
            }
            Component original = ORIGINAL.get(info);

            Component target = original;
            if (active) {
                Component base = original != null ? original
                        : PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));

                // learn the real account of every name shown in the Tab list (once, in the background)
                for (String word : NameTiers.words(base.getString())) {
                    if (NameTiers.resolve(word) == null) com.pxokie.tiertagger.source.Mojang.cachedOrRequest(word);
                }

                boolean right = config.getNametagPosition() == TierTaggerConfig.NametagPosition.RIGHT;
                Component decorated = NameTiers.insert(base, config.tabTierLists(), right);
                if (decorated != base) target = decorated;
            }

            if (!Objects.equals(info.getTabListDisplayName(), target)) {
                applying = true;
                try {
                    info.setTabListDisplayName(target);
                } finally {
                    applying = false;
                }
                Debug.hit("tab: tiers set", target == null ? "" : target.getString());
            }
        }
    }

    private TabTiers() {
    }
}
