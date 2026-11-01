package net.irisshaders.iris.gl.program;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.image.ImageBinding;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.image.ImageLimits;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;

public class ProgramImages {
   private final ImmutableList<ImageBinding> imageBindings;
   private List<GlUniform1iCall> initializer;

   private ProgramImages(ImmutableList<ImageBinding> imageBindings, List<GlUniform1iCall> initializer) {
      this.imageBindings = imageBindings;
      this.initializer = initializer;
   }

   public static ProgramImages.Builder builder(int program) {
      return new ProgramImages.Builder(program);
   }

   public void update() {
      if (this.initializer != null) {
         for (GlUniform1iCall call : this.initializer) {
            GlStateManager._glUniform1i(call.location(), call.value());
         }

         this.initializer = null;
      }

      UnmodifiableIterator var3 = this.imageBindings.iterator();

      while (var3.hasNext()) {
         ImageBinding imageBinding = (ImageBinding)var3.next();
         imageBinding.update();
      }
   }

   public int getActiveImages() {
      return this.imageBindings.size();
   }

   public static final class Builder implements ImageHolder {
      private final int program;
      private final com.google.common.collect.ImmutableList.Builder<ImageBinding> images;
      private final List<GlUniform1iCall> calls;
      private final int maxImageUnits;
      private int nextImageUnit;

      private Builder(int program) {
         this.program = program;
         this.images = ImmutableList.builder();
         this.calls = new ArrayList<>();
         this.nextImageUnit = 0;
         this.maxImageUnits = ImageLimits.get().getMaxImageUnits();
      }

      @Override
      public boolean hasImage(String name) {
         return GlStateManager._glGetUniformLocation(this.program, name) != -1;
      }

      @Override
      public void addTextureImage(IntSupplier textureID, InternalTextureFormat internalFormat, String name) {
         int location = GlStateManager._glGetUniformLocation(this.program, name);
         if (location != -1) {
            if (this.nextImageUnit >= this.maxImageUnits) {
               if (this.maxImageUnits == 0) {
                  throw new IllegalStateException("Image units are not supported on this platform, but a shader program attempted to reference " + name + ".");
               } else {
                  throw new IllegalStateException(
                     "No more available texture units while activating image " + name + ". Only " + this.maxImageUnits + " image units are available."
                  );
               }
            } else {
               if (internalFormat == InternalTextureFormat.RGBA) {
                  internalFormat = InternalTextureFormat.RGBA8;
               }

               this.images.add(new ImageBinding(this.nextImageUnit, internalFormat.getGlFormat(), textureID));
               this.calls.add(new GlUniform1iCall(location, this.nextImageUnit));
               this.nextImageUnit++;
            }
         }
      }

      public ProgramImages build() {
         return new ProgramImages(this.images.build(), this.calls);
      }
   }
}
