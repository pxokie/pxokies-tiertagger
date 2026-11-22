package com.pxokie.tiertagger.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pxokie.tiertagger.TierTagger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the tiers right before a player's nametag is drawn. {@code Player#getDisplayName} is not always
 * used for the nametag (some clients, like Lunar, and some servers build it differently), so this
 * makes sure the tiers show up above every player's head.
 */
@Mixin(AvatarRenderer.class)
public class MixinAvatarRenderer {
    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), require = 0)
    private void pxtiertagger$addTiers(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (state.nameTag == null || !TierTagger.getManager().getConfig().isInNameText()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player)) return;

        state.nameTag = TierTagger.decorateNametag(player.getUUID(), state.nameTag);
    }
}
