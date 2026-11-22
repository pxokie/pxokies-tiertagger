package com.pxokie.tiertagger.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.pxokie.tiertagger.TierTagger;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class MixinPlayer {
    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    public Component prependTier(Component original) {
        Player self = (Player) (Object) this;
        com.pxokie.tiertagger.Debug.hit("Player.getDisplayName", original == null ? "" : original.getString());
        return TierTagger.decorateNametag(self.getUUID(), original);
    }
}
