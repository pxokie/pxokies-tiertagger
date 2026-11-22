package com.pxokie.tiertagger.mixin;

import com.pxokie.tiertagger.TierTagger;
import com.pxokie.tiertagger.config.TierTaggerConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    @Nullable
    public Component prependTier(Component original, PlayerInfo entry) {
        if (original == null) return null;
        com.pxokie.tiertagger.Debug.hit("PlayerTabOverlay.getNameForDisplay", original.getString());
        return TierTagger.decorateTab(entry.getProfile().id(), original);
    }
}
