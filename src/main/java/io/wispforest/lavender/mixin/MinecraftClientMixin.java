package io.wispforest.lavender.mixin;

import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.lavender.client.OffhandBookRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Nullable
    public Screen currentScreen;

    @Inject(method = "render", at = @At("HEAD"))
    private void onFrameStart(boolean tick, CallbackInfo ci) {
        OffhandBookRenderer.beginFrame();
        if (this.player == null) return;

        var offhandStack = this.player.getOffHandStack();
        if (offhandStack.getItem() instanceof LavenderBookItem && LavenderBookItem.bookOf(offhandStack) != null && !(this.currentScreen instanceof LavenderBookScreen)) {
            OffhandBookRenderer.prepareTexture(LavenderBookItem.bookOf(offhandStack));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onFrameEnd(boolean tick, CallbackInfo ci) {
        OffhandBookRenderer.endFrame();
    }
}
