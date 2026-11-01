package net.irisshaders.iris.pipeline.programs;

import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.function.Consumer;
import java.util.function.Function;
import net.irisshaders.iris.gl.shader.ShaderCompileException;

public class ShaderMap {
   private final GlProgram[] shaders;

   public ShaderMap(ShaderLoadingMap loadingMap, Function<ShaderSupplier, Boolean> deletionFunction, Consumer<GlProgram> programConsumer) {
      ShaderKey[] ids = ShaderKey.values();
      this.shaders = new GlProgram[ids.length];
      loadingMap.forAllShaders((key, shader) -> {
         if (shader != null) {
            if (deletionFunction.apply(shader)) {
               GlStateManager.glDeleteProgram(shader.id().program());
               return;
            }

            this.checkLinkingState(key, shader);
            GlProgram shaderProgram = shader.shader().get();
            this.shaders[key.ordinal()] = shaderProgram;
            programConsumer.accept(shaderProgram);
         }
      });
   }

   private void checkLinkingState(ShaderKey key, ShaderSupplier shader) {
      int i = shader.id().program();
      int j = GlStateManager.glGetProgrami(i, 35714);
      if (j == 0) {
         String string = GlStateManager.glGetProgramInfoLog(i, 32768);
         throw new ShaderCompileException(key.name(), string);
      }
   }

   public GlProgram getShader(ShaderKey id) {
      return this.shaders[id.ordinal()];
   }
}
