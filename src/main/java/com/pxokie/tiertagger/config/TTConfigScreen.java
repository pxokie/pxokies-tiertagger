package com.pxokie.tiertagger.config;

import com.pxokie.tiertagger.TierCache;
import com.pxokie.tiertagger.TierTagger;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.TierList;
import com.pxokie.tiertagger.tierlist.PlayerSearchScreen;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.option.*;
import net.uku3lig.ukulib.config.option.widget.ButtonTab;
import net.uku3lig.ukulib.config.screen.TabbedConfigScreen;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Settings screen (Mod Menu). ukulib shows the options of a tab in one column without scrolling,
 * so every tab is kept short: general settings, display, one tab per tierlist and the colours.
 */
public class TTConfigScreen extends TabbedConfigScreen<TierTaggerConfig> {
    public TTConfigScreen(Screen parent) {
        super("Pxokie's TierTagger Config", parent, TierTagger.getManager());

        // a tierlist whose gamemodes didn't load (site was down): try again
        for (TierList list : TierList.values()) {
            if (TierCache.getGamemodes(list).getFirst().isNone()) TierCache.loadModes(list);
        }
    }

    @Override
    protected Tab[] getTabs(TierTaggerConfig config) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new MainSettingsTab());
        tabs.add(new DisplayTab());
        for (TierList list : TierList.values()) tabs.add(new TierlistTab(list));
        tabs.add(new ColorsTab("pxtiertagger.colors.high", '1', '3', false));
        tabs.add(new ColorsTab("pxtiertagger.colors.low", '4', '5', true));
        return tabs.toArray(Tab[]::new);
    }

    public class MainSettingsTab extends ButtonTab<TierTaggerConfig> {
        public MainSettingsTab() {
            super("pxtiertagger.config", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    CyclingOption.ofBoolean("pxtiertagger.config.enabled", config.isEnabled(), config::setEnabled),
                    CyclingOption.ofTranslatableEnum("pxtiertagger.config.position", TierTaggerConfig.NametagPosition.class, config.getNametagPosition(), config::setNametagPosition, OptionInstance.cachedConstantTooltip(Component.translatable("pxtiertagger.config.position.desc"))),
                    CyclingOption.ofTranslatableEnum("pxtiertagger.config.method", TierTaggerConfig.DrawMethod.class, config.getDrawMethod(), config::setDrawMethod, OptionInstance.cachedConstantTooltip(Component.translatable("pxtiertagger.config.method.desc"))),
                    CyclingOption.ofBoolean("pxtiertagger.config.playerList", config.isPlayerList(), config::setPlayerList),
                    CyclingOption.ofBoolean("pxtiertagger.config.chat", config.isChatTiers(), config::setChatTiers),
                    CyclingOption.ofBoolean("pxtiertagger.config.own", config.isShowOwnTiers(), config::setShowOwnTiers),
                    CyclingOption.ofBoolean("pxtiertagger.config.others", config.isShowOtherTiers(), config::setShowOtherTiers),
            };
        }
    }

    public class DisplayTab extends ButtonTab<TierTaggerConfig> {
        public DisplayTab() {
            super("pxtiertagger.config.display", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    CyclingOption.ofBoolean("pxtiertagger.config.retired", config.isShowRetired(), config::setShowRetired),
                    CyclingOption.ofTranslatableEnum("pxtiertagger.config.highest", TierTaggerConfig.HighestMode.class, config.getHighestMode(), config::setHighestMode, OptionInstance.cachedConstantTooltip(Component.translatable("pxtiertagger.config.highest.desc"))),
                    CyclingOption.ofBoolean("pxtiertagger.config.icons", config.isShowIcons(), config::setShowIcons),
                    CyclingOption.ofBoolean("pxtiertagger.config.logos", config.isShowListLogos(), config::setShowListLogos),
                    new SimpleButton("pxtiertagger.clear", b -> TierCache.clearCache()),
                    new ScreenOpenButton("pxtiertagger.config.search", PlayerSearchScreen::new)
            };
        }
    }

    /**
     * One tab per tierlist: its gamemode and where its tiers are shown (each switch separate).
     */
    public class TierlistTab extends OwnTab {
        private final TierList list;

        public TierlistTab(TierList list) {
            super(Component.literal(list.getIcon() + " " + list.getName()));
            this.list = list;
            build();
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            String key = "pxtiertagger.list." + list.name().toLowerCase(Locale.ROOT);
            List<GameMode> modes = TierCache.getGamemodes(list);
            GameMode current = config.getMode(list);

            return new WidgetCreator[]{
                    new CyclingOption<>("pxtiertagger.config.gamemode", modes, current, m -> config.setMode(list, m.id()),
                            m -> Component.literal(m.title()),
                            m -> m.isNone() ? Tooltip.create(Component.translatable("pxtiertagger.config.gamemode.none")) : null,
                            !current.isNone()),
                    CyclingOption.ofBoolean(key + ".nametag", config.isShown(list), b -> config.setShown(list, b)),
                    CyclingOption.ofBoolean(key + ".tab", config.isInTab(list), b -> config.setInTab(list, b)),
                    CyclingOption.ofBoolean(key + ".menu", config.isInMenu(list), b -> config.setInMenu(list, b)),
                    CyclingOption.ofBoolean(key + ".chat", config.isInChat(list), b -> config.setInChat(list, b)),
            };
        }
    }

    /**
     * Tier colours, split over two tabs so they fit on the screen.
     */
    public class ColorsTab extends OwnTab {
        private final char fromTier;
        private final char toTier;
        private final boolean withRetired;

        protected ColorsTab(String title, char fromTier, char toTier, boolean withRetired) {
            super(Component.translatable(title));
            this.fromTier = fromTier;
            this.toTier = toTier;
            this.withRetired = withRetired;
            build();
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            // i genuinely don't understand but chaining the calls just EXPLODES????
            Comparator<Map.Entry<String, Integer>> comparator = Comparator.comparing(e -> e.getKey().charAt(2));
            comparator = comparator.thenComparing(e -> e.getKey().charAt(0));

            List<ColorOption> tiers = config.getTierColors().entrySet().stream()
                    .filter(e -> e.getKey().length() == 3 && e.getKey().charAt(2) >= fromTier && e.getKey().charAt(2) <= toTier)
                    .sorted(comparator)
                    .map(e -> new ColorOption(e.getKey(), e.getValue(), val -> config.getTierColors().put(e.getKey(), val)))
                    .collect(Collectors.toList());

            if (withRetired) {
                tiers.addLast(new ColorOption("pxtiertagger.colors.retired", config.getRetiredColor(), config::setRetiredColor));
            }

            return tiers.toArray(WidgetCreator[]::new);
        }
    }

    /**
     * Like ukulib's ButtonTab, but the buttons are added after the subclass has set its fields.
     * (ButtonTab builds them inside its own constructor, when those fields are still empty.)
     */
    public abstract class OwnTab extends GridLayoutTab {
        protected OwnTab(Component title) {
            super(title);
        }

        protected void build() {
            GridLayout.RowHelper helper = this.layout.rowSpacing(4).createRowHelper(1);
            for (WidgetCreator option : getWidgets(TTConfigScreen.this.manager.getConfig())) {
                helper.addChild(option.createWidget(0, 0, 210, 20));
            }
        }

        protected abstract WidgetCreator[] getWidgets(TierTaggerConfig config);
    }
}