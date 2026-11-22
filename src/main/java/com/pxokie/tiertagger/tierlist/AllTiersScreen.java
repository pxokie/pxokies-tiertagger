package com.pxokie.tiertagger.tierlist;

import com.mojang.authlib.GameProfile;
import com.pxokie.tiertagger.TierCache;
import com.pxokie.tiertagger.TierTagger;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.PlayerInfo;
import com.pxokie.tiertagger.model.TierList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.Services;
import net.minecraft.world.entity.player.PlayerSkin;
import net.uku3lig.ukulib.config.screen.CloseableScreen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Every tier a player has on every tierlist, on one screen: one card per tierlist in a grid that
 * uses the full width, scaled down only when it really doesn't fit.
 */
public class AllTiersScreen extends CloseableScreen {
    private static final int ROW = 11;
    private static final int HEADER = 18;
    private static final int PAD = 6;
    private static final int GAP = 8;
    private static final int MIN_CARD_WIDTH = 150;
    private static final int MIN_ROWS = 10;
    private static final int SKIN_WIDTH = 60;
    private static final int SKIN_HEIGHT = 144;
    private static final int TOP = 44;
    private static final int BOTTOM_MARGIN = 34;

    private final String playerName;
    private @Nullable UUID uuid;
    private @Nullable GameProfile profile;

    private volatile @Nullable List<TierCache.ListResult> results = null;
    private boolean started = false;

    private @Nullable PlayerSkinWidget skin;

    /**
     * A row in a card: gamemode on the left, tier on the right.
     */
    private record Row(Component left, Component right) {
    }

    private record Card(TierList list, @Nullable Component info, List<Row> rows, @Nullable Component message) {
        int height() {
            int h = HEADER + PAD;
            if (info != null) h += ROW + 2;
            // every card is at least MIN_ROWS tall, so the menu has the same shape for every player
            h += Math.max(MIN_ROWS, message != null ? 1 : rows.size()) * ROW;
            return h + PAD - 2;
        }
    }

    private record Layout(int columns, float scale) {
    }

    public AllTiersScreen(@Nullable Screen parent, @Nullable UUID uuid, String playerName, @Nullable GameProfile profile) {
        super(Component.literal(playerName + "'s tiers"), parent);
        this.uuid = uuid;
        this.playerName = playerName;
        this.profile = profile;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
                .build());

        if (!started) {
            started = true;
            load();
        }

