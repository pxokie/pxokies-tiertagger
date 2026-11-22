package com.pxokie.tiertagger.model;

import com.pxokie.tiertagger.source.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum TierList {
    MCTIERS("MCTiers", '', '', 0xFFC83D, new McTiersSource("https://mctiers.net/api")),
    PVPTIERS("PvPTiers", '', '', 0xF5873A, new PvpTiersSource()),
    SUBTIERS("SubTiers", '', '', 0x3FB8F0, new McTiersSource("https://subtiers.net/api")),
    TIERTESTS("TierTests", '', '', 0xC15BF0, new TierTestsSource()),
    MCPVP("MCPvP", '', '', 0xE84545, new McPvpSource()),
    ;

    private final String name;
    /**
     * Logo glyph for menus (12 px).
     */
    private final char icon;
    /**
     * Logo glyph sized to fit next to a nametag (8 px).
     */
    private final char smallIcon;
    /**
     * Accent colour used for the tierlist's name in menus.
     */
    private final int color;
    private final TierSource source;

    public String styledName(boolean current) {
        String s = icon + " " + name;
        if (current) s += " (selected)";
        return s;
    }

    public static TierList byId(String id) {
        return Arrays.stream(values()).filter(l -> l.name().equalsIgnoreCase(id)).findFirst().orElse(MCTIERS);
    }
}
