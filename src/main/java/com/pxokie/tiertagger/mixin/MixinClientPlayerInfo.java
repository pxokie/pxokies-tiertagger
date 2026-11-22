package com.pxokie.tiertagger.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.pxokie.tiertagger.TierTagger;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds tiers to the name a player has in the Tab list. Clients with their own Tab list (like Lunar)
 * still ask the player entry for this name, so the tiers show up there too.
 */
@Mixin(PlayerInfo.class)
public class MixinClientPlayerInfo {
    @ModifyReturnValue(method = "getTabListDisplayName", at = @At("RETURN"), require = 0)
    private Component pxtiertagger$addTiers(Component original) {
        try {
            PlayerInfo self = (PlayerInfo) (Object) this;
            if (self.getProfile() == null || self.getProfile().id() == null) return original;
            if (!TierTagger.getManager().getConfig().isPlayerList()) return original;

            // no custom Tab name from the server: build the one vanilla would show (team colours + name)
            Component name = original != null ? original
                    : PlayerTeam.formatNameForTeam(self.getTeam(), Component.literal(self.getProfile().name()));
            Component decorated = TierTagger.decorateTab(self.getProfile().id(), name);
            return decorated == name ? original : decorated;
        } catch (Throwable t) {
            return original;
        }
    }
}
