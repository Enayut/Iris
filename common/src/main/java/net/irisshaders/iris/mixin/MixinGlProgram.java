package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout.UniformDescription;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.Uniform;
import com.mojang.renderpearl.backend.opengl.Uniform.Sampler;
import com.mojang.renderpearl.backend.opengl.Uniform.Ubo;
import com.mojang.renderpearl.backend.opengl.Uniform.Utb;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.irisshaders.iris.mixinterface.GlProgramBindings;
import net.irisshaders.iris.pipeline.programs.IrisProgram;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlProgram.class)
public class MixinGlProgram implements GlProgramBindings {
   @Shadow
   @Final
   private List<Uniform> uniforms;
   @Shadow
   private Ubo pushConstant;
   @Unique
   private final Map<String, Utb> iris$textureBuffers = new HashMap<>();

   @Inject(method = "setupBindGroupLayouts", at = @At("HEAD"), cancellable = true)
   private void iris$setupInitialBindings(List<UniformDescription> descriptions, CallbackInfo ci) {
      if (this instanceof IrisProgram) {
         this.iris$setupBindings(descriptions, 0);
         ci.cancel();
      }
   }

   @Override
   public void iris$setupBindings(List<UniformDescription> descriptions, int pushConstantsSize) {
      this.pushConstant = pushConstantsSize > 0 ? new Ubo(6) : null;

      while (this.uniforms.size() < Math.max(descriptions.size(), 32)) {
         this.uniforms.add(null);
      }

      for (int i = 0; i < this.uniforms.size(); i++) {
         this.uniforms.set(i, null);
      }

      for (int i = 0; i < descriptions.size(); i++) {
         this.uniforms.set(i, this.iris$createBinding(descriptions.get(i)));
      }
   }

   @Inject(method = "close", at = @At("HEAD"))
   private void iris$deleteTextureBuffers(CallbackInfo ci) {
      if (!this.iris$textureBuffers.isEmpty()) {
         for (int i = 0; i < this.uniforms.size(); i++) {
            if (this.uniforms.get(i) instanceof Utb) {
               this.uniforms.set(i, null);
            }
         }

         this.iris$textureBuffers.values().forEach(Utb::close);
         this.iris$textureBuffers.clear();
      }
   }

   @Unique
   private Uniform iris$createBinding(UniformDescription description) {
      return (Uniform)(switch (description.type()) {
         case UNIFORM_BUFFER -> {
            int binding = iris$uniformBufferBinding(description.name());
            yield binding < 0 ? null : new Ubo(binding);
         }
         case COMBINED_IMAGE_SAMPLER -> {
            int binding = iris$samplerBinding(description.name());
            yield binding < 0 ? null : new Sampler(binding);
         }
         case TEXEL_BUFFER -> {
            int binding = iris$samplerBinding(description.name());
            yield binding < 0
               ? null
               : (Uniform)this.iris$textureBuffers
                  .computeIfAbsent(description.name(), name -> new Utb(binding, Objects.requireNonNull(description.gpuFormat())));
         }
         default -> throw new MatchException(null, null);
      });
   }

   @Unique
   private static int iris$uniformBufferBinding(String name) {
      return switch (name) {
         case "DynamicTransforms", "TerrainUniform" -> 0;
         case "CloudInfo" -> 1;
         case "Projection" -> 2;
         case "Globals" -> 3;
         case "Fog" -> 4;
         case "Lighting" -> 5;
         case "u_Globals" -> 0;
         default -> -1;
      };
   }

   @Unique
   private static int iris$samplerBinding(String name) {
      return switch (name) {
         case "Sampler0", "u_BlockTex" -> 0;
         case "Sampler1" -> 1;
         case "Sampler2", "u_LightTex" -> 2;
         case "CloudFaces", "u_SectionTimeInfo" -> 3;
         default -> -1;
      };
   }
}
