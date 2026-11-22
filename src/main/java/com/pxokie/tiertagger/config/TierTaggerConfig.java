package com.pxokie.tiertagger.config;

import com.google.gson.internal.LinkedTreeMap;
import com.pxokie.tiertagger.TierCache;
import com.pxokie.tiertagger.model.GameMode;
import com.pxokie.tiertagger.model.TierList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.uku3lig.ukulib.config.option.StringTranslatable;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierTaggerConfig implements Serializable {
    private boolean enabled = true;
    private boolean showRetired = true;
    private HighestMode highestMode = HighestMode.NOT_FOUND;
    private boolean showIcons = true;
    /**
     * Show the logo of the tierlist in front of each tier next to a name.
     */
    private boolean showListLogos = true;
    private boolean playerList = true;
    /**
     * Where the tiers go above a player's head: in front of the name, or as a line above it.
     */
    private TierPosition tierPosition = TierPosition.AUTO;
    private int retiredColor = 0xa2d6ff;
    // note: this is a GSON internal class. this *might* break in the future
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();

    /**
     * Which tierlists are shown next to names (key: {@link TierList#name()}).
     */
    private LinkedTreeMap<String, Boolean> shownLists = defaultShown();
    /**
     * The gamemode picked for each tierlist (key: {@link TierList#name()}, value: gamemode id).
     */
    private LinkedTreeMap<String, String> listModes = new LinkedTreeMap<>();

    public boolean isShown(TierList list) {
        if (shownLists == null) shownLists = defaultShown();
        return shownLists.getOrDefault(list.name(), true);
    }

    public void setShown(TierList list, boolean shown) {
        if (shownLists == null) shownLists = defaultShown();
        shownLists.put(list.name(), shown);
    }

    public List<TierList> shownTierLists() {
        return java.util.Arrays.stream(TierList.values()).filter(this::isShown).toList();
    }

    /**
     * The gamemode shown for this tierlist. Falls back to the first gamemode of the list,
     * or {@link GameMode#NONE} while the list's gamemodes haven't loaded.
     */
    public GameMode getMode(TierList list) {
        if (listModes == null) listModes = new LinkedTreeMap<>();
        String id = listModes.get(list.name());

        if (id != null) {
            Optional<GameMode> opt = TierCache.findMode(list, id);
            if (opt.isPresent()) return opt.get();
        }

        GameMode first = TierCache.getGamemodes(list).getFirst();
        if (!first.isNone() && id == null) listModes.put(list.name(), first.id());
        return first;
    }

    public void setMode(TierList list, String modeId) {
        if (listModes == null) listModes = new LinkedTreeMap<>();
        listModes.put(list.name(), modeId);
    }

    private static LinkedTreeMap<String, Boolean> defaultShown() {
        LinkedTreeMap<String, Boolean> shown = new LinkedTreeMap<>();
        for (TierList list : TierList.values()) shown.put(list.name(), true);
        return shown;
    }

    private static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xe8ba3a);
        colors.put("LT1", 0xd5b355);
        colors.put("HT2", 0xc4d3e7);
        colors.put("LT2", 0xa0a7b2);
        colors.put("HT3", 0xf89f5a);
        colors.put("LT3", 0xc67b42);
        colors.put("HT4", 0x81749a);
        colors.put("LT4", 0x655b79);
        colors.put("HT5", 0x8f82a8);
        colors.put("LT5", 0x655b79);

        return colors;
    }

    /**
     * True when the tiers should be drawn as their own line above the nametag.
     */
    public boolean isTiersAboveName() {
        return switch (tierPosition == null ? TierPosition.AUTO : tierPosition) {
            case ABOVE_NAME -> true;
            case NEXT_TO_NAME -> false;
            case AUTO -> LUNAR;
        };
    }

    /**
     * Lunar Client draws nametags itself and ignores the vanilla name, so tiers in front of the
     * name never show up there.
     */
    public static final boolean LUNAR = detectLunar();

    private static boolean detectLunar() {
        String cp = System.getProperty("java.class.path", "").toLowerCase(java.util.Locale.ROOT);
        if (cp.contains("lunar.jar") || cp.contains(".lunarclient")) return true;
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lunar");
        } catch (Throwable t) {
            return false;
        }
    }

    @Getter
    @AllArgsConstructor
    public enum TierPosition implements StringTranslatable {
        AUTO("auto", "pxtiertagger.position.auto"),
        NEXT_TO_NAME("next_to_name", "pxtiertagger.position.next_to_name"),
        ABOVE_NAME("above_name", "pxtiertagger.position.above_name"),
        ;

        private final String name;
        private final String translationKey;
    }

    @Getter
    @AllArgsConstructor
    public enum HighestMode implements StringTranslatable {
        NEVER("never", "pxtiertagger.highest.never"),
        NOT_FOUND("not_found", "pxtiertagger.highest.not_found"),
        ALWAYS("always", "pxtiertagger.highest.always"),
        ;

        private final String name;
        private final String translationKey;
    }
}
