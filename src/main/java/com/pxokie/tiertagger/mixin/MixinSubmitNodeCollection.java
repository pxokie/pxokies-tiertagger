package com.pxokie.tiertagger.mixin;

import com.pxokie.tiertagger.TierTagger;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every nametag ends up here right before it is drawn, including the ones Lunar Client builds itself.
 * Adding the tiers at this point makes "Left"/"Right" work on clients that ignore the vanilla name text.
 */
@Mixin(SubmitNodeCollection.class)
public class MixinSubmitNodeCollection {
    @ModifyVariable(method = "submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component pxtiertagger$addTiers(Component text) {
        if (text == null) return null;
        try {
            return TierTagger.decorateRenderedNametag(text);
        } catch (Throwable t) {
            return text;
        }
    }
}
