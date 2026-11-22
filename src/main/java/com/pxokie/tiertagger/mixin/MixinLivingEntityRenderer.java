package com.pxokie.tiertagger.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pxokie.tiertagger.TierTagger;
import com.pxokie.tiertagger.config.TierTaggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Above Name" mode: draws the tiers as their own line above a player's nametag. This doesn't depend
 * on how the nametag itself is built, so it also works on Lunar Client.
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    /**
     * Height of one nametag line, in blocks (same as vanilla: 9 px * 1.15 * 0.025).
     */
    @Unique
    private static final float LINE = 9 * 1.15f * 0.025f;
    @Unique
    private static final double MAX_DISTANCE_SQ = 64 * 64;
    @Unique
    private static boolean loggedOnce = false;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void pxtiertagger$drawTiersAboveName(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) return;

        TierTaggerConfig config = TierTagger.getManager().getConfig();
        if (!config.isEnabled() || !config.isTiersAboveName()) return;
        if (state.distanceToCameraSq > MAX_DISTANCE_SQ) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(avatar.id);
        if (!(entity instanceof Player player) || player == mc.player) return;
        if (player.isInvisibleTo(mc.player)) return;

        Component tiers = TierTagger.buildTiers(player.getUUID(), false);
        if (tiers == null) return;

        if (!loggedOnce) {
            loggedOnce = true;
            System.out.println("[Pxokie's TierTagger] Drawing tiers above nametags" + (TierTaggerConfig.LUNAR ? " (Lunar Client detected)" : ""));
        }

        Vec3 attachment = state.nameTagAttachment != null ? state.nameTagAttachment : new Vec3(0, state.boundingBoxHeight + 0.5, 0);
        // one line above the name, or two when a score ("20") is shown under the name
        int lines = avatar.scoreText != null ? 2 : 1;

        poseStack.pushPose();
        poseStack.translate(0, lines * LINE, 0);
        collector.submitNameTag(poseStack, attachment, 0, tiers, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
        poseStack.popPose();
    }
}
