package com.pxokie.tiertagger.config;

import com.pxokie.tiertagger.TierCache;
import com.pxokie.tiertagger.TierTagger;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.TierList;
import com.pxokie.tiertagger.tierlist.PlayerSearchScreen;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.option.*;
import net.uku3lig.ukulib.config.option.widget.ButtonTab;
import net.uku3lig.ukulib.config.screen.TabbedConfigScreen;

import java.util.*;
import java.util.stream.Collectors;

public class TTConfigScreen extends TabbedConfigScreen<TierTaggerConfig> {
    public TTConfigScreen(Screen parent) {
        super("Pxokie's TierTagger Config", parent, TierTagger.getManager());
    }

    @Override
    protected Tab[] getTabs(TierTaggerConfig config) {
        return new Tab[]{new MainSettingsTab(), new TierlistTab(), new ColorsTab()};
    }

    public class MainSettingsTab extends ButtonTab<TierTaggerConfig> {
        public MainSettingsTab() {
            super("pxtiertagger.config", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    CyclingOption.ofBoolean("pxtiertagger.config.enabled", config.isEnabled(), config::setEnabled),
                    CyclingOption.ofBoolean("pxtiertagger.config.retired", config.isShowRetired(), config::setShowRetired),
                    CyclingOption.ofTranslatableEnum("pxtiertagger.config.highest", TierTaggerConfig.HighestMode.class, config.getHighestMode(), config::setHighestMode, OptionInstance.cachedConstantTooltip(Component.translatable("pxtiertagger.config.highest.desc"))),
                    CyclingOption.ofTranslatableEnum("pxtiertagger.config.position", TierTaggerConfig.NametagPosition.class, config.getNametagPosition(), config::setNametagPosition, OptionInstance.cachedConstantTooltip(Component.translatable("pxtiertagger.config.position.desc"))),
                    CyclingOption.ofTranslatableEnum("pxtiertagger.config.method", TierTaggerConfig.DrawMethod.class, config.getDrawMethod(), config::setDrawMethod, OptionInstance.cachedConstantTooltip(Component.translatable("pxtiertagger.config.method.desc"))),
                    CyclingOption.ofBoolean("pxtiertagger.config.own", config.isShowOwnTiers(), config::setShowOwnTiers),
                    CyclingOption.ofBoolean("pxtiertagger.config.others", config.isShowOtherTiers(), config::setShowOtherTiers),
                    CyclingOption.ofBoolean("pxtiertagger.config.icons", config.isShowIcons(), config::setShowIcons),
                    CyclingOption.ofBoolean("pxtiertagger.config.logos", config.isShowListLogos(), config::setShowListLogos),
                    CyclingOption.ofBoolean("pxtiertagger.config.playerList", config.isPlayerList(), config::setPlayerList),
                    CyclingOption.ofBoolean("pxtiertagger.config.chat", config.isChatTiers(), config::setChatTiers),
                    new SimpleButton("pxtiertagger.clear", b -> TierCache.clearCache()),
                    new ScreenOpenButton("pxtiertagger.config.search", PlayerSearchScreen::new)
            };
        }
    }

    /**
     * One row per tierlist: show it next to names or not, and which gamemode to show.
     */
    public class TierlistTab extends ButtonTab<TierTaggerConfig> {
        public TierlistTab() {
            super("pxtiertagger.config.tierlists", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            List<WidgetCreator> widgets = new ArrayList<>();

            // per tierlist: gamemode + three separate on/off switches (nametag, Tab list, profile menu)
            for (TierList list : TierList.values()) {
                String key = "pxtiertagger.list." + list.name().toLowerCase(Locale.ROOT);

                List<GameMode> modes = TierCache.getGamemodes(list);
                GameMode current = config.getMode(list);
                widgets.add(new CyclingOption<>(key + ".mode", modes, current, m -> config.setMode(list, m.id()),
                        m -> Component.literal(m.title()),
                        m -> m.isNone() ? Tooltip.create(Component.translatable("pxtiertagger.config.gamemode.none")) : null,
                        !current.isNone()));
                widgets.add(CyclingOption.ofBoolean(key + ".nametag", config.isShown(list), b -> config.setShown(list, b)));
                widgets.add(CyclingOption.ofBoolean(key + ".tab", config.isInTab(list), b -> config.setInTab(list, b)));
                widgets.add(CyclingOption.ofBoolean(key + ".menu", config.isInMenu(list), b -> config.setInMenu(list, b)));
                widgets.add(CyclingOption.ofBoolean(key + ".chat", config.isInChat(list), b -> config.setInChat(list, b)));
                // keep every tierlist starting on a new row (the tab is a 2-column grid)
                widgets.add(new SimpleButton(Component.empty(), b -> {}, false));
            }

            return widgets.toArray(WidgetCreator[]::new);
        }
    }

    public class ColorsTab extends ButtonTab<TierTaggerConfig> {
        protected ColorsTab() {
            super("pxtiertagger.colors", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            // i genuinely don't understand but chaining the calls just EXPLODES????
            Comparator<Map.Entry<String, Integer>> comparator = Comparator.comparing(e -> e.getKey().charAt(2));
            comparator = comparator.thenComparing(e -> e.getKey().charAt(0));

            List<ColorOption> tiers = config.getTierColors().entrySet().stream()
                    .sorted(comparator)
                    .map(e -> new ColorOption(e.getKey(), e.getValue(), val -> config.getTierColors().put(e.getKey(), val)))
                    .collect(Collectors.toList());

            tiers.addLast(new ColorOption("pxtiertagger.colors.retired", config.getRetiredColor(), config::setRetiredColor));

            return tiers.toArray(WidgetCreator[]::new);
        }
    }
}
