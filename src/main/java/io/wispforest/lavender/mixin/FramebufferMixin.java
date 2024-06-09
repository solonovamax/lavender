package io.wispforest.lavender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.GlStateManager;
import io.wispforest.lavender.client.LavenderClient;
import io.wispforest.lavender.pond.LavenderFramebufferExtension;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(Framebuffer.class)
public class FramebufferMixin implements LavenderFramebufferExtension {

    @Unique
    private Supplier<ShaderProgram> blitProgram = null;

    @Override
    public void lavender$setBlitProgram(Supplier<ShaderProgram> blitProgram) {
        this.blitProgram = blitProgram;
    }

    @ModifyExpressionValue(method = "drawInternal", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;blitScreenProgram:Lnet/minecraft/client/gl/ShaderProgram;"))
    private ShaderProgram applyBlitProgram(ShaderProgram original) {
        if (this.blitProgram == null) return original;

        return this.blitProgram.get();
    }
}
