package net.irisshaders.iris.gl.shader;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProgramCreator {
   private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

   public static int create(String name, GlShader... shaders) {
      int program = GlStateManager.glCreateProgram();
      GlStateManager._glBindAttribLocation(program, 11, "iris_Entity");
      GlStateManager._glBindAttribLocation(program, 11, "mc_Entity");
      GlStateManager._glBindAttribLocation(program, 12, "mc_midTexCoord");
      GlStateManager._glBindAttribLocation(program, 13, "at_tangent");
      GlStateManager._glBindAttribLocation(program, 14, "at_midBlock");
      GlStateManager._glBindAttribLocation(program, 0, "Position");
      GlStateManager._glBindAttribLocation(program, 1, "UV0");

      for (GlShader shader : shaders) {
         GLDebug.nameObject(33505, shader.getHandle(), shader.getName());
         GlStateManager.glAttachShader(program, shader.getHandle());
      }

      GlStateManager.glLinkProgram(program);
      GLDebug.nameObject(33506, program, name);

      for (GlShader shader : shaders) {
         IrisRenderSystem.detachShader(program, shader.getHandle());
      }

      String log = IrisRenderSystem.getProgramInfoLog(program);
      if (!log.isEmpty()) {
         LOGGER.warn("Program link log for " + name + ": " + log);
      }

      int result = GlStateManager.glGetProgrami(program, 35714);
      if (result != 1) {
         throw new ShaderCompileException(name, log);
      } else {
         return program;
      }
   }
}
