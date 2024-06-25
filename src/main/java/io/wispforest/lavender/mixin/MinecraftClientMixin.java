package io.wispforest.lavender.mixin;

import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.lavender.client.OffhandBookRenderer;
import io.wispforest.lavender.client.StructureOverlayRenderer;
import io.wispforest.lavender.util.RaycastResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Nullable
    public Screen currentScreen;

    @Shadow
    @Nullable
    public ClientWorld world;

    @Shadow
    @Nullable
    public Entity cameraEntity;

    @Shadow
    @Nullable
    public HitResult crosshairTarget;

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Shadow
    @Final
    private RenderTickCounter renderTickCounter;

    @Inject(method = "render", at = @At("HEAD"))
    private void onFrameStart(boolean tick, CallbackInfo ci) {
        if (this.player == null) return;

        Book bookToRender = null;
        var offhandStack = this.player.getOffHandStack();
        if (offhandStack.getItem() instanceof LavenderBookItem && LavenderBookItem.bookOf(offhandStack) != null && !(this.currentScreen instanceof LavenderBookScreen)) {
            bookToRender = LavenderBookItem.bookOf(offhandStack);
        }

        OffhandBookRenderer.beginFrame(bookToRender);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onFrameEnd(boolean tick, CallbackInfo ci) {
        if (this.player == null) return;
        OffhandBookRenderer.endFrame();
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    void onItemPick(final CallbackInfo ci) {
        if (this.player == null || this.cameraEntity == null || this.interactionManager == null)
            return;

        double blockRange = this.interactionManager.getReachDistance();
        float tickDelta = this.renderTickCounter.tickDelta;
        Vec3d rotation = this.player.getRotationVec(tickDelta);
        Vec3d rayLength = rotation.multiply(blockRange);
        Vec3d cameraPos = this.player.getCameraPosVec(tickDelta);

        var firstOverlayHit = StructureOverlayRenderer.getActiveOverlays()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().fetchStructure() != null)
                .map(entry -> {
                    BlockPos pos = entry.getKey();
                    var overlayEntry = entry.getValue();
                    var template = overlayEntry.fetchStructure();
                    assert template != null;

                    Vec3d rayStart = cameraPos.subtract(Vec3d.of(pos));
                    Vec3d rayEnd = rayStart.add(rayLength);
                    var context = new RaycastContext(rayStart, rayEnd, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, this.player);
                    var raycast = template.asBlockRenderView().raycast(context);

                    return new RaycastResult(
                            raycast,
                            rayStart,
                            rayEnd,
                            template.asBlockRenderView().getBlockState(raycast.getBlockPos()),
                            template
                    );
                })
                .min(Comparator.comparingDouble((raycast) -> raycast.hitResult().getPos().squaredDistanceTo(raycast.raycastStart())));

        firstOverlayHit.ifPresent((raycast) -> {
            double hitDistance = raycast.hitResult().getPos().squaredDistanceTo(raycast.raycastStart());
            double crosshairDistance = this.crosshairTarget != null ? this.crosshairTarget.getPos().squaredDistanceTo(cameraPos) : 0.0;

            if (crosshairDistance < hitDistance) // slightly prefer structure block
                return;

            ItemStack stack = raycast.block().getBlock().asItem().getDefaultStack();

            PlayerInventory playerInventory = this.player.getInventory();

            int i = playerInventory.getSlotWithStack(stack);
            if (this.player.getAbilities().creativeMode) {
                playerInventory.addPickBlock(stack);
                this.interactionManager.clickCreativeStack(this.player.getStackInHand(Hand.MAIN_HAND), 36 + playerInventory.selectedSlot);
            } else if (i != -1) {
                if (PlayerInventory.isValidHotbarIndex(i)) {
                    playerInventory.selectedSlot = i;
                } else {
                    this.interactionManager.pickFromInventory(i);
                }
            }

            ci.cancel();
        });
    }
}
