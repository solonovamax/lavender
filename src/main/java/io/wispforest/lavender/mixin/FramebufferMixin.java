package io.wispforest.lavender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.GlStateManager;
import io.wispforest.lavender.client.LavenderClient;
import io.wispforest.lavender.pond.LavenderFramebufferExtension;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(Framebuffer.class)
public class FramebufferMixin implements LavenderFramebufferExtension {

    private boolean useCutoutBlit = false;

    @Override
    public void lavender$setUseCutoutBlit() {
        this.useCutoutBlit = true;
    }

    @ModifyExpressionValue(method = "drawInternal", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;blitScreenProgram:Lnet/minecraft/client/gl/ShaderProgram;"))
    private ShaderProgram applyBlitProgram(ShaderProgram original) {
        if (!this.useCutoutBlit) return original;

        GlStateManager._colorMask(true, true, true, true);
        return LavenderClient.BLIT_CUTOUT_PROGRAM.program();
    }
}
