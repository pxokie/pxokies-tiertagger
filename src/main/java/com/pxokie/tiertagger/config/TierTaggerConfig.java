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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    /**
     * Show tiers in the player list (Tab) at all.
     */
    private boolean playerList = true;
    /**
     * Show tiers in front of chat messages from players.
     */
    private boolean chatTiers = true;
    /**
     * Show tiers on your own name (Tab list, F5).
     */
    private boolean showOwnTiers = true;
    /**
     * Show tiers on other players' names.
     */
    private boolean showOtherTiers = true;
    /**
     * Where the tiers go on a nametag: left of the name, right of it, on a line above it, or nowhere.
     */
    private NametagPosition nametagPosition = NametagPosition.LEFT;
    /**
     * How the tiers get onto the nametag. Lunar Client ignores changes to the name itself.
     */
    private DrawMethod drawMethod = DrawMethod.AUTO;
    private int retiredColor = 0xa2d6ff;
    // note: this is a GSON internal class. this *might* break in the future
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();

    /**
     * Which tierlists are shown on nametags (key: {@link TierList#name()}).
     */
    private LinkedTreeMap<String, Boolean> shownLists = defaultTrue();
    /**
     * Which tierlists are shown in the player list (Tab).
     */
    private LinkedTreeMap<String, Boolean> tabLists = defaultTrue();
    /**
     * Which tierlists are shown in chat.
     */
    private LinkedTreeMap<String, Boolean> chatLists = defaultTrue();
    /**
     * Which tierlists are shown in the profile menu and the /pxtiers command.
     */
    private LinkedTreeMap<String, Boolean> menuLists = defaultTrue();
    /**
     * The gamemode picked for each tierlist (key: {@link TierList#name()}, value: gamemode id).
     */
    private LinkedTreeMap<String, String> listModes = new LinkedTreeMap<>();

    // === per tierlist on/off, each one separate ===

    public boolean isShown(TierList list) {
        if (shownLists == null) shownLists = defaultTrue();
        return shownLists.getOrDefault(list.name(), true);
    }

    public void setShown(TierList list, boolean shown) {
        if (shownLists == null) shownLists = defaultTrue();
        shownLists.put(list.name(), shown);
    }

    public boolean isInTab(TierList list) {
        if (tabLists == null) tabLists = defaultTrue();
        return tabLists.getOrDefault(list.name(), true);
    }

    public void setInTab(TierList list, boolean shown) {
        if (tabLists == null) tabLists = defaultTrue();
        tabLists.put(list.name(), shown);
    }

    public boolean isInChat(TierList list) {
        if (chatLists == null) chatLists = defaultTrue();
        return chatLists.getOrDefault(list.name(), true);
    }

    public void setInChat(TierList list, boolean shown) {
        if (chatLists == null) chatLists = defaultTrue();
        chatLists.put(list.name(), shown);
    }

    public List<TierList> chatTierLists() {
        return Arrays.stream(TierList.values()).filter(this::isInChat).toList();
    }

    public boolean isInMenu(TierList list) {
        if (menuLists == null) menuLists = defaultTrue();
        return menuLists.getOrDefault(list.name(), true);
    }

    public void setInMenu(TierList list, boolean shown) {
        if (menuLists == null) menuLists = defaultTrue();
        menuLists.put(list.name(), shown);
    }

    public List<TierList> shownTierLists() {
        return Arrays.stream(TierList.values()).filter(this::isShown).toList();
    }

    public List<TierList> tabTierLists() {
        return Arrays.stream(TierList.values()).filter(this::isInTab).toList();
    }

    public List<TierList> menuTierLists() {
        return Arrays.stream(TierList.values()).filter(this::isInMenu).toList();
    }

    // === gamemodes ===

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

    // === nametag placement ===

    public NametagPosition getNametagPosition() {
        return nametagPosition == null ? NametagPosition.LEFT : nametagPosition;
    }

    public DrawMethod getDrawMethod() {
        return drawMethod == null ? DrawMethod.AUTO : drawMethod;
    }

    /**
     * True when the mod draws the tiers itself next to/above the nametag, instead of changing the
     * name text. Needed on Lunar Client, where changes to the name text never show up.
     */
    public boolean isDrawnSeparately() {
        // Auto edits the name text: the mod hooks the last step before a nametag is drawn,
        // which also catches nametags that Lunar Client builds itself
        // On Lunar Client, servers can send their own nametags (Apollo) that the mod can't change,
        // so Auto draws the tiers there as a separate line
        return getDrawMethod() == DrawMethod.SEPARATE || getNametagPosition() == NametagPosition.ABOVE
                || (getDrawMethod() == DrawMethod.AUTO && LUNAR);
    }

    /**
     * Where separately drawn tiers go. On Lunar with Auto, the width of the server's nametag is
     * unknown, so left/right would overlap it: the tiers go above the nametag instead.
     */
    public NametagPosition getSeparatePosition() {
        NametagPosition pos = getNametagPosition();
        if (getDrawMethod() == DrawMethod.AUTO && LUNAR && (pos == NametagPosition.LEFT || pos == NametagPosition.RIGHT)) {
            return NametagPosition.ABOVE;
        }
        return pos;
    }

    /**
     * True when the tiers go into the name text itself (vanilla, Fabric launchers).
     */
    public boolean isInNameText() {
        return getNametagPosition() != NametagPosition.OFF && !isDrawnSeparately();
    }

    /**
     * Lunar Client draws nametags itself and ignores the vanilla name text.
     */
    public static final boolean LUNAR = detectLunar();

    private static boolean detectLunar() {
        String cp = System.getProperty("java.class.path", "").toLowerCase(Locale.ROOT);
        if (cp.contains("lunar.jar") || cp.contains(".lunarclient") || cp.contains("lunarclient")) return true;
        if (System.getProperty("lunar.version") != null || System.getProperty("ichor.version") != null) return true;
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lunar")
                    || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lunarclient")
                    || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ichor");
        } catch (Throwable t) {
            return false;
        }
    }

    private static LinkedTreeMap<String, Boolean> defaultTrue() {
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

    @Getter
    @AllArgsConstructor
    public enum NametagPosition implements StringTranslatable {
        LEFT("left", "pxtiertagger.position.left"),
        RIGHT("right", "pxtiertagger.position.right"),
        ABOVE("above", "pxtiertagger.position.above"),
        OFF("off", "pxtiertagger.position.off"),
        ;

        private final String name;
        private final String translationKey;
    }

    @Getter
    @AllArgsConstructor
    public enum DrawMethod implements StringTranslatable {
        AUTO("auto", "pxtiertagger.method.auto"),
        EDIT_NAME("edit_name", "pxtiertagger.method.edit_name"),
        SEPARATE("separate", "pxtiertagger.method.separate"),
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
