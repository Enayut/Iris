package net.irisshaders.iris.mixin.vertices.block_rendering;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BufferBuilder.class, priority = 1001)
public abstract class MixinBufferBuilder_SeparateAo implements VertexConsumer {
}
