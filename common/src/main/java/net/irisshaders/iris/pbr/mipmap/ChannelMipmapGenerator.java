package net.irisshaders.iris.pbr.mipmap;

import net.caffeinemc.mods.sodium.api.util.ColorABGR;

public class ChannelMipmapGenerator extends AbstractMipmapGenerator {
   protected final ChannelMipmapGenerator.BlendFunction redFunc;
   protected final ChannelMipmapGenerator.BlendFunction greenFunc;
   protected final ChannelMipmapGenerator.BlendFunction blueFunc;
   protected final ChannelMipmapGenerator.BlendFunction alphaFunc;

   public ChannelMipmapGenerator(
      ChannelMipmapGenerator.BlendFunction redFunc,
      ChannelMipmapGenerator.BlendFunction greenFunc,
      ChannelMipmapGenerator.BlendFunction blueFunc,
      ChannelMipmapGenerator.BlendFunction alphaFunc
   ) {
      this.redFunc = redFunc;
      this.greenFunc = greenFunc;
      this.blueFunc = blueFunc;
      this.alphaFunc = alphaFunc;
   }

   @Override
   public int blend(int c0, int c1, int c2, int c3) {
      return this.packABGR(
         this.alphaFunc.blend(ColorABGR.unpackAlpha(c0), ColorABGR.unpackAlpha(c1), ColorABGR.unpackAlpha(c2), ColorABGR.unpackAlpha(c3)),
         this.blueFunc.blend(ColorABGR.unpackBlue(c0), ColorABGR.unpackBlue(c1), ColorABGR.unpackBlue(c2), ColorABGR.unpackBlue(c3)),
         this.greenFunc.blend(ColorABGR.unpackGreen(c0), ColorABGR.unpackGreen(c1), ColorABGR.unpackGreen(c2), ColorABGR.unpackGreen(c3)),
         this.redFunc.blend(ColorABGR.unpackRed(c0), ColorABGR.unpackRed(c1), ColorABGR.unpackRed(c2), ColorABGR.unpackRed(c3))
      );
   }

   private int packABGR(int a, int b, int g, int r) {
      return ColorABGR.pack(r, g, b, a);
   }

   public interface BlendFunction {
      int blend(int var1, int var2, int var3, int var4);
   }
}
