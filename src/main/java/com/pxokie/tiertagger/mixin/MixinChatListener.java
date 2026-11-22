package com.pxokie.tiertagger.mixin;

import com.pxokie.tiertagger.ChatDelay;
import com.pxokie.tiertagger.Debug;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches server chat messages before chat mods do, so the tiers are already in the message
 * when any chat (vanilla or Lunar's) shows it.
 */
@Mixin(ChatListener.class)
public class MixinChatListener {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void pxtiertagger$addTiers(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay || message == null) return;
        try {
            Debug.hit("ChatListener.handleSystemMessage", message.getString());
            if (ChatDelay.intercept(message)) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
