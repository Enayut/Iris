package net.irisshaders.iris.gl.program;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.DynamicLocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.Uniform;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;

public class ProgramUniforms {
   private static ProgramUniforms active;
   private final ImmutableList<Uniform> perTick;
   private final ImmutableList<Uniform> perFrame;
   private final ImmutableList<Uniform> dynamic;
   private final ImmutableList<ValueUpdateNotifier> notifiersToReset;
   long lastTick = -1L;
   int lastFrame = -1;
   private ImmutableList<Uniform> once;

   public ProgramUniforms(
      ImmutableList<Uniform> once,
      ImmutableList<Uniform> perTick,
      ImmutableList<Uniform> perFrame,
      ImmutableList<Uniform> dynamic,
      ImmutableList<ValueUpdateNotifier> notifiersToReset
   ) {
      this.once = once;
      this.perTick = perTick;
      this.perFrame = perFrame;
      this.dynamic = dynamic;
      this.notifiersToReset = notifiersToReset;
   }

   private static long getCurrentTick() {
      return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
   }

   public static void clearActiveUniforms() {
      if (active != null) {
         active.removeListeners();
      }
   }

   public static ProgramUniforms.Builder builder(String name, int program) {
      return new ProgramUniforms.Builder(name, program);
   }

   private static String getTypeName(int type) {
      String typeName;
      if (type == 5126) {
         typeName = "float";
      } else if (type == 5124) {
         typeName = "int";
      } else if (type == 35676) {
         typeName = "mat4";
      } else if (type == 35666) {
         typeName = "vec4";
      } else if (type == 35675) {
         typeName = "mat3";
      } else if (type == 35665) {
         typeName = "vec3";
      } else if (type == 35674) {
         typeName = "mat2";
      } else if (type == 35664) {
         typeName = "vec2";
      } else if (type == 35667) {
         typeName = "ivec2";
      } else if (type == 35669) {
         typeName = "ivec4";
      } else if (type == 35679) {
         typeName = "sampler3D";
      } else if (type == 35678) {
         typeName = "sampler2D";
      } else if (type == 36306) {
         typeName = "usampler2D";
      } else if (type == 36307) {
         typeName = "usampler3D";
      } else if (type == 35677) {
         typeName = "sampler1D";
      } else if (type == 35682) {
         typeName = "sampler2DShadow";
      } else if (type == 35681) {
         typeName = "sampler1DShadow";
      } else if (type == 36940) {
         typeName = "image1D";
      } else if (type == 36941) {
         typeName = "image2D";
      } else if (type == 36942) {
         typeName = "image3D";
      } else if (type == 36951) {
         typeName = "iimage1D";
      } else if (type == 36952) {
         typeName = "iimage2D";
      } else if (type == 36953) {
         typeName = "iimage3D";
      } else if (type == 36962) {
         typeName = "uimage1D";
      } else if (type == 36963) {
         typeName = "uimage2D";
      } else if (type == 36964) {
         typeName = "uimage3D";
      } else {
         typeName = "(unknown:" + type + ")";
      }

      return typeName;
   }

   private static UniformType getExpectedType(int type) {
      if (type == 5126) {
         return UniformType.FLOAT;
      } else if (type == 5124) {
         return UniformType.INT;
      } else if (type == 35670) {
         return UniformType.INT;
      } else if (type == 35676) {
         return UniformType.MAT4;
      } else if (type == 35666) {
         return UniformType.VEC4;
      } else if (type == 35669) {
         return UniformType.VEC4I;
      } else if (type == 35675) {
         return UniformType.MAT3;
      } else if (type == 35665) {
         return UniformType.VEC3;
      } else if (type == 35668) {
         return UniformType.VEC3I;
      } else if (type == 35674) {
         return null;
      } else if (type == 35664) {
         return UniformType.VEC2;
      } else if (type == 35667) {
         return UniformType.VEC2I;
      } else if (type == 35679) {
         return UniformType.INT;
      } else if (type == 35678) {
         return UniformType.INT;
      } else if (type == 36306) {
         return UniformType.INT;
      } else if (type == 36307) {
         return UniformType.INT;
      } else if (type == 35677) {
         return UniformType.INT;
      } else if (type == 35682) {
         return UniformType.INT;
      } else {
         return type == 35681 ? UniformType.INT : null;
      }
   }

   private static boolean isSampler(int type) {
      return type == 35677 || type == 35678 || type == 36306 || type == 36307 || type == 35679 || type == 35681 || type == 35682;
   }

   private static boolean isImage(int type) {
      return type == 36940
         || type == 36941
         || type == 36962
         || type == 36963
         || type == 36964
         || type == 36951
         || type == 36952
         || type == 36953
         || type == 36942
         || type == 36946
         || type == 36947;
   }

   private void updateStage(ImmutableList<Uniform> uniforms) {
      UnmodifiableIterator var2 = uniforms.iterator();

      while (var2.hasNext()) {
         Uniform uniform = (Uniform)var2.next();
         uniform.update();
      }
   }

