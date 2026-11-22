package com.pxokie.tiertagger.mixin;

import com.pxokie.tiertagger.TabTiers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the Tab name the server gives a player, so {@link TabTiers} can add the tiers to it.
 */
@Mixin(PlayerInfo.class)
public class MixinClientPlayerInfo {
    @Inject(method = "setTabListDisplayName", at = @At("HEAD"), require = 0)
    private void pxtiertagger$rememberOriginal(Component name, CallbackInfo ci) {
        try {
            TabTiers.onServerSet((PlayerInfo) (Object) this, name);
        } catch (Throwable ignored) {
        }
    }
}
