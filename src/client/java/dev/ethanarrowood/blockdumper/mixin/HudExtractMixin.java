package dev.ethanarrowood.blockdumper.mixin;

import dev.ethanarrowood.blockdumper.BlockImageDumperMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects at the RETURN of Hud.extractRenderState so we can add our item-icon
 * draw calls directly to the GuiGraphicsExtractor.  This bypasses the Fabric
 * HudElementRegistry entirely (addLast targets the SUBTITLES root layer which
 * has no Fabric mixin wrapper and is never invoked).
 *
 * Pre-26.2 this method lived on Gui with the same signature; 26.2 split the
 * per-frame HUD extraction out of Gui into the new Hud class (Gui's own
 * extractRenderState is now (DeltaTracker, boolean, boolean) and delegates
 * here).
 */
@Mixin(Hud.class)
public abstract class HudExtractMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("RETURN")
    )
    private void blockDumper_onExtract(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        BlockImageDumperMod.onGuiExtract(graphics);
    }
}
