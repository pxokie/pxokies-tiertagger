package com.pxokie.tiertagger;

import com.pxokie.tiertagger.model.TierList;
import com.pxokie.tiertagger.source.Mojang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puts tiers next to player names inside any text (Tab list, chat), based on the name itself:
 * "pxokie" gets pxokie's tiers wherever it appears, no matter which entry or message it is in.
 */
public final class NameTiers {
    private record Segment(String text, Style style) {
    }

    /**
     * The real account behind a name as written, or null when it isn't a known player name.
     * Mojang's answer wins (exact spelling), then players in your world, then the server's player list.
     */
    @Nullable
    public static UUID resolve(String name) {
        if (name.length() < 3 || name.length() > 16) return null;

        UUID uuid = Mojang.cachedExact(name);
        if (uuid != null) return uuid;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p.getScoreboardName().equals(name) && p.getUUID().version() == 4) return p.getUUID();
            }
        }

        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfo(name);
            if (info != null && info.getProfile() != null && name.equals(info.getProfile().name())) {
                // the entry may be a fake one (custom Tab lists): ask Mojang in the background to be sure
                Mojang.cachedOrRequest(name);
                UUID id = info.getProfile().id();
                return id != null && id.version() == 4 ? id : null;
            }
        }
        return null;
    }

    /**
     * Every name-like word in the text (3-16 letters, digits or underscores).
     */
    public static Set<String> words(String text) {
        Set<String> out = new LinkedHashSet<>();
        int i = 0;
        while (i < text.length()) {
            if (!isNameChar(text.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && isNameChar(text.charAt(i))) i++;
            int len = i - start;
            if (len >= 3 && len <= 16) out.add(text.substring(start, i));
        }
        return out;
    }

    /**
     * Returns the text with tiers put right before (or after) every known player name in it.
     * Returns the same object when nothing changed.
     */
    public static Component insert(Component text, List<TierList> lists, boolean right) {
        if (lists.isEmpty()) return text;

        List<Segment> segments = new ArrayList<>();
        text.visit((style, s) -> {
            segments.add(new Segment(s, style));
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent out = Component.empty();
        boolean changed = false;

        for (Segment seg : segments) {
            String s = seg.text();
            StringBuilder pending = new StringBuilder();
            int i = 0;
            while (i < s.length()) {
                if (!isNameChar(s.charAt(i))) {
                    pending.append(s.charAt(i));
                    i++;
                    continue;
                }

                int start = i;
                while (i < s.length() && isNameChar(s.charAt(i))) i++;
                String word = s.substring(start, i);

                MutableComponent tiers = null;
                UUID uuid = resolve(word);
                if (uuid != null && TierTagger.showsTiersFor(uuid)) {
                    tiers = TierTagger.buildTiers(uuid, lists, right ? TierTagger.Placement.RIGHT : TierTagger.Placement.LEFT);
                }

                if (tiers == null) {
                    pending.append(word);
                    continue;
                }

                if (!pending.isEmpty()) {
                    out.append(Component.literal(pending.toString()).setStyle(seg.style()));
                    pending.setLength(0);
                }
                if (!right) out.append(tiers);
                out.append(Component.literal(word).setStyle(seg.style()));
                if (right) out.append(tiers);
                changed = true;
            }
            if (!pending.isEmpty()) out.append(Component.literal(pending.toString()).setStyle(seg.style()));
        }

        return changed ? out : text;
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private NameTiers() {
    }
}
