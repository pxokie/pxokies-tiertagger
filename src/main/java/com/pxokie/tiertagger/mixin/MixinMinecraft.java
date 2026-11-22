package com.pxokie.tiertagger.mixin;

import com.pxokie.tiertagger.Prefetcher;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void pxtiertagger$prefetch(CallbackInfo ci) {
        try {
            Prefetcher.tick((Minecraft) (Object) this);
        } catch (Throwable ignored) {
            // never break the game loop
        }
    }
}
