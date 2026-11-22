package com.pxokie.tiertagger.mixin;

import com.pxokie.tiertagger.TierTagger;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Puts the sender's tiers in front of chat messages.
 */
@Mixin(ChatComponent.class)
public class MixinChatComponent {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component pxtiertagger$addTiers(Component message) {
        if (message == null) return null;
        try {
            return TierTagger.decorateChat(message);
        } catch (Throwable t) {
            return message;
        }
    }
}
