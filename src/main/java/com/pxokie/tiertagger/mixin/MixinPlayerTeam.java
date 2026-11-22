package com.pxokie.tiertagger.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.pxokie.tiertagger.Debug;
import com.pxokie.tiertagger.TierTagger;
import com.pxokie.tiertagger.config.TierTaggerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lunar Client ignores the vanilla nametag text but still shows the server's team prefix/suffix
 * in front of/after names (like "◆ LT4 | Name"). These hooks put the tiers there, on Lunar only.
 */
@Mixin(PlayerTeam.class)
public class MixinPlayerTeam {
    @ModifyReturnValue(method = "formatNameForTeam", at = @At("RETURN"), require = 0)
    private static MutableComponent pxtiertagger$formatName(MutableComponent original, Team team, Component name) {
        try {
            if (!TierTaggerConfig.LUNAR || original == null || name == null) return original;
            Debug.hit("PlayerTeam.formatNameForTeam", original.getString());

            Component decorated = TierTagger.decorateByName(name.getString(), original);
            return decorated == original ? original : decorated.copy();
        } catch (Throwable t) {
            return original;
        }
    }

    @ModifyReturnValue(method = "getPlayerPrefix", at = @At("RETURN"), require = 0)
    private Component pxtiertagger$prefix(Component original) {
        return pxtiertagger$teamText(original, false);
    }

    @ModifyReturnValue(method = "getPlayerSuffix", at = @At("RETURN"), require = 0)
    private Component pxtiertagger$suffix(Component original) {
        return pxtiertagger$teamText(original, true);
    }

    private Component pxtiertagger$teamText(Component original, boolean suffix) {
        try {
            if (!TierTaggerConfig.LUNAR) return original;
            PlayerTeam self = (PlayerTeam) (Object) this;
            // only teams with exactly one player: otherwise the tiers would show for everyone in the team
            if (self.getPlayers().size() != 1) return original;

            Debug.hit(suffix ? "PlayerTeam.getPlayerSuffix" : "PlayerTeam.getPlayerPrefix", original == null ? "" : original.getString());
            String member = self.getPlayers().iterator().next();
            return TierTagger.decorateTeamText(member, original == null ? Component.empty() : original, suffix);
        } catch (Throwable t) {
            return original;
        }
    }
}
