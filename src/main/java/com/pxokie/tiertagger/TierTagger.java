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
        TierTaggerConfig config = manager.getConfig();
        System.out.println("[Pxokie's TierTagger] Lunar Client detected: " + TierTaggerConfig.LUNAR
                + ", nametag position: " + config.getNametagPosition()
                + ", drawn " + (config.isDrawnSeparately() ? "separately" : "in the name text"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal(COMMAND)
                        .then(argument("player", PlayerArgumentType.player())
                                .executes(TierTagger::displayTierInfo))));

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pxtiertagger", "key"));

        Ukutils.registerKeybinding(new KeyMapping("pxtiertagger.keybind.gamemode", GLFW.GLFW_KEY_UNKNOWN, category),
                TierTagger::cycleAllGamemodes);

        // one keybind per tierlist, so every tierlist's gamemode can be changed on its own
        for (TierList list : TierList.values()) {
            Ukutils.registerKeybinding(new KeyMapping("pxtiertagger.keybind.gamemode." + list.name().toLowerCase(java.util.Locale.ROOT),
                    GLFW.GLFW_KEY_UNKNOWN, category), mc -> cycleGamemode(mc, list));
        }

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
     * Cycles the gamemode of every tierlist except SubTiers (its kits don't exist anywhere else):
     * the first shown one goes to its next gamemode and the others switch to the same kit when they have it.
     */
    private static void cycleAllGamemodes(Minecraft mc) {
        TierTaggerConfig config = manager.getConfig();
        List<TierList> lists = config.shownTierLists().stream().filter(l -> l != TierList.SUBTIERS).toList();
        if (lists.isEmpty()) lists = java.util.Arrays.stream(TierList.values()).filter(l -> l != TierList.SUBTIERS).toList();

        TierList main = lists.getFirst();
        GameMode next = TierCache.findNextMode(main, config.getMode(main));
        config.setMode(main, next.id());
        for (TierList other : lists.subList(1, lists.size())) {
            TierCache.findSameKit(other, next).ifPresent(m -> config.setMode(other, m.id()));
        }
        manager.saveConfig();

        if (mc.player != null) {
            Component message = Component.literal("Displayed gamemode: ").append(next.asStyled(false));
            mc.player.displayClientMessage(message, true);
        }
    }

    /**
     * Cycles the gamemode of one tierlist only.
     */
    private static void cycleGamemode(Minecraft mc, TierList list) {
        TierTaggerConfig config = manager.getConfig();
        GameMode next = TierCache.findNextMode(list, config.getMode(list));
        config.setMode(list, next.id());
        manager.saveConfig();

        if (mc.player != null) {
            Component message = Component.literal(list.getIcon() + " " + list.getName() + " gamemode: ").append(next.asStyled(false));
            mc.player.displayClientMessage(message, true);
        }
    }

    /**
     * Where the tiers end up relative to the name.
     */
    public enum Placement {
        /** in front of the name, with a divider after the tiers */
        LEFT,
        /** after the name, with a divider before the tiers */
        RIGHT,
        /** on their own, without a divider (drawn separately) */
        ALONE
    }

    /**
     * Whether tiers should be shown on this player's name at all (own name vs other players).
     */
    public static boolean showsTiersFor(UUID uuid) {
        TierTaggerConfig config = manager.getConfig();
        if (!config.isEnabled()) return false;

        Minecraft mc = Minecraft.getInstance();
        boolean own = mc.player != null && mc.player.getUUID().equals(uuid);
        return own ? config.isShowOwnTiers() : config.isShowOtherTiers();
    }

    /**
     * The tiers of the given tierlists, joined by dividers in the tier colours, or null when the
     * player has no tiers on any of them.
     */
    @Nullable
    public static MutableComponent buildTiers(UUID uuid, List<TierList> lists, Placement placement) {
        TierTaggerConfig config = manager.getConfig();
        MutableComponent following = null;
        int firstColor = 0xAAAAAA;
        int lastColor = 0xAAAAAA;

        for (TierList list : lists) {
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

            int color = getRankingColor(entry.get().ranking());
            if (following == null) {
                following = part;
                firstColor = color;
            } else {
                // the divider takes the colour of the tier in front of it
                final int c = lastColor;
                following.append(Component.literal(" | ").withStyle(s -> s.withColor(c).withBold(true))).append(part);
            }
            lastColor = color;
        }

        if (following == null) return null;

        switch (placement) {
            case LEFT -> {
                final int c = lastColor;
                following.append(Component.literal(" | ").withStyle(s -> s.withColor(c)));
            }
            case RIGHT -> {
                final int c = firstColor;
                following = Component.literal(" | ").withStyle(s -> s.withColor(c)).append(following);
            }
            case ALONE -> {
            }
        }
        return following;
    }

    /**
     * Puts the tiers on the left or right of a name, unless they are already there (several hooks can
     * build the same nametag, so this keeps the tiers from showing twice).
     */
    public static Component decorate(UUID uuid, Component name, List<TierList> lists, boolean right) {
        MutableComponent tiers = buildTiers(uuid, lists, right ? Placement.RIGHT : Placement.LEFT);
        if (tiers == null) return name;

        String plain = name.getString();
        String tierText = tiers.getString();
        if (right ? plain.endsWith(tierText) : plain.startsWith(tierText)) return name;

        return right ? name.copy().append(tiers) : tiers.append(name);
    }

    /**
     * Adds tiers to a nametag right before it is drawn. Works out whose nametag it is from the names of
     * the players around you, so it also works when a client (like Lunar) builds the nametag itself.
     */
    public static Component decorateRenderedNametag(Component text) {
        TierTaggerConfig config = manager.getConfig();
        if (!config.isInNameText()) return text;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return text;

        String plain = text.getString();
        if (plain.isEmpty() || plain.length() > 200) return text;

        UUID owner = null;
        int best = Integer.MAX_VALUE;
        for (AbstractClientPlayer p : mc.level.players()) {
            String name = p.getScoreboardName();
            if (name.length() < 2) continue;
            int idx = indexOfWord(plain, name);
            if (idx >= 0 && idx < best) {
                best = idx;
                owner = p.getUUID();
            }
        }
        if (owner == null || !showsTiersFor(owner)) return text;

        return decorate(owner, text, config.shownTierLists(), config.getNametagPosition() == TierTaggerConfig.NametagPosition.RIGHT);
    }

    /**
     * Puts the tiers of the player who sent a chat message in front of that message. The sender is
     * the online player whose name appears first in the message (works with any server's chat format).
     */
    public static Component decorateChat(Component message) {
        TierTaggerConfig config = manager.getConfig();
        if (!config.isEnabled() || !config.isChatTiers()) return message;

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientPacketListener connection = mc.getConnection();
        if (connection == null) return message;

        String text = message.getString();
        UUID sender = null;
        int best = Integer.MAX_VALUE;
        for (net.minecraft.client.multiplayer.PlayerInfo info : connection.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || name.length() < 3) continue;

            int idx = indexOfWord(text, name);
            if (idx >= 0 && idx < best) {
                best = idx;
                sender = info.getProfile().id();
            }
        }
        if (sender == null || !showsTiersFor(sender)) return message;

        MutableComponent tiers = buildTiers(sender, config.chatTierLists(), Placement.LEFT);
        if (tiers == null) return message;
        if (text.startsWith(tiers.getString())) return message;
        return tiers.append(message);
    }

    /**
     * Index of {@code word} in {@code text} as a whole word (not part of a longer name), or -1.
     */
    private static int indexOfWord(String text, String word) {
        int from = 0;
        while (true) {
            int idx = text.indexOf(word, from);
            if (idx < 0) return -1;

            boolean startOk = idx == 0 || !isNameChar(text.charAt(idx - 1));
            int end = idx + word.length();
            boolean endOk = end >= text.length() || !isNameChar(text.charAt(end));
            if (startOk && endOk) return idx;
            from = idx + 1;
        }
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Nametag text with tiers, for clients that use the vanilla name text.
     */
    public static Component decorateNametag(UUID uuid, Component name) {
        TierTaggerConfig config = manager.getConfig();
        if (!config.isInNameText() || !showsTiersFor(uuid)) return name;
        return decorate(uuid, name, config.shownTierLists(), config.getNametagPosition() == TierTaggerConfig.NametagPosition.RIGHT);
    }

    /**
     * Player list (Tab) text with tiers. Uses the nametag side, or the left when nametags are above/off.
     */
    public static Component decorateTab(UUID uuid, Component name) {
        TierTaggerConfig config = manager.getConfig();
        if (!config.isPlayerList() || !showsTiersFor(uuid)) return name;
        return decorate(uuid, name, config.tabTierLists(), config.getNametagPosition() == TierTaggerConfig.NametagPosition.RIGHT);
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
            if (!manager.getConfig().isInMenu(list)) continue;
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
