package dev.ethanarrowood.blockdumper.mixin;

import dev.ethanarrowood.blockdumper.BlockImageDumperMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into Minecraft.renderFrame(boolean) at RETURN so we request a
 * GPU pixel-readback screenshot after the FULL frame pipeline has completed —
 * including blitToScreen() which is what actually submits GPU commands to
 * the command queue.  Injecting at GameRenderer.render() RETURN was too early:
 * render() only *builds* the command buffer; commands aren't on the GPU until
 * after blitToScreen(), so screenshots taken there captured the previous frame.
 */
@Mixin(Minecraft.class)
public abstract class GameRendererMixin {

    @Inject(
        method = "renderFrame(Z)V",
        at = @At("RETURN")
    )
    private void blockDumper_afterRenderFrame(boolean renderLevel, CallbackInfo ci) {
        BlockImageDumperMod.onFrameRendered();
    }
}
