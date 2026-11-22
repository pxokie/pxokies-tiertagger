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
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the tiers as their own text next to (left/right) or above a player's nametag. This doesn't
 * depend on the name text, so it also works on Lunar Client, which draws nametags itself.
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    /**
     * Height of one nametag line, in blocks (same as vanilla: 9 px * 1.15 * 0.025).
     */
    @Unique
    private static final float LINE = 9 * 1.15f * 0.025f;
    /**
     * Size of one text pixel on a nametag, in blocks.
     */
    @Unique
    private static final float PIXEL = 0.025f;
    /**
     * Space between the name and the tiers, in text pixels.
     */
    @Unique
    private static final int GAP = 4;
    /**
     * Room Lunar Client takes for the icon on each side of a name, in text pixels.
     */
    @Unique
    private static final int LUNAR_ICON = 13;
    @Unique
    private static final double MAX_DISTANCE_SQ = 64 * 64;
    @Unique
    private static boolean loggedOnce = false;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void pxtiertagger$drawTiers(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        com.pxokie.tiertagger.Debug.hit("LivingEntityRenderer.submit", state.nameTag == null ? "nameTag=null" : state.nameTag.getString());

        TierTaggerConfig config = TierTagger.getManager().getConfig();
        TierTaggerConfig.NametagPosition position = config.getSeparatePosition();
        if (!config.isEnabled() || !config.isDrawnSeparately() || position == TierTaggerConfig.NametagPosition.OFF) return;
        if (state.distanceToCameraSq > MAX_DISTANCE_SQ) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(avatar.id);
        if (!(entity instanceof Player player) || player.isInvisibleTo(mc.player)) return;
        // your own nametag is only visible in F5
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (!TierTagger.showsTiersFor(player.getUUID())) return;

        Component tiers = TierTagger.buildTiers(player.getUUID(), config.shownTierLists(), TierTagger.Placement.ALONE);
        if (tiers == null) return;

        if (!loggedOnce) {
            loggedOnce = true;
            System.out.println("[Pxokie's TierTagger] Drawing tiers separately (" + position + ")"
                    + (TierTaggerConfig.LUNAR ? ", Lunar Client detected" : ""));
        }

        // same spot vanilla uses for the name (the nametag renderer itself adds the 0.5 block on top)
        Vec3 attachment = state.nameTagAttachment != null ? state.nameTagAttachment : new Vec3(0, state.boundingBoxHeight, 0);
        // vanilla moves the name one line up when a score ("20") is shown under it; Lunar keeps the name
        // at the normal height
        float nameLine = avatar.scoreText != null && !TierTaggerConfig.LUNAR ? LINE : 0;

        poseStack.pushPose();
        if (position == TierTaggerConfig.NametagPosition.ABOVE) {
            poseStack.translate(0, nameLine + LINE, 0);
        } else {
            int nameWidth;
            if (TierTaggerConfig.LUNAR) {
                // Lunar draws the plain name with an icon on the left (Lunar logo) and a badge on the right
                nameWidth = mc.font.width(player.getScoreboardName()) + 2 * LUNAR_ICON;
            } else {
                Component name = state.nameTag != null ? state.nameTag : player.getDisplayName();
                nameWidth = mc.font.width(name);
            }
            int tiersWidth = mc.font.width(tiers);

            // move sideways along the camera's right direction, so the tiers sit next to the name
            float px = (nameWidth / 2f + GAP + tiersWidth / 2f) * (position == TierTaggerConfig.NametagPosition.LEFT ? -1 : 1);
            Vector3f right = camera.orientation != null
                    ? camera.orientation.transform(new Vector3f(px * PIXEL, 0, 0))
                    : new Vector3f(px * PIXEL, 0, 0);
            poseStack.translate(right.x(), nameLine + right.y(), right.z());
        }
        collector.submitNameTag(poseStack, attachment, 0, tiers, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
        poseStack.popPose();
    }
}
