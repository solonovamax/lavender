package io.wispforest.lavender.pond;

import net.minecraft.client.gl.ShaderProgram;

import java.util.function.Supplier;

public interface LavenderFramebufferExtension {
    void lavender$setBlitProgram(Supplier<ShaderProgram> program);
}
