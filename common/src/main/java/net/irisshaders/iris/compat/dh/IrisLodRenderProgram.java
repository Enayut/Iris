package net.irisshaders.iris.compat.dh;

import com.google.common.primitives.Ints;
import com.seibel.distanthorizons.api.DhApi.Delayed;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.shader.GlShader;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryStack;

public class IrisLodRenderProgram {
   public final int modelOffsetUniform;
   public final int worldYOffsetUniform;
   public final int mircoOffsetUniform;
   public final int modelViewUniform;
   public final int modelViewInverseUniform;
   public final int projectionUniform;
   public final int projectionInverseUniform;
   public final int dhBlockAtlas;
   public final int normalMatrix3fUniform;
   public final int clipDistanceUniform;
   private final int id = GL43C.glCreateProgram();
   private final ProgramUniforms uniforms;
   private final CustomUniforms customUniforms;
   private final ProgramSamplers samplers;
   private final ProgramImages images;
   private final BlendModeOverride blend;
   private final BufferBlendOverride[] bufferBlendOverrides;

   private IrisLodRenderProgram(
      String name,
      boolean isShadowPass,
      boolean translucent,
      BlendModeOverride override,
      BufferBlendOverride[] bufferBlendOverrides,
      String vertex,
      String tessControl,
      String tessEval,
      String geometry,
      String fragment,
      CustomUniforms customUniforms,
      IrisRenderingPipeline pipeline
   ) {
      GL32.glBindAttribLocation(this.id, 0, "vPosition");
      GL32.glBindAttribLocation(this.id, 1, "iris_color");
      GL32.glBindAttribLocation(this.id, 2, "irisExtra");
      this.bufferBlendOverrides = bufferBlendOverrides;
      GlShader vert = new GlShader(ShaderType.VERTEX, name + ".vsh", vertex);
      GL43C.glAttachShader(this.id, vert.getHandle());
      GlShader tessCont = null;
      if (tessControl != null) {
         tessCont = new GlShader(ShaderType.TESSELATION_CONTROL, name + ".tcs", tessControl);
         GL43C.glAttachShader(this.id, tessCont.getHandle());
      }

      GlShader tessE = null;
      if (tessEval != null) {
         tessE = new GlShader(ShaderType.TESSELATION_EVAL, name + ".tes", tessEval);
         GL43C.glAttachShader(this.id, tessE.getHandle());
      }

      GlShader geom = null;
      if (geometry != null) {
         geom = new GlShader(ShaderType.GEOMETRY, name + ".gsh", geometry);
         GL43C.glAttachShader(this.id, geom.getHandle());
      }

      GlShader frag = new GlShader(ShaderType.FRAGMENT, name + ".fsh", fragment);
      GL43C.glAttachShader(this.id, frag.getHandle());
      GL32.glLinkProgram(this.id);
      int status = GL32.glGetProgrami(this.id, 35714);
      if (status != 1) {
         String message = "Shader link error in Iris DH program! Details: " + GL32.glGetProgramInfoLog(this.id);
         this.free();
         throw new RuntimeException(message);
      }

      GL32.glUseProgram(this.id);
      vert.destroy();
      frag.destroy();
      if (tessCont != null) {
         tessCont.destroy();
      }

      if (tessE != null) {
         tessE.destroy();
      }

      if (geom != null) {
         geom.destroy();
      }

      this.blend = override;
      ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(name, this.id);
      ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(this.id, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
      CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_VERTEX);
      customUniforms.assignTo(uniformBuilder);
      BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniformBuilder);
      ProgramImages.Builder builder = ProgramImages.builder(this.id);
      pipeline.addGbufferOrShadowSamplers(
         samplerBuilder,
         builder,
         isShadowPass ? pipeline::getFlippedBeforeShadow : () -> translucent ? pipeline.getFlippedAfterTranslucent() : pipeline.getFlippedAfterPrepare(),
         isShadowPass,
         false,
         true,
         false
      );
      customUniforms.mapholderToPass(uniformBuilder, this);
      this.uniforms = uniformBuilder.buildUniforms();
      this.customUniforms = customUniforms;
      this.samplers = samplerBuilder.build();
      this.images = builder.build();
      this.modelOffsetUniform = this.tryGetUniformLocation2("modelOffset");
      this.worldYOffsetUniform = this.tryGetUniformLocation2("worldYOffset");
      this.mircoOffsetUniform = this.tryGetUniformLocation2("mircoOffset");
      this.projectionUniform = this.tryGetUniformLocation2("iris_ProjectionMatrix");
      this.projectionInverseUniform = this.tryGetUniformLocation2("iris_ProjectionMatrixInverse");
      this.modelViewUniform = this.tryGetUniformLocation2("iris_ModelViewMatrix");
      this.modelViewInverseUniform = this.tryGetUniformLocation2("iris_ModelViewMatrixInverse");
      this.normalMatrix3fUniform = this.tryGetUniformLocation2("iris_NormalMatrix");
      this.dhBlockAtlas = this.tryGetUniformLocation2("dhBlockAtlas");
      this.clipDistanceUniform = this.tryGetUniformLocation2("clipDistance");
   }

   public static IrisLodRenderProgram createProgram(
      String name, boolean isShadowPass, boolean translucent, ProgramSource source, CustomUniforms uniforms, IrisRenderingPipeline pipeline
   ) {
      Map<PatchShaderType, String> transformed = TransformPatcher.patchDHTerrain(
         name,
         source.getVertexSource().orElseThrow(RuntimeException::new),
         source.getTessControlSource().orElse(null),
         source.getTessEvalSource().orElse(null),
         source.getGeometrySource().orElse(null),
         source.getFragmentSource().orElseThrow(RuntimeException::new),
         pipeline.getTextureMap(),
         pipeline.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW)
      );
      String vertex = transformed.get(PatchShaderType.VERTEX);
      String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
      String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
      String geometry = transformed.get(PatchShaderType.GEOMETRY);
      String fragment = transformed.get(PatchShaderType.FRAGMENT);
      ShaderPrinter.printProgram(name).addSources(transformed).setName("dh_" + name).print();
      List<BufferBlendOverride> bufferOverrides = new ArrayList<>();
      source.getDirectives().getBufferBlendOverrides().forEach(information -> {
         int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
         if (index > -1) {
            bufferOverrides.add(new BufferBlendOverride(index, information.blendMode()));
         }
      });
      return new IrisLodRenderProgram(
         name,
         isShadowPass,
         translucent,
         source.getDirectives().getBlendModeOverride().orElse(null),
         bufferOverrides.toArray(BufferBlendOverride[]::new),
         vertex,
         tessControl,
         tessEval,
         geometry,
         fragment,
         uniforms,
         pipeline
      );
   }

   public int tryGetUniformLocation2(CharSequence name) {
      return GL32.glGetUniformLocation(this.id, name);
   }

   public void setUniform(int index, Matrix4fc matrix) {
      if (index != -1 && matrix != null) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            FloatBuffer buffer = stack.callocFloat(16);
            matrix.get(buffer);
            buffer.rewind();
            GL46C.glUniformMatrix4fv(index, false, buffer);
         } catch (Throwable var7) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (stack != null) {
            stack.close();
         }
      }
   }

   public void setUniform(int index, Matrix3f matrix) {
      if (index != -1) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            FloatBuffer buffer = stack.callocFloat(9);
            matrix.get(buffer);
            buffer.rewind();
            IrisRenderSystem.uniformMatrix3fv(index, false, buffer);
         } catch (Throwable var7) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (stack != null) {
            stack.close();
         }
      }
   }

   public void bind() {
      GL43C.glUseProgram(this.id);
      if (this.blend != null) {
         this.blend.apply();
      }

      for (BufferBlendOverride override : this.bufferBlendOverrides) {
         override.apply();
      }
   }

   public void unbind() {
      GL43C.glUseProgram(0);
      ProgramUniforms.clearActiveUniforms();
      ProgramSamplers.clearActiveSamplers();
      BlendModeOverride.restore();
   }

   public void free() {
      GL43C.glDeleteProgram(this.id);
   }

   public void fillUniformData(Matrix4fc projection, Matrix4fc modelView, int worldYOffset, float partialTicks) {
      GL43C.glUseProgram(this.id);
      this.setUniform(this.modelViewUniform, modelView);
      this.setUniform(this.modelViewInverseUniform, modelView.invert(new Matrix4f()));
      this.setUniform(this.projectionUniform, projection);
      this.setUniform(this.projectionInverseUniform, projection.invert(new Matrix4f()));
      this.setUniform(this.normalMatrix3fUniform, new Matrix4f(modelView).invert().transpose3x3(new Matrix3f()));
      this.setUniform(this.mircoOffsetUniform, 0.01F);
      this.setUniform(this.dhBlockAtlas, 0.0F);
      if (this.worldYOffsetUniform != -1) {
         this.setUniform(this.worldYOffsetUniform, worldYOffset);
      }

      float dhNearClipDistance = Delayed.renderProxy.getNearClipPlaneDistanceInBlocks(partialTicks);
      this.setUniform(this.clipDistanceUniform, dhNearClipDistance);
      this.samplers.update();
      this.uniforms.update();
      this.customUniforms.push(this);
      this.images.update();
      DhApiResult<Integer> out = Delayed.renderProxy.getDhBlockRatioAtlasTextureGlId();
      if (out.success) {
         IrisRenderSystem.bindTextureToUnit(3553, 0, (Integer)out.payload);
      } else {
         System.out.println("WHY");
      }
   }

   private void setUniform(int index, float value) {
      GL43C.glUniform1f(index, value);
   }

   public void setModelPos(DhApiVec3f modelPos) {
      this.setUniform(this.modelOffsetUniform, modelPos);
   }

   private void setUniform(int index, DhApiVec3f pos) {
      GL43C.glUniform3f(index, pos.x, pos.y, pos.z);
   }
}
