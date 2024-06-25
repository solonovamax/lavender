package io.wispforest.lavender.util;

import io.wispforest.lavender.structure.StructureTemplate;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

public record RaycastResult(
        BlockHitResult hitResult,
        Vec3d raycastStart,
        Vec3d raycastEnd,
        BlockState block,
        StructureTemplate template
) {
}