        if (this.profile != null) {
            if (this.skin == null) {
                Supplier<PlayerSkin> skinSupplier = Minecraft.getInstance().getSkinManager().createLookup(this.profile, true);
                this.skin = new PlayerSkinWidget(SKIN_WIDTH, SKIN_HEIGHT, Minecraft.getInstance().getEntityModels(), skinSupplier);
            }
            int areaHeight = this.height - TOP - BOTTOM_MARGIN;
            this.skin.setPosition(14, TOP + Math.max(0, (areaHeight - SKIN_HEIGHT) / 2));
            this.addRenderableWidget(this.skin);
        }
    }

    private void load() {
        CompletableFuture<Void> resolve;
        if (this.uuid == null || this.profile == null) {
            // searching by name: resolve the real UUID and skin through Mojang first
            Services services = Minecraft.getInstance().services();
            resolve = CompletableFuture.runAsync(() -> {
                try {
                    services.profileResolver().fetchByName(this.playerName).ifPresent(p -> {
                        this.profile = p;
                        this.uuid = p.id();
                    });
                } catch (Exception e) {
                    TierTagger.getLogger().debug("Could not resolve profile of {}", this.playerName, e);
                }
            });
        } else {
            resolve = CompletableFuture.completedFuture(null);
        }

        resolve.thenCompose(v -> TierCache.fetchAll(this.uuid, this.uuid == null ? this.playerName : null))
                .thenAccept(list -> {
                    this.results = list;
                    // re-init on the render thread so the skin widget appears once the profile is known
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().screen == this) this.rebuildWidgets();
                    });
                });
    }

    private List<Card> buildCards(List<TierCache.ListResult> res) {
        List<Card> cards = new ArrayList<>();

        for (TierCache.ListResult r : res) {
            TierList list = r.list();

            // cards without tiers keep the same structure (info line + divider) so every card looks the same
            Component noInfo = Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);

            if (r.error() != null) {
                cards.add(new Card(list, noInfo, List.of(), Component.literal("Site unreachable").withStyle(ChatFormatting.RED)));
                continue;
            }

            PlayerInfo info = r.info();
            if (info == null || info.rankings().isEmpty()) {
                cards.add(new Card(list, noInfo, List.of(), Component.literal("Not ranked").withStyle(ChatFormatting.DARK_GRAY)));
                continue;
            }

            MutableComponent extra = Component.empty();
            boolean hasExtra = false;
            if (info.hasRegion()) {
                extra.append(Component.literal(info.region()).withStyle(s -> s.withColor(info.getRegionColor())));
                hasExtra = true;
            }
            if (info.points() > 0) {
                if (hasExtra) extra.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                extra.append(Component.literal(info.points() + " pts").withStyle(ChatFormatting.GRAY));
                hasExtra = true;
            }
            if (info.overall() > 0) {
                if (hasExtra) extra.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                extra.append(Component.literal("#" + info.overall()).withStyle(ChatFormatting.GOLD));
                hasExtra = true;
            }

            List<Row> rows = new ArrayList<>();
            for (PlayerInfo.NamedRanking nr : info.getSortedTiers(list)) {
                GameMode mode = nr.mode();
                if (mode == null) continue;
                rows.add(new Row(mode.asStyled(true), tierWithPeak(nr.ranking())));
            }
            cards.add(new Card(list, hasExtra ? extra : Component.literal("-").withStyle(ChatFormatting.DARK_GRAY), rows, null));
        }

        return cards;
    }

    /**
     * "LT3 ↑HT3": the current tier, plus the peak in compact form when it was higher.
     */
    private Component tierWithPeak(PlayerInfo.Ranking ranking) {
        MutableComponent text = TierTagger.getRankingText(ranking, false).copy();
        if (!ranking.retired() && ranking.peakTier() != null && ranking.peakPos() != null
                && ranking.comparablePeak() < ranking.comparableTier()) {
            PlayerInfo.Ranking peak = new PlayerInfo.Ranking(ranking.peakTier(), ranking.peakPos(), null, null, 0, false);
            text.append(Component.literal(" ↑").withStyle(ChatFormatting.DARK_GRAY))
                    .append(TierTagger.getRankingText(peak, false));
        }
        return text;
    }

    private Component headerText(TierList list) {
        return Component.literal(list.getIcon() + " ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(list.getName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    /**
     * The narrowest a card can be without its text overlapping.
     */
    private int naturalWidth(Card c) {
        int w = this.font.width(headerText(c.list()));
        if (c.info() != null) w = Math.max(w, this.font.width(c.info()));
        if (c.message() != null) w = Math.max(w, this.font.width(c.message()));
        for (Row r : c.rows()) w = Math.max(w, this.font.width(r.left()) + 12 + this.font.width(r.right()));
        return Math.max(MIN_CARD_WIDTH, w + PAD * 2);
    }

    private int gridHeight(List<Card> cards, int columns) {
        int total = 0;
        for (int start = 0; start < cards.size(); start += columns) {
            int rowHeight = 0;
            for (int i = start; i < Math.min(start + columns, cards.size()); i++) rowHeight = Math.max(rowHeight, cards.get(i).height());
            total += rowHeight + (start > 0 ? GAP : 0);
        }
        return total;
    }

    /**
     * Always one row with every tierlist side by side, so the menu looks the same for every player;
     * it only gets scaled down when it doesn't fit.
     */
    private Layout bestLayout(List<Card> cards, int cardWidth, int availWidth, int availHeight) {
        int cols = cards.size();
        int width = cols * cardWidth + (cols - 1) * GAP;
        int height = gridHeight(cards, cols);
        float scale = Math.min(1f, Math.min((float) availWidth / width, (float) availHeight / height));
        return new Layout(cols, scale);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // title
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.width / 2f, 8);
        graphics.pose().scale(1.5f, 1.5f);
        Component name = Component.literal(this.playerName).withStyle(ChatFormatting.BOLD);
        graphics.drawString(this.font, name, -this.font.width(name) / 2, 0, 0xFFFFFFFF);
        graphics.pose().popMatrix();
        graphics.drawCenteredString(this.font, Component.literal("Tiers on every tierlist").withStyle(ChatFormatting.GRAY),
                this.width / 2, 26, 0xFFFFFFFF);

        int areaLeft = this.profile != null ? 14 + SKIN_WIDTH + 14 : 12;
        int areaRight = this.width - 12;
        int areaTop = TOP;
        int areaBottom = this.height - BOTTOM_MARGIN;
        int availWidth = areaRight - areaLeft;
        int availHeight = areaBottom - areaTop;

        List<TierCache.ListResult> res = this.results;
        if (res == null) {
            graphics.drawCenteredString(this.font, Component.literal("Loading tiers from every tierlist...").withStyle(ChatFormatting.GRAY),
                    (areaLeft + areaRight) / 2, (areaTop + areaBottom) / 2, 0xFFFFFFFF);
            return;
        }

        List<Card> cards = buildCards(res);
        int cardWidth = 0;
        for (Card c : cards) cardWidth = Math.max(cardWidth, naturalWidth(c));

        Layout layout = bestLayout(cards, cardWidth, availWidth, availHeight);
        float scale = layout.scale();
        int cols = layout.columns();

        // stretch the cards so the grid fills the available width
        float virtualWidth = availWidth / scale;
        int stretched = (int) ((virtualWidth - (cols - 1) * GAP) / cols);
        cardWidth = Math.max(cardWidth, Math.min(stretched, cardWidth * 2));
        int gridWidth = cols * cardWidth + (cols - 1) * GAP;
        int gridHeight = gridHeight(cards, cols);

        float originX = areaLeft + Math.max(0, (availWidth - gridWidth * scale) / 2f);
        float originY = areaTop + Math.max(0, (availHeight - gridHeight * scale) / 2f);

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(scale, scale);

        int y = 0;
        for (int start = 0; start < cards.size(); start += cols) {
            int end = Math.min(start + cols, cards.size());
            int rowHeight = 0;
            for (int i = start; i < end; i++) rowHeight = Math.max(rowHeight, cards.get(i).height());

            // centre a last row that has fewer cards
            int count = end - start;
            int x = (gridWidth - (count * cardWidth + (count - 1) * GAP)) / 2;
            for (int i = start; i < end; i++) {
                drawCard(graphics, cards.get(i), x, y, cardWidth, rowHeight);
                x += cardWidth + GAP;
            }
            y += rowHeight + GAP;
        }

        graphics.pose().popMatrix();
    }

    private void drawCard(GuiGraphics g, Card card, int x, int y, int w, int h) {
        int color = card.list().getColor() & 0xFFFFFF;

        // body, coloured header bar and outline
        g.fill(x, y, x + w, y + h, 0xC0101014);
        g.fill(x, y, x + w, y + HEADER, 0x90000000 | color);
        g.fill(x, y + HEADER - 1, x + w, y + HEADER, 0xFF000000 | color);
        g.renderOutline(x, y, w, h, 0x80000000 | color);

        g.drawString(this.font, headerText(card.list()), x + PAD, y + 5, 0xFFFFFFFF);

        int cy = y + HEADER + PAD - 1;
        if (card.info() != null) {
            g.drawString(this.font, card.info(), x + PAD, cy, 0xFFFFFFFF);
            cy += ROW + 2;
            g.fill(x + PAD, cy - 3, x + w - PAD, cy - 2, 0x40FFFFFF);
        }

        if (card.message() != null) {
            g.drawCenteredString(this.font, card.message(), x + w / 2, cy, 0xFFFFFFFF);
            return;
        }

        for (Row row : card.rows()) {
            g.drawString(this.font, row.left(), x + PAD, cy, 0xFFFFFFFF);
            g.drawString(this.font, row.right(), x + w - PAD - this.font.width(row.right()), cy, 0xFFFFFFFF);
            cy += ROW;
        }
    }
}