   public void update() {
      if (active != null) {
         active.removeListeners();
      }

      active = this;
      this.updateStage(this.dynamic);
      if (this.once != null) {
         this.updateStage(this.once);
         this.updateStage(this.perTick);
         this.updateStage(this.perFrame);
         this.lastTick = getCurrentTick();
         this.once = null;
      } else {
         long currentTick = getCurrentTick();
         if (this.lastTick != currentTick) {
            this.lastTick = currentTick;
            this.updateStage(this.perTick);
         }

         int currentFrame = SystemTimeUniforms.COUNTER.getAsInt();
         if (this.lastFrame != currentFrame) {
            this.lastFrame = currentFrame;
            this.updateStage(this.perFrame);
         }
      }
   }

   public void removeListeners() {
      active = null;
      UnmodifiableIterator var1 = this.notifiersToReset.iterator();

      while (var1.hasNext()) {
         ValueUpdateNotifier notifier = (ValueUpdateNotifier)var1.next();
         notifier.setListener(null);
      }
   }

   public static class Builder implements DynamicLocationalUniformHolder {
      private final String name;
      private final int program;
      private final Map<Integer, String> locations;
      private final Map<String, Uniform> once;
      private final Map<String, Uniform> perTick;
      private final Map<String, Uniform> perFrame;
      private final Map<String, Uniform> dynamic;
      private final Map<String, UniformType> uniformNames;
      private final Map<String, UniformType> externalUniformNames;
      private final List<ValueUpdateNotifier> notifiersToReset;

      protected Builder(String name, int program) {
         this.name = name;
         this.program = program;
         this.locations = new HashMap<>();
         this.once = new HashMap<>();
         this.perTick = new HashMap<>();
         this.perFrame = new HashMap<>();
         this.dynamic = new HashMap<>();
         this.uniformNames = new HashMap<>();
         this.externalUniformNames = new HashMap<>();
         this.notifiersToReset = new ArrayList<>();
      }

      public ProgramUniforms.Builder addUniform(UniformUpdateFrequency updateFrequency, Uniform uniform) {
         Objects.requireNonNull(uniform);
         switch (updateFrequency) {
            case ONCE:
               this.once.put(this.locations.get(uniform.getLocation()), uniform);
               break;
            case PER_TICK:
               this.perTick.put(this.locations.get(uniform.getLocation()), uniform);
               break;
            case PER_FRAME:
               this.perFrame.put(this.locations.get(uniform.getLocation()), uniform);
         }

         return this;
      }

      @Override
      public OptionalInt location(String name, UniformType type) {
         int id = GlStateManager._glGetUniformLocation(this.program, name);
         if (id == -1) {
            return OptionalInt.empty();
         } else if (!this.locations.containsKey(id) && !this.uniformNames.containsKey(name)) {
            this.locations.put(id, name);
            this.uniformNames.put(name, type);
            return OptionalInt.of(id);
         } else {
            Iris.logger.warn("[" + this.name + "] Duplicate uniform: " + type.toString().toLowerCase() + " " + name);
            return OptionalInt.empty();
         }
      }

      public ProgramUniforms buildUniforms() {
         int activeUniforms = GlStateManager.glGetProgrami(this.program, 35718);
         IntBuffer sizeBuf = BufferUtils.createIntBuffer(1);
         IntBuffer typeBuf = BufferUtils.createIntBuffer(1);

         for (int index = 0; index < activeUniforms; index++) {
            String name = IrisRenderSystem.getActiveUniform(this.program, index, 128, sizeBuf, typeBuf);
            if (!name.isEmpty()) {
               int size = sizeBuf.get(0);
               int type = typeBuf.get(0);
               UniformType provided = this.uniformNames.get(name);
               UniformType expected = ProgramUniforms.getExpectedType(type);
               if (provided != null && provided != expected) {
                  String expectedName;
                  if (expected != null) {
                     expectedName = expected.toString();
                  } else {
                     expectedName = "(unsupported type: " + ProgramUniforms.getTypeName(type) + ")";
                  }

                  Iris.logger
                     .error(
                        "["
                           + this.name
                           + "] Wrong uniform type for "
                           + name
                           + ": Iris is providing "
                           + provided
                           + " but the program expects "
                           + expectedName
                           + ". Disabling that uniform."
                     );
                  this.once.remove(name);
                  this.perTick.remove(name);
                  this.perFrame.remove(name);
                  this.dynamic.remove(name);
               }
            }
         }

         return new ProgramUniforms(
            ImmutableList.copyOf(this.once.values()),
            ImmutableList.copyOf(this.perTick.values()),
            ImmutableList.copyOf(this.perFrame.values()),
            ImmutableList.copyOf(this.dynamic.values()),
            ImmutableList.copyOf(this.notifiersToReset)
         );
      }

      public ProgramUniforms.Builder addDynamicUniform(Uniform uniform, ValueUpdateNotifier notifier) {
         Objects.requireNonNull(uniform);
         Objects.requireNonNull(notifier);
         this.dynamic.put(this.locations.get(uniform.getLocation()), uniform);
         this.notifiersToReset.add(notifier);
         return this;
      }

      @Override
      public UniformHolder externallyManagedUniform(String name, UniformType type) {
         this.externalUniformNames.put(name, type);
         return this;
      }
   }
}
