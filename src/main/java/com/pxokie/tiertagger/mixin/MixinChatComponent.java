package com.pxokie.tiertagger.mixin;

import com.pxokie.tiertagger.ChatDelay;
import com.pxokie.tiertagger.Debug;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Tiers next to names in chat messages that don't come through the server chat hook (e.g. signed
 * player chat on vanilla servers). Server messages were already handled by {@link ChatDelay}.
 */
@Mixin(ChatComponent.class)
public class MixinChatComponent {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component pxtiertagger$addTiers(Component message) {
        if (message == null) return null;
        try {
            Debug.hit("ChatComponent.addMessage", message.getString());
            if (ChatDelay.isHandled(message)) return message;
            return ChatDelay.decorate(message);
        } catch (Throwable t) {
            return message;
        }
    }
}
