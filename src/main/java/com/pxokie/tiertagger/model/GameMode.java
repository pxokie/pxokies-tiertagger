package com.pxokie.tiertagger.model;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.Optional;

public record GameMode(String id, String title) {
    public static final GameMode NONE = new GameMode("annoying_long_id_that_no_one_will_ever_use_just_to_make_sure", "§cNone§r");

    public boolean isNone() {
        return this.id.equals(NONE.id);
    }

    /**
     * Turns an API id like "dia_smp" into "Dia Smp" for modes a tierlist doesn't give a title for.
     */
    public static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.replace('-', '_').split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.isEmpty() ? id : sb.toString();
    }

    /**
     * Different tierlists use different ids for the same kit ("nethop", "neth_pot", "NETH"...).
     * This returns one shared name per kit, so kits can be matched across tierlists.
     */
    public String canonical() {
        String key = this.id.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");

        return switch (key) {
            case "nethop", "neth_pot", "nethpot", "netherite_pot", "nethop_pot", "neth" -> "nethpot";
            case "pot", "diapot", "dia_pot" -> "pot";
            case "smp", "netherite_smp", "neth_smp" -> "smp";
            case "vanilla", "crystal", "cpvp", "crystal_pvp" -> "crystal";
            case "dia_smp", "diasmp", "diamond_smp" -> "dia_smp";
            case "minecart", "cart" -> "minecart";
            case "og_vanilla", "ogvanilla" -> "og_vanilla";
            default -> key;
        };
    }

    private Pair<Character, TextColor> iconAndColor() {
        return switch (canonical()) {
            case "axe" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.GREEN));
            case "mace" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.GRAY));
            case "nethpot" -> Pair.of('', TextColor.fromRgb(0x7d4a40));
            case "pot" -> Pair.of('', TextColor.fromRgb(0xff0000));
            case "smp" -> Pair.of('', TextColor.fromRgb(0xeccb45));
            case "sword" -> Pair.of('', TextColor.fromRgb(0xa4fdf0));
            case "uhc" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.RED));
            case "crystal" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE));
            case "bed" -> Pair.of('', TextColor.fromRgb(0xff0000));
            case "bow" -> Pair.of('', TextColor.fromRgb(0x663d10));
            case "creeper" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.GREEN));
            case "debuff" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY));
            case "dia_crystal" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.AQUA));
            case "dia_smp" -> Pair.of('', TextColor.fromRgb(0x8c668b));
            case "elytra" -> Pair.of('', TextColor.fromRgb(0x8d8db1));
            case "manhunt" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.RED));
            case "minecart" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.GRAY));
            case "og_vanilla" -> Pair.of('', TextColor.fromLegacyFormat(ChatFormatting.GOLD));
            case "speed" -> Pair.of('', TextColor.fromRgb(0x43a9d1));
            case "trident" -> Pair.of('', TextColor.fromRgb(0x579b8c));
            default -> Pair.of('•', TextColor.fromLegacyFormat(ChatFormatting.WHITE));
        };
    }

    public Optional<Character> icon() {
        Pair<Character, TextColor> pair = this.iconAndColor();

        return pair.right().getValue() == 0xFFFFFF ? Optional.empty() : Optional.of(pair.left());
    }

    public Component asStyled(boolean withDefaultDot) {
        Pair<Character, TextColor> pair = this.iconAndColor();

        if (pair.right().getValue() == 0xFFFFFF && !withDefaultDot) {
            return Component.literal(this.title);
        } else {
            Component name = Component.literal(this.title).withStyle(s -> s.withColor(pair.right()));
            return Component.literal(pair.left() + " ").append(name);
        }
    }
}
