package net.irisshaders.iris.gl.shader;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.Locale;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GlShader extends GlResource {
   private static final Logger LOGGER = LogManager.getLogger(GlShader.class);
   private final String name;

   public GlShader(ShaderType type, String name, String src) {
      super(createShader(type, name, src));
      this.name = name;
   }

   private static int createShader(ShaderType type, String name, String src) {
      int handle = GlStateManager.glCreateShader(type.id);
      ShaderWorkarounds.safeShaderSource(handle, src);
      GlStateManager.glCompileShader(handle);
      GLDebug.nameObject(33505, handle, name + "(" + type.name().toLowerCase(Locale.ROOT) + ")");
      String log = IrisRenderSystem.getShaderInfoLog(handle);
      if (!log.isEmpty()) {
         LOGGER.warn("Shader compilation log for " + name + ": " + log);
      }

      int result = GlStateManager.glGetShaderi(handle, 35713);
      if (result != 1) {
         throw new ShaderCompileException(name, log);
      } else {
         return handle;
      }
   }

   public String getName() {
      return this.name;
   }

   public int getHandle() {
      return this.getGlId();
   }

   @Override
   protected void destroyInternal() {
      GlStateManager.glDeleteShader(this.getGlId());
   }
}
