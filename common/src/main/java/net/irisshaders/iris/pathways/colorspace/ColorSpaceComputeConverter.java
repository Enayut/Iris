package net.irisshaders.iris.pathways.colorspace;

import com.google.common.collect.ImmutableSet;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.apache.commons.io.IOUtils;

public class ColorSpaceComputeConverter implements ColorSpaceConverter {
   private int width;
   private int height;
   private ColorSpace colorSpace;
   private ComputeProgram program;
   private GlTexture target;

   public ColorSpaceComputeConverter(int width, int height, ColorSpace colorSpace) {
      this.rebuildProgram(width, height, colorSpace);
   }

   @Override
   public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
      if (this.program != null) {
         this.program.destroy();
         this.program = null;
      }

      this.width = width;
      this.height = height;
      this.colorSpace = colorSpace;

      String source;
      try {
         source = new String(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/colorSpace.csh"))), StandardCharsets.UTF_8);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      List<StringPair> defineList = new ArrayList<>();
      defineList.add(new StringPair("COMPUTE", ""));
      defineList.add(new StringPair("CURRENT_COLOR_SPACE", String.valueOf(colorSpace.ordinal())));

      for (ColorSpace space : ColorSpace.values()) {
         defineList.add(new StringPair(space.name(), String.valueOf(space.ordinal())));
      }

      source = JcppProcessor.glslPreprocessSource(source, defineList);
      ProgramBuilder builder = ProgramBuilder.beginCompute("colorSpaceCompute", source, ImmutableSet.of());
      builder.addTextureImage(() -> this.target.glId(), InternalTextureFormat.RGBA8, "readImage");
      this.program = builder.buildCompute();
   }

   @Override
   public void process(GlTexture targetImage) {
      if (this.colorSpace != ColorSpace.SRGB) {
         this.target = targetImage;
         this.program.use();
         IrisRenderSystem.dispatchCompute(this.width / 8, this.height / 8, 1);
         IrisRenderSystem.memoryBarrier(40);
         ComputeProgram.unbind();
      }
   }
}
