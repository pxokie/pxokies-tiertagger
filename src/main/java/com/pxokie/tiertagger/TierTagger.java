package com.pxokie.tiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.pxokie.tiertagger.config.TierTaggerConfig;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import com.pxokie.tiertagger.tierlist.AllTiersScreen;
import com.pxokie.tiertagger.tierlist.PlayerSearchScreen;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.uku3lig.ukulib.config.ConfigManager;
import net.uku3lig.ukulib.utils.PlayerArgumentType;
import net.uku3lig.ukulib.utils.Ukutils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pxokie.tiertagger.model.TierList;
import com.pxokie.tiertagger.source.Mojang;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TierTagger implements ModInitializer {
    public static final String MOD_ID = "pxokies-tiertagger";
    public static final String COMMAND = "pxtiers";

    public static final Gson GSON = new GsonBuilder().create();

    @Getter
    private static final ConfigManager<TierTaggerConfig> manager = ConfigManager.createDefault(TierTaggerConfig.class, MOD_ID);
    @Getter
    private static final Logger logger = LoggerFactory.getLogger("PxokiesTierTagger");

    /**
     * How far away (in blocks) the "show all tiers" keybind can pick a player you are looking at.
     */
    private static final double PICK_RANGE = 128;

    @Override
    public void onInitialize() {
        TierCache.init();
        System.out.println("[Pxokie's TierTagger] Lunar Client detected: " + TierTaggerConfig.LUNAR
                + ", tiers drawn " + (manager.getConfig().isTiersAboveName() ? "above the name" : "next to the name"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal(COMMAND)
                        .then(argument("player", PlayerArgumentType.player())
                                .executes(TierTagger::displayTierInfo))));

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pxtiertagger", "key"));

        Ukutils.registerKeybinding(new KeyMapping("pxtiertagger.keybind.gamemode", GLFW.GLFW_KEY_UNKNOWN, category),
                TierTagger::cycleGamemode);

        Ukutils.registerKeybinding(new KeyMapping("pxtiertagger.keybind.profile", GLFW.GLFW_KEY_UNKNOWN, category),
                TierTagger::openProfileOfTarget);

        Ukutils.registerKeybinding(new KeyMapping("pxtiertagger.keybind.nearest", GLFW.GLFW_KEY_UNKNOWN, category),
                TierTagger::openProfileOfNearest);
    }

    /**
     * Opens the "all tiers" menu for the closest other player, wherever you are looking.
     */
    private static void openProfileOfNearest(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        Player nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;

            double d = p.distanceToSqr(mc.player);
            if (d < bestDist) {
                bestDist = d;
                nearest = p;
            }
        }

        if (nearest != null) {
            mc.setScreen(new AllTiersScreen(mc.screen, nearest.getUUID(), nearest.getScoreboardName(), nearest.getGameProfile()));
        } else {
            mc.player.displayClientMessage(Component.literal("No players nearby").withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * Opens the "all tiers" menu for the player under the crosshair, or the search screen when
     * you aren't looking at anyone.
     */
    private static void openProfileOfTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        Player target = findLookedAtPlayer(mc);
        if (target != null) {
            mc.setScreen(new AllTiersScreen(mc.screen, target.getUUID(), target.getScoreboardName(), target.getGameProfile()));
        } else {
            mc.setScreen(new PlayerSearchScreen(mc.screen));
        }
    }

    @Nullable
    private static Player findLookedAtPlayer(Minecraft mc) {
        if (mc.crosshairPickEntity instanceof Player p) return p;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1.0f).scale(PICK_RANGE));

        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || p.isInvisibleTo(mc.player)) continue;

            Optional<Vec3> hit = p.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(eye);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        }
        return best;
    }

    /**
     * Cycles the gamemode of the first tierlist shown next to names, and switches the other shown
     * tierlists to the same kit when they have it (e.g. Sword everywhere).
     */
    private static void cycleGamemode(Minecraft mc) {
        TierTaggerConfig config = manager.getConfig();
        List<TierList> shown = config.shownTierLists();
        if (shown.isEmpty()) return;

        TierList main = shown.getFirst();
        GameMode next = TierCache.findNextMode(main, config.getMode(main));
        config.setMode(main, next.id());
        for (TierList other : shown.subList(1, shown.size())) {
            TierCache.findSameKit(other, next).ifPresent(m -> config.setMode(other, m.id()));
        }
        manager.saveConfig();

        if (mc.player != null) {
            Component message = Component.literal("Displayed gamemode: ").append(next.asStyled(false));
            mc.player.displayClientMessage(message, true);
        }
    }

    /**
     * The tiers that go in front of a name, including the divider before the name, or null when
     * the player has no tiers on any shown tierlist.
     */
    @Nullable
    public static MutableComponent buildTierPrefix(UUID uuid) {
        return buildTiers(uuid, true);
    }

    /**
     * The tiers of every shown tierlist, joined by dividers in the tier colours.
     *
     * @param nameSeparator add the divider that goes between the tiers and the name
     */
    @Nullable
    public static MutableComponent buildTiers(UUID uuid, boolean nameSeparator) {
        TierTaggerConfig config = manager.getConfig();
        MutableComponent following = null;
        int lastColor = 0xAAAAAA;

        for (TierList list : config.shownTierLists()) {
            Optional<PlayerInfo.NamedRanking> entry = getPlayerTier(list, uuid);
            if (entry.isEmpty()) continue;

            MutableComponent part = Component.empty();
            if (config.isShowListLogos()) {
                part.append(Component.literal(String.valueOf(list.getSmallIcon())).withStyle(ChatFormatting.WHITE));
            }
            GameMode mode = entry.get().mode();
            if (config.isShowIcons() && mode != null && mode.icon().isPresent()) {
                part.append(Component.literal(mode.icon().get().toString()));
            }
            part.append(getRankingText(entry.get().ranking(), false));

            if (following == null) {
                following = part;
            } else {
                // the divider takes the colour of the tier in front of it
                final int color = lastColor;
                following.append(Component.literal(" | ").withStyle(s -> s.withColor(color).withBold(true))).append(part);
            }
            lastColor = getRankingColor(entry.get().ranking());
        }

        if (following != null && nameSeparator) {
            final int color = lastColor;
            following.append(Component.literal(" | ").withStyle(s -> s.withColor(color)));
        }
        return following;
    }

    public static Component appendTier(UUID uuid, Component text) {
        MutableComponent prefix = buildTierPrefix(uuid);
        return prefix == null ? text : prefix.append(text);
    }

    /**
     * Like {@link #appendTier} but does nothing when the text already starts with the tiers, so
     * hooking several places that build the nametag never shows the tiers twice.
     */
    public static Component appendTierOnce(UUID uuid, Component text) {
        MutableComponent prefix = buildTierPrefix(uuid);
        if (prefix == null) return text;
        if (text.getString().startsWith(prefix.getString())) return text;
        return prefix.append(text);
    }

    /**
     * The colour a ranking is drawn in next to a name.
     */
    public static int getRankingColor(PlayerInfo.Ranking ranking) {
        if (ranking.retired()) return manager.getConfig().getRetiredColor();
        String key = (ranking.pos() == 0 ? "H" : ranking.pos() == 2 ? "M" : "L") + "T" + ranking.tier();
        return getTierColor(key);
    }

    public static Optional<PlayerInfo.NamedRanking> getPlayerTier(TierList list, UUID uuid) {
        TierTaggerConfig config = manager.getConfig();
        GameMode mode = config.getMode(list);

        return TierCache.getPlayerRankings(list, uuid)
                .map(all -> {
                    Map<String, PlayerInfo.Ranking> rankings = all;
                    if (!config.isShowRetired()) {
                        rankings = new LinkedHashMap<>();
                        for (Map.Entry<String, PlayerInfo.Ranking> e : all.entrySet()) {
                            if (!e.getValue().retired()) rankings.put(e.getKey(), e.getValue());
                        }
                    }

                    PlayerInfo.Ranking ranking = rankings.get(mode.id());
                    Optional<PlayerInfo.NamedRanking> highest = PlayerInfo.getHighestRanking(list, rankings);
                    TierTaggerConfig.HighestMode highestMode = config.getHighestMode();

                    if (ranking == null) {
                        if (highestMode != TierTaggerConfig.HighestMode.NEVER && highest.isPresent()) {
                            return highest.get();
                        } else {
                            return null;
                        }
                    } else {
                        if (highestMode == TierTaggerConfig.HighestMode.ALWAYS && highest.isPresent()) {
                            return highest.get();
                        } else {
                            return ranking.asNamed(mode);
                        }
                    }
                });
    }

    private static MutableComponent getTierText(int tier, int pos, boolean retired) {
        StringBuilder text = new StringBuilder();
        if (retired) text.append("R");
        text.append(pos == 0 ? "H" : pos == 2 ? "M" : "L").append("T").append(tier);

        int color = TierTagger.getTierColor(text.toString());
        return Component.literal(text.toString()).withStyle(s -> s.withColor(color));
    }

    public static Component getRankingText(PlayerInfo.Ranking ranking, boolean showPeak) {
        if (ranking.retired() && ranking.peakTier() != null && ranking.peakPos() != null) {
            return getTierText(ranking.peakTier(), ranking.peakPos(), true);
        } else {
            MutableComponent tierText = getTierText(ranking.tier(), ranking.pos(), ranking.retired());

            if (showPeak && ranking.comparablePeak() < ranking.comparableTier()) {
                // noinspection DataFlowIssue
                tierText.append(Component.literal(" (peak: ").withStyle(s -> s.withColor(ChatFormatting.GRAY)))
                        .append(getTierText(ranking.peakTier(), ranking.peakPos(), false))
                        .append(Component.literal(")").withStyle(s -> s.withColor(ChatFormatting.GRAY)));
            }

            return tierText;
        }
    }

    private static int displayTierInfo(CommandContext<FabricClientCommandSource> ctx) {
        PlayerArgumentType.PlayerSelector selector = ctx.getArgument("player", PlayerArgumentType.PlayerSelector.class);
        String name = selector.name();

        UUID uuid = ctx.getSource().getWorld().players().stream()
                .filter(p -> p.getScoreboardName().equalsIgnoreCase(name) || p.getStringUUID().equalsIgnoreCase(name))
                .findFirst()
                .map(Entity::getUUID)
                .orElse(null);

        ctx.getSource().sendFeedback(Component.literal("[Pxokie's TierTagger] Searching every tierlist..."));
        CompletableFuture<UUID> id = uuid != null ? CompletableFuture.completedFuture(uuid) : Mojang.uuidOf(name).exceptionally(t -> null);

        id.thenCompose(u -> TierCache.fetchAll(u, u == null ? name : null))
                .thenAccept(results -> Minecraft.getInstance().execute(() -> ctx.getSource().sendFeedback(printPlayerInfo(name, results))));

        return 0;
    }

    private static Component printPlayerInfo(String name, List<TierCache.ListResult> results) {
        MutableComponent text = Component.empty().append("=== Tiers of " + name + " ===");

        for (TierCache.ListResult r : results) {
            TierList list = r.list();
            text.append(Component.literal("\n" + list.getIcon() + " ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(list.getName()).withStyle(s -> s.withColor(list.getColor()).withBold(true)));

            if (r.error() != null) {
                text.append(Component.literal(": couldn't reach the site").withStyle(ChatFormatting.RED));
            } else if (r.info() == null || r.info().rankings().isEmpty()) {
                text.append(Component.literal(": not ranked").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (PlayerInfo.NamedRanking nr : r.info().getSortedTiers(list)) {
                    if (nr.mode() == null) continue;
                    text.append(Component.literal("\n  ").append(nr.mode().asStyled(true)).append(": ").append(getRankingText(nr.ranking(), true)));
                }
            }
        }

        return text;
    }

    public static int getTierColor(String tier) {
        if (tier.startsWith("R")) {
            return manager.getConfig().getRetiredColor();
        }

        Map<String, Integer> colors = manager.getConfig().getTierColors();
        Integer color = colors.get(tier);
        if (color != null) return color;

        // MCPvP middle tiers (MT3...) aren't in TierTagger's colour list: use the HT colour of that tier
        if (tier.startsWith("MT")) {
            return colors.getOrDefault("H" + tier.substring(1), 0xD3D3D3);
        }
        return 0xD3D3D3;
    }
}
