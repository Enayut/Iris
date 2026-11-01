package net.irisshaders.iris.pbr.texture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.texture.SpriteContentsAnimatedTextureAccessor;
import net.irisshaders.iris.mixin.texture.SpriteContentsFrameInfoAccessor;
import net.irisshaders.iris.mixin.texture.SpriteContentsTickerAccessor;
import net.irisshaders.iris.pbr.loader.AtlasPBRLoader;
import net.irisshaders.iris.pbr.util.TextureManipulationUtil;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents.AnimatedTexture;
import net.minecraft.client.renderer.texture.SpriteContents.AnimationState;
import net.minecraft.client.renderer.texture.SpriteContents.FrameInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public class PBRAtlasTexture extends AbstractTexture implements PBRDumpable {
   protected final TextureAtlas atlasTexture;
   protected final PBRType type;
   protected final Identifier location;
   protected final Map<Identifier, AtlasPBRLoader.PBRTextureAtlasSprite> texturesByNameToAdd = new HashMap<>();
   protected Map<Identifier, AtlasPBRLoader.PBRTextureAtlasSprite> texturesByName = new HashMap<>();
   protected int width;
   protected int height;
   private List<AtlasPBRLoader.PBRTextureAtlasSprite> sprites = List.of();
   private List<AnimationState> animatedTexturesStates = List.of();
   private GpuBuffer spriteUbos;
   private int mipLevelCount;
   private GpuTextureView[] mipViews = new GpuTextureView[0];
   private int maxMipLevel;
   private TextureAtlasSprite missingSprite;

   public PBRAtlasTexture(TextureAtlas atlasTexture, PBRType type) {
      this.atlasTexture = atlasTexture;
      this.type = type;
      this.location = Identifier.fromNamespaceAndPath(
         atlasTexture.location().getNamespace(), atlasTexture.location().getPath().replace(".png", "") + type.getSuffix() + ".png"
      );
   }

   public static void syncAnimation(AnimatedTexture source, AnimationState target) {
      SpriteContentsTickerAccessor sourceAccessor = (SpriteContentsTickerAccessor)source;
      List<FrameInfo> sourceFrames = ((SpriteContentsAnimatedTextureAccessor)sourceAccessor.getAnimationInfo()).getFrames();
      int ticks = 0;

      for (int f = 0; f < sourceAccessor.getFrame(); f++) {
         ticks += ((SpriteContentsFrameInfoAccessor)(Object)sourceFrames.get(f)).getTime();
      }

      SpriteContentsTickerAccessor targetAccessor = (SpriteContentsTickerAccessor)target;
      List<FrameInfo> targetFrames = ((SpriteContentsAnimatedTextureAccessor)targetAccessor.getAnimationInfo()).getFrames();
      int cycleTime = 0;
      int frameCount = targetFrames.size();

      for (FrameInfo frame : targetFrames) {
         cycleTime += ((SpriteContentsFrameInfoAccessor)(Object)frame).getTime();
      }

      ticks %= cycleTime;
      int targetFrame = 0;

      while (true) {
         int time = ((SpriteContentsFrameInfoAccessor)(Object)targetFrames.get(targetFrame)).getTime();
         if (ticks < time) {
            targetAccessor.setFrame(targetFrame);
            targetAccessor.setSubFrame(ticks + sourceAccessor.getSubFrame());
            return;
         }

         targetFrame++;
         ticks -= time;
      }
   }

   protected static void dumpSpriteNames(Path dir, String fileName, Map<Identifier, AtlasPBRLoader.PBRTextureAtlasSprite> sprites) {
      Path path = dir.resolve(fileName + ".txt");

      try (BufferedWriter writer = Files.newBufferedWriter(path)) {
         for (Entry<Identifier, AtlasPBRLoader.PBRTextureAtlasSprite> entry : sprites.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
            AtlasPBRLoader.PBRTextureAtlasSprite sprite = entry.getValue();
            writer.write(
               String.format(
                  Locale.ROOT,
                  "%s\tx=%d\ty=%d\tw=%d\th=%d%n",
                  entry.getKey(),
                  sprite.getX(),
                  sprite.getY(),
                  sprite.contents().width(),
                  sprite.contents().height()
               )
            );
         }
      } catch (IOException e) {
         Iris.logger.warn("Failed to write file {}", path, e);
      }
   }

   public PBRType getType() {
      return this.type;
   }

   public Identifier getAtlasId() {
      return this.location;
   }

   public void addSprite(AtlasPBRLoader.PBRTextureAtlasSprite sprite) {
      this.texturesByNameToAdd.put(sprite.contents().name(), sprite);
   }

   @Nullable
   public AtlasPBRLoader.PBRTextureAtlasSprite getSprite(Identifier id) {
      return this.texturesByName.get(id);
   }

   public boolean tryUpload(int atlasWidth, int atlasHeight, int mipLevel) {
      try {
         this.upload(atlasWidth, atlasHeight, mipLevel);
         return true;
      } catch (Throwable t) {
         if (IrisPlatformHelpers.getInstance().isDevelopmentEnvironment()) {
            t.printStackTrace();
         }

         return false;
      }
   }

   private void createTexture(int i, int j, int k) {
      Iris.logger.info("Created: {}x{}x{} {}-atlas", i, j, k, this.location);
      GpuDevice gpuDevice = RenderSystem.getDevice();
      this.releaseTextures();
      this.texture = gpuDevice.createTexture(this.location::toString, 15, GpuFormat.RGBA8_UNORM, i, j, 1, k + 1);
      this.textureView = gpuDevice.createTextureView(this.texture);
      this.width = i;
      this.height = j;
      this.maxMipLevel = k;
      this.mipLevelCount = k + 1;
      this.mipViews = new GpuTextureView[this.mipLevelCount];
      TextureManipulationUtil.fillWithColor(((GlTexture)this.texture).glId(), this.maxMipLevel, this.type.getDefaultValue());

      for (int l = 0; l <= this.maxMipLevel; l++) {
         this.mipViews[l] = gpuDevice.createTextureView(this.texture, l, 1);
      }
   }

   public void clearTextureData() {
      this.sprites.forEach(TextureAtlasSprite::close);
      this.sprites = List.of();
      this.animatedTexturesStates = List.of();
      this.texturesByName = Map.of();
      this.missingSprite = null;
   }

   public void upload(int atlasWidth, int atlasHeight, int mipLevel) {
      this.createTexture(atlasWidth, atlasHeight, mipLevel);
      this.clearTextureData();
      this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
      this.texturesByName = Map.copyOf(this.texturesByNameToAdd);
      this.missingSprite = null;
      Builder<AtlasPBRLoader.PBRTextureAtlasSprite> spritesBuilder = ImmutableList.builder();
      int animatedSpriteCount = 0;

      for (AtlasPBRLoader.PBRTextureAtlasSprite sprite : this.texturesByName.values()) {
         spritesBuilder.add(sprite);
         if (sprite.isAnimated()) {
            animatedSpriteCount++;
         }
      }

      this.sprites = spritesBuilder.build();
      if (animatedSpriteCount > 0) {
         Builder<AnimationState> animationStates = ImmutableList.builder();
         int spriteUboSize = Mth.roundToward(SpriteContents.UBO_SIZE, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment());
         int uboBlockSize = spriteUboSize * this.mipLevelCount;
         ByteBuffer spriteUboBuffer = MemoryUtil.memAlloc(animatedSpriteCount * uboBlockSize);
         int animationIndex = 0;

         for (TextureAtlasSprite spritex : this.sprites) {
            if (spritex.isAnimated()) {
               spritex.uploadSpriteUbo(spriteUboBuffer, animationIndex * uboBlockSize, this.maxMipLevel, this.width, this.height, spriteUboSize);
               animationIndex++;
            }
         }

         GpuBuffer spriteUbos = RenderSystem.getDevice().createBuffer(() -> this.location + " sprite UBOs PBR", 128, spriteUboBuffer);
         animationIndex = 0;

         for (TextureAtlasSprite spritexx : this.sprites) {
            if (spritexx.isAnimated()) {
               AnimationState animationState = spritexx.createAnimationState(spriteUbos.slice(animationIndex * uboBlockSize, uboBlockSize), spriteUboSize);
               animationIndex++;
               if (animationState != null) {
                  animationStates.add(animationState);
               }
            }
         }

         this.spriteUbos = spriteUbos;
         this.animatedTexturesStates = animationStates.build();
         MemoryUtil.memFree(spriteUboBuffer);
      }

      this.uploadInitialContents();
      if (SharedConstants.DEBUG_DUMP_TEXTURE_ATLAS) {
         Path dumpDir = TextureUtil.getDebugTexturePath();

         try {
            Files.createDirectories(dumpDir);
            this.dumpContents(this.location, dumpDir);
         } catch (IOException var13) {
            Iris.logger.warn("Failed to dump atlas contents to {}", dumpDir);
         }
      }

      PBRAtlasHolder pbrHolder = ((TextureAtlasExtension)this.atlasTexture).getOrCreatePBRHolder();
      switch (this.type) {
         case NORMAL:
            pbrHolder.setNormalAtlas(this);
            break;
         case SPECULAR:
            pbrHolder.setSpecularAtlas(this);
      }
   }

   private void uploadInitialContents() {
      GpuDevice gpuDevice = RenderSystem.getDevice();
      int spriteUboSize = Mth.roundToward(SpriteContents.UBO_SIZE, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment());
      int uboBlockSize = spriteUboSize * this.mipLevelCount;
      GpuSampler gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
      List<AtlasPBRLoader.PBRTextureAtlasSprite> staticSprites = this.sprites.stream().filter(textureAtlasSprite -> !textureAtlasSprite.isAnimated()).toList();
      List<GpuTextureView[]> scratchTextures = new ArrayList<>();
      ByteBuffer byteBuffer = MemoryUtil.memAlloc(staticSprites.size() * uboBlockSize);

      for (int k = 0; k < staticSprites.size(); k++) {
         TextureAtlasSprite textureAtlasSprite = staticSprites.get(k);
         textureAtlasSprite.uploadSpriteUbo(byteBuffer, k * uboBlockSize, this.maxMipLevel, this.width, this.height, spriteUboSize);
         GpuTexture gpuTexture = gpuDevice.createTexture(
            () -> textureAtlasSprite.contents().name().toString(),
            5,
            GpuFormat.RGBA8_UNORM,
            textureAtlasSprite.contents().width(),
            textureAtlasSprite.contents().height(),
            1,
            this.mipLevelCount
         );
         GpuTextureView[] gpuTextureViews = new GpuTextureView[this.mipLevelCount];
         ImmediateState.temporarilyIgnorePass = true;

         for (int l = 0; l <= this.maxMipLevel; l++) {
            textureAtlasSprite.uploadFirstFrame(gpuTexture, l);
            gpuTextureViews[l] = gpuDevice.createTextureView(gpuTexture);
         }

         ImmediateState.temporarilyIgnorePass = false;
         scratchTextures.add(gpuTextureViews);
      }

      GpuBuffer gpuBuffer = gpuDevice.createBuffer(() -> "SpriteAnimationInfo", 128, byteBuffer);

      try {
         for (int level = 0; level < this.mipLevelCount; level++) {
            RenderPass renderPass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> "Animate " + this.location, this.mipViews[level], Optional.empty());

            try {
               renderPass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.ANIMATE_SPRITE_BLIT));
               renderPass.setIndexBuffer(
                  RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6), RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type()
               );
               RenderSystem.bindDefaultUniforms(renderPass);

               for (int n = 0; n < staticSprites.size(); n++) {
                  renderPass.setUniform("Sprite", scratchTextures.get(n)[level], gpuSampler);
                  renderPass.setUniform("SpriteAnimationInfo", gpuBuffer.slice((long)n * uboBlockSize + (long)level * spriteUboSize, SpriteContents.UBO_SIZE));
                  renderPass.draw(6, 1, 0, 0);
               }
            } catch (Throwable var16) {
               if (renderPass != null) {
                  try {
                     renderPass.close();
                  } catch (Throwable var15) {
                     var16.addSuppressed(var15);
                  }
               }

               throw var16;
            }

            if (renderPass != null) {
               renderPass.close();
            }
         }
      } catch (Throwable var17) {
         if (gpuBuffer != null) {
            try {
               gpuBuffer.close();
            } catch (Throwable var14) {
               var17.addSuppressed(var14);
            }
         }

         throw var17;
      }

      if (gpuBuffer != null) {
         gpuBuffer.close();
      }

      for (GpuTextureView[] views : scratchTextures) {
         for (GpuTextureView view : views) {
            view.close();
            view.texture().close();
         }
      }

      MemoryUtil.memFree(byteBuffer);
   }

   public void cycleAnimationFrames() {
      if (this.texture != null) {
         for (AnimationState animationState : this.animatedTexturesStates) {
            animationState.tick();
         }

         if (this.animatedTexturesStates.stream().anyMatch(AnimationState::needsToDraw)) {
            for (int i = 0; i <= this.maxMipLevel; i++) {
               RenderPass renderPass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(() -> "Animate " + this.location, this.mipViews[i], Optional.empty());

               try {
                  RenderSystem.bindDefaultUniforms(renderPass);

                  for (AnimationState animationState2 : this.animatedTexturesStates) {
                     if (animationState2.needsToDraw()) {
                        animationState2.drawToAtlas(renderPass, animationState2.getDrawUbo(i));
                     }
                  }
               } catch (Throwable var6) {
                  if (renderPass != null) {
                     try {
                        renderPass.close();
                     } catch (Throwable var5) {
                        var6.addSuppressed(var5);
                     }
                  }

                  throw var6;
               }

               if (renderPass != null) {
                  renderPass.close();
               }
            }
         }
      }
   }

   public void releaseTextures() {
      PBRAtlasHolder pbrHolder = ((TextureAtlasExtension)this.atlasTexture).getPBRHolder();
      if (pbrHolder != null) {
         switch (this.type) {
            case NORMAL:
               pbrHolder.setNormalAtlas(null);
               break;
            case SPECULAR:
               pbrHolder.setSpecularAtlas(null);
         }
      }

      super.releaseTextures();

      for (GpuTextureView gpuTextureView : this.mipViews) {
         gpuTextureView.close();
      }

      for (AnimationState animationState : this.animatedTexturesStates) {
         animationState.close();
      }

      if (this.spriteUbos != null) {
         this.spriteUbos.close();
         this.spriteUbos = null;
      }
   }

   public void close() {
      this.clearTextureData();
      super.close();
   }

   public void dumpContents(Identifier id, Path path) {
      String string = id.toDebugFileName();
      TextureUtil.writeAsPNG(path, string, this.getTexture(), this.maxMipLevel, i -> i);
      dumpSpriteNames(path, string, this.texturesByName);
   }

   @Override
   public Identifier getDefaultDumpLocation() {
      return this.location;
   }
}
