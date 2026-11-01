package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import java.util.OptionalDouble;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.GameRendererAccessor;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class HandRenderer {
   public static final HandRenderer INSTANCE = new HandRenderer();
   public static final float DEPTH = 0.125F;
   private final RenderBuffers bufferSource = new RenderBuffers(Runtime.getRuntime().availableProcessors());
   private final ProjectionMatrixBuffer cachedProjectionMatrixBuffer = new ProjectionMatrixBuffer("hand (Iris)");
   private boolean ACTIVE;
   private boolean renderingSolid;
   private SubmitNodeStorage submitNodeCollector;
   private FeatureRenderDispatcher featureRenderDispatcher;
   private Projection projection = new Projection();
   private PreparedFrame frame;

   public HandRenderer() {
      this.submitNodeCollector = new SubmitNodeStorage();
      this.featureRenderDispatcher = new FeatureRenderDispatcher(
         this.bufferSource,
         Minecraft.getInstance().getModelManager(),
         Minecraft.getInstance().getAtlasManager(),
         Minecraft.getInstance().font,
         Minecraft.getInstance().gameRenderer.gameRenderState()
      );
   }

   private PoseStack setupGlState(GameRenderer gameRenderer, CameraRenderState camera, Matrix4fc modelMatrix, float tickDelta) {
      PoseStack poseStack = new PoseStack();
      Matrix4f scaleMatrix = new Matrix4f().m22(0.125F).m32(0.4375F);
      this.projection
         .setupPerspective(
            0.05F, camera.depthFar, camera.hudFov, Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight()
         );
      scaleMatrix.mul(this.projection.getMatrix(new Matrix4f()));
      poseStack.pushPose();
      ((GameRendererAccessor)gameRenderer).invokeBobHurt(camera, poseStack);
      if ((Boolean)Minecraft.getInstance().options.bobView().get()) {
         ((GameRendererAccessor)gameRenderer).invokeBobView(camera, poseStack);
      }

      scaleMatrix.mul(poseStack.last().pose());
      RenderSystem.setProjectionMatrix(this.cachedProjectionMatrixBuffer.getBuffer(scaleMatrix), ProjectionType.PERSPECTIVE);
      return new PoseStack();
   }

   private boolean canRender(Camera camera, GameRenderer gameRenderer) {
      return !camera.isDetached()
         && camera.entity() instanceof Player
         && !camera.isPanoramicMode()
         && !Minecraft.getInstance().gui.hud.isHidden()
         && (!(camera.entity() instanceof LivingEntity) || !((LivingEntity)camera.entity()).isSleeping())
         && Minecraft.getInstance().gameMode.getPlayerMode() != GameType.SPECTATOR;
   }

   public boolean isHandTranslucent(ItemStack itemStack) {
      Item item = itemStack.getItem();
      return item instanceof BlockItem
         ? Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(((BlockItem)item).getBlock().defaultBlockState()).hasMaterialFlag(1)
         : false;
   }

   public void renderSolid(
      Matrix4fc modelMatrix, float tickDelta, Camera camera, CameraRenderState cameraState, GameRenderer gameRenderer, WorldRenderingPipeline pipeline
   ) {
      FirstPersonHandsAndItemsRenderState state = gameRenderer.gameRenderState().levelRenderState.playerRenderState.firstPersonHandsAndItems;
      if (this.canRender(camera, gameRenderer) && this.iris$isAnyHandSolid(state) && Iris.isPackInUseQuick()) {
         RenderSystem.backupProjectionMatrix();
         this.ACTIVE = true;
         PoseStack poseStack = this.setupGlState(gameRenderer, cameraState, modelMatrix, tickDelta);
         pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);
         poseStack.pushPose();
         Profiler.get().push("iris_hand");
         this.renderingSolid = true;
         RenderSystem.getModelViewStack().pushMatrix();
         RenderSystem.getModelViewStack().set(poseStack.last().pose());
         gameRenderer.firstPersonHandsAndItemsRenderer
            .submitHandsWithItems(
               tickDelta, new PoseStack(), this.submitNodeCollector, gameRenderer.gameRenderState().levelRenderState.playerRenderState, state
            );
         PreparedFrame frame = this.featureRenderDispatcher.prepareFrame(this.submitNodeCollector);
         RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
               () -> "Terrain",
               Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(),
               Optional.empty(),
               Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(),
               OptionalDouble.empty()
            );

         try {
            FeatureRenderDispatcher.renderAllFeatures(renderPass, frame);
         } catch (Throwable var14) {
            if (renderPass != null) {
               try {
                  renderPass.close();
               } catch (Throwable var13) {
                  var14.addSuppressed(var13);
               }
            }

            throw var14;
         }

         if (renderPass != null) {
            renderPass.close();
         }

         Profiler.get().pop();
         frame.close();
         RenderSystem.restoreProjectionMatrix();
         poseStack.popPose();
         RenderSystem.getModelViewStack().popMatrix();
         this.renderingSolid = false;
         pipeline.setPhase(WorldRenderingPhase.NONE);
         this.ACTIVE = false;
      }
   }

   private boolean iris$isAnyHandSolid(FirstPersonHandsAndItemsRenderState state) {
      return !this.isHandTranslucent(state.mainHandItem) || !this.isHandTranslucent(state.offHandItem);
   }

   public void renderTranslucent(
      Matrix4fc modelMatrix, float tickDelta, Camera camera, CameraRenderState cameraState, GameRenderer gameRenderer, WorldRenderingPipeline pipeline
   ) {
      FirstPersonHandsAndItemsRenderState state = gameRenderer.gameRenderState().levelRenderState.playerRenderState.firstPersonHandsAndItems;
      if (this.canRender(camera, gameRenderer) && Iris.isPackInUseQuick()) {
         RenderSystem.backupProjectionMatrix();
         this.ACTIVE = true;
         pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);
         PoseStack poseStack = this.setupGlState(gameRenderer, cameraState, modelMatrix, tickDelta);
         poseStack.pushPose();
         Profiler.get().push("iris_hand_translucent");
         RenderSystem.getModelViewStack().pushMatrix();
         RenderSystem.getModelViewStack().set(poseStack.last().pose());
         gameRenderer.firstPersonHandsAndItemsRenderer
            .submitHandsWithItems(
               tickDelta, new PoseStack(), this.submitNodeCollector, gameRenderer.gameRenderState().levelRenderState.playerRenderState, state
            );
         PreparedFrame frame = this.featureRenderDispatcher.prepareFrame(this.submitNodeCollector);
         RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
               () -> "Terrain",
               Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(),
               Optional.empty(),
               Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(),
               OptionalDouble.empty()
            );

         try {
            FeatureRenderDispatcher.renderAllFeatures(renderPass, frame);
         } catch (Throwable var14) {
            if (renderPass != null) {
               try {
                  renderPass.close();
               } catch (Throwable var13) {
                  var14.addSuppressed(var13);
               }
            }

            throw var14;
         }

         if (renderPass != null) {
            renderPass.close();
         }

         frame.close();
         poseStack.popPose();
         Profiler.get().pop();
         this.bufferSource.endFrame();
         RenderSystem.restoreProjectionMatrix();
         RenderSystem.getModelViewStack().popMatrix();
         pipeline.setPhase(WorldRenderingPhase.NONE);
         this.ACTIVE = false;
      } else {
         this.bufferSource.endFrame();
      }
   }

   public boolean isActive() {
      return this.ACTIVE;
   }

   public boolean isRenderingSolid() {
      return this.renderingSolid;
   }

   public void destroy() {
   }

   public void endRender() {
   }
}
