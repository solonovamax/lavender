package io.wispforest.lavender.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.pond.LavenderFramebufferExtension;
import io.wispforest.owo.ui.event.WindowResizeCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class OffhandBookRenderer {

    public static boolean rendering = false;

    private static final Supplier<Framebuffer> BACK_BUFFER = Suppliers.memoize(() -> {
        var window = MinecraftClient.getInstance().getWindow();

        var framebuffer = new SimpleFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight(), true, MinecraftClient.IS_SYSTEM_MAC);
        ((LavenderFramebufferExtension)framebuffer).lavender$setUseCutoutBlit();
        framebuffer.setClearColor(0f, 0f, 0f, 0f);
        return framebuffer;
    });

    private static final Supplier<Framebuffer> DISPLAY_BUFFER = Suppliers.memoize(() -> {
        var window = MinecraftClient.getInstance().getWindow();

        var framebuffer = new SimpleFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight(), true, MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.setClearColor(0f, 0f, 0f, 0f);
        return framebuffer;
    });

    private static LavenderBookScreen cachedScreen = null;
    private static boolean cacheExpired = true;

    public static void initialize() {
        WindowResizeCallback.EVENT.register((client, window) -> {
            DISPLAY_BUFFER.get().resize(window.getFramebufferWidth(), window.getFramebufferHeight(), MinecraftClient.IS_SYSTEM_MAC);
            BACK_BUFFER.get().resize(window.getFramebufferWidth(), window.getFramebufferHeight(), MinecraftClient.IS_SYSTEM_MAC);
            cachedScreen = null;
        });
    }

    public static void beginFrame(@Nullable Book book) {
        cacheExpired = true;

        if (book == null) return;
        var client = MinecraftClient.getInstance();

        rendering = true;
        var backBuffer = BACK_BUFFER.get();

        try {
            // --- render book screen to separate framebuffer ---

            var screen = cachedScreen;
            if (screen == null || screen.book != book) {
                cachedScreen = screen = new LavenderBookScreen(book, true);
                screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());

                // we dispose the ui adapter here to
                // stop it from messing with and/or
                // leaking GLFW cursor objects
                screen.adapter().dispose();
            }

            var modelView = RenderSystem.getModelViewStack();
            modelView.push();
            modelView.loadIdentity();
            modelView.translate(0, 0, -2000);
            RenderSystem.applyModelViewMatrix();

            backBuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            backBuffer.beginWrite(false);

            screen.render(new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers()), -69, -69, 0);
            RenderSystem.disableDepthTest();

            modelView.pop();
            RenderSystem.applyModelViewMatrix();

            var displayBuffer = DISPLAY_BUFFER.get();
            displayBuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            displayBuffer.beginWrite(false);

            backBuffer.draw(backBuffer.textureWidth, backBuffer.textureHeight, false);

            client.getFramebuffer().beginWrite(false);
        } finally {
            rendering = false;
        }
    }

    public static void render(MatrixStack matrices, int light) {
        cacheExpired = false;
        var client = MinecraftClient.getInstance();

        // --- draw color attachment in place of map texture ---

        var framebuffer = DISPLAY_BUFFER.get();

        var texture = new FramebufferTexture(framebuffer.getColorAttachment());
        client.getTextureManager().registerTexture(Lavender.id("offhand_book_framebuffer"), texture);

        var rightHanded = client.player.getMainArm() == Arm.RIGHT;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rightHanded ? 15 : -15));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10));

        matrices.scale(1 * (framebuffer.textureWidth / (float) framebuffer.textureHeight), 1f, 1f);
        matrices.translate(rightHanded ? -.4f : -.6f, -.35f, -.165f);

        var buffer = client.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getText(Lavender.id("offhand_book_framebuffer")));
        var matrix = matrices.peek().getPositionMatrix();

        buffer.vertex(matrix, 0, 1, 0).color(1f, 1f, 1f, 1f).texture(0, 1).light(light).next();
        buffer.vertex(matrix, 0, 0, 0).color(1f, 1f, 1f, 1f).texture(0, 0).light(light).next();
        buffer.vertex(matrix, 1, 0, 0).color(1f, 1f, 1f, 1f).texture(1, 0).light(light).next();
        buffer.vertex(matrix, 1, 1, 0).color(1f, 1f, 1f, 1f).texture(1, 1).light(light).next();

        client.getBufferBuilders().getEntityVertexConsumers().draw();

        matrices.pop();
    }

    public static void endFrame() {
        if (cacheExpired) cachedScreen = null;
    }

    private static class FramebufferTexture extends AbstractTexture {

        private FramebufferTexture(int textureId) {
            this.glId = textureId;
        }

        @Override
        public void load(ResourceManager manager) {}

        @Override
        public void clearGlId() {}
    }
}
