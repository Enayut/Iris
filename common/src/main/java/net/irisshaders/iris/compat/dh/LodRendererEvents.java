package net.irisshaders.iris.compat.dh;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.DhApi.Delayed;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericObjectRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderPassEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeTextureClearEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiColorDepthTextureCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent.EventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.MatrixUtils;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.lwjgl.opengl.GL46C;

public class LodRendererEvents {
   private static boolean eventHandlersBound = false;
   private static boolean atTranslucent = false;
   private static int textureWidth;
   private static int textureHeight;

   public static void setupEventHandlers() {
      if (!eventHandlersBound) {
         eventHandlersBound = true;
         Iris.logger.info("Queuing DH event binding...");
         DhApiAfterDhInitEvent beforeCleanupEvent = new DhApiAfterDhInitEvent() {
            public void afterDistantHorizonsInit(DhApiEventParam<Void> event) {
               Iris.logger.info("DH Ready, binding Iris event handlers...");
               Iris.loadShaderpackWhenPossible();
               LodRendererEvents.setupSetDeferredBeforeRenderingEvent();
               LodRendererEvents.setupReconnectDepthTextureEvent();
               LodRendererEvents.setupGenericEvent();
               LodRendererEvents.setupCreateDepthTextureEvent();
               LodRendererEvents.setupTransparentRendererEventCancling();
               LodRendererEvents.setupBeforeBufferClearEvent();
               LodRendererEvents.setupBeforeRenderCleanupEvent();
               LodRendererEvents.beforeBufferRenderEvent();
               LodRendererEvents.setupBeforeRenderFrameBufferBinding();
               LodRendererEvents.setupBeforeRenderPassEvent();
               LodRendererEvents.setupBeforeApplyShaderEvent();
               DHCompatInternal.dhEnabled = (Boolean)Delayed.configs.graphics().renderingEnabled().getValue();
               Iris.logger.info("DH Iris events bound.");
            }
         };
         DhApi.events.bind(DhApiAfterDhInitEvent.class, beforeCleanupEvent);
      }
   }

   private static void setupSetDeferredBeforeRenderingEvent() {
      DhApiBeforeRenderEvent beforeRenderEvent = new DhApiBeforeRenderEvent() {
         public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            Delayed.renderProxy.setDeferTransparentRendering(Iris.isPackInUseQuick() && LodRendererEvents.getInstance().shouldOverride);
            Delayed.configs
               .graphics()
               .fog()
               .drawMode()
               .setValue(LodRendererEvents.getInstance().shouldOverride ? EDhApiFogDrawMode.FOG_DISABLED : EDhApiFogDrawMode.FOG_ENABLED);
         }
      };
      DhApi.events.bind(DhApiBeforeRenderEvent.class, beforeRenderEvent);
   }

   private static void setupReconnectDepthTextureEvent() {
      DhApiBeforeTextureClearEvent beforeRenderEvent = new DhApiBeforeTextureClearEvent() {
         public void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event) {
            DhApiResult<Integer> getResult = Delayed.renderProxy.getDhDepthTextureId();
            if (getResult.success) {
               int depthTextureId = (Integer)getResult.payload;
               LodRendererEvents.getInstance().reconnectDHTextures(depthTextureId);
            }
         }
      };
      DhApi.events.bind(DhApiBeforeTextureClearEvent.class, beforeRenderEvent);
   }

   private static void setupGenericEvent() {
      DhApiBeforeGenericRenderSetupEvent beforeRenderEvent = new DhApiBeforeGenericRenderSetupEvent() {
         public void beforeSetup(DhApiEventParam<DhApiRenderParam> dhApiEventParam) {
            if (LodRendererEvents.getInstance().getGenericFB() != null) {
               LodRendererEvents.getInstance().getGenericFB().bind();
            }
         }
      };
      DhApiBeforeGenericObjectRenderEvent beforeDrawEvent = new DhApiBeforeGenericObjectRenderEvent() {
         public void beforeRender(
            DhApiCancelableEventParam<com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericObjectRenderEvent.EventParam> dhApiCancelableEventParam
         ) {
            if (((com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericObjectRenderEvent.EventParam)dhApiCancelableEventParam.value)
                  .resourceLocationPath
                  .equalsIgnoreCase("Clouds")
               && LodRendererEvents.getInstance().avoidRenderingClouds()) {
               dhApiCancelableEventParam.cancelEvent();
            }
         }
      };
      DhApi.events.bind(DhApiBeforeGenericRenderSetupEvent.class, beforeRenderEvent);
      DhApi.events.bind(DhApiBeforeGenericObjectRenderEvent.class, beforeDrawEvent);
   }

   private static DHCompatInternal getInstance() {
      return Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::getDHCompat).map(DHCompat::getInstance).map(instance -> (DHCompatInternal) instance).orElse(DHCompatInternal.SHADERLESS);
   }

   private static void setupCreateDepthTextureEvent() {
      DhApiColorDepthTextureCreatedEvent beforeRenderEvent = new DhApiColorDepthTextureCreatedEvent() {
         public void onResize(DhApiEventParam<com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiColorDepthTextureCreatedEvent.EventParam> input) {
            LodRendererEvents.textureWidth = ((com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiColorDepthTextureCreatedEvent.EventParam)input.value)
               .newWidth;
            LodRendererEvents.textureHeight = ((com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiColorDepthTextureCreatedEvent.EventParam)input.value)
               .newHeight;
         }
      };
      DhApi.events.bind(DhApiColorDepthTextureCreatedEvent.class, beforeRenderEvent);
   }

   private static void setupTransparentRendererEventCancling() {
      DhApiBeforeRenderEvent beforeRenderEvent = new DhApiBeforeRenderEvent() {
         public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (ShadowRenderingState.areShadowsCurrentlyBeingRendered() && !LodRendererEvents.getInstance().shouldOverrideShadow) {
               event.cancelEvent();
            }
         }
      };
      DhApiBeforeDeferredRenderEvent beforeRenderEvent2 = new DhApiBeforeDeferredRenderEvent() {
         public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (ShadowRenderingState.areShadowsCurrentlyBeingRendered() && !LodRendererEvents.getInstance().shouldOverrideShadow) {
               event.cancelEvent();
            }
         }
      };
      DhApi.events.bind(DhApiBeforeRenderEvent.class, beforeRenderEvent);
      DhApi.events.bind(DhApiBeforeDeferredRenderEvent.class, beforeRenderEvent2);
   }

   private static void setupBeforeRenderCleanupEvent() {
      DhApiBeforeRenderCleanupEvent beforeCleanupEvent = new DhApiBeforeRenderCleanupEvent() {
         public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event) {
            if (LodRendererEvents.getInstance().shouldOverride) {
               if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                  LodRendererEvents.getInstance().getShadowShader().unbind();
               } else {
                  LodRendererEvents.getInstance().getSolidShader().unbind();
               }
            }
         }
      };
      DhApi.events.bind(DhApiBeforeRenderCleanupEvent.class, beforeCleanupEvent);
   }

   private static void setupBeforeBufferClearEvent() {
      DhApiBeforeTextureClearEvent beforeCleanupEvent = new DhApiBeforeTextureClearEvent() {
         public void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (((DhApiRenderParam)event.value).renderPass == EDhApiRenderPass.OPAQUE) {
               if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                  event.cancelEvent();
               } else if (LodRendererEvents.getInstance().shouldOverride) {
                  GlStateManager._clear(256);
                  event.cancelEvent();
               }
            }
         }
      };
      DhApi.events.bind(DhApiBeforeTextureClearEvent.class, beforeCleanupEvent);
   }

   private static void beforeBufferRenderEvent() {
      DhApiBeforeBufferRenderEvent beforeCleanupEvent = new DhApiBeforeBufferRenderEvent() {
         public void beforeRender(DhApiEventParam<EventParam> input) {
            DHCompatInternal instance = LodRendererEvents.getInstance();
            if (instance.shouldOverride) {
               DhApiVec3f modelPos = ((EventParam)input.value).modelPos;
               if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                  instance.getShadowShader().bind();
                  instance.getShadowShader().setModelPos(modelPos);
               } else if (LodRendererEvents.atTranslucent) {
                  instance.getTranslucentShader().bind();
                  instance.getTranslucentShader().setModelPos(modelPos);
               } else {
                  instance.getSolidShader().bind();
                  instance.getSolidShader().setModelPos(modelPos);
               }
            }
         }
      };
      DhApi.events.bind(DhApiBeforeBufferRenderEvent.class, beforeCleanupEvent);
   }

   private static void setupBeforeRenderFrameBufferBinding() {
      DhApiBeforeRenderSetupEvent beforeRenderPassEvent = new DhApiBeforeRenderSetupEvent() {
         public void beforeSetup(DhApiEventParam<DhApiRenderParam> event) {
            DHCompatInternal instance = LodRendererEvents.getInstance();
            OverrideInjector.INSTANCE.unbind(IDhApiShadowCullingFrustum.class, (IDhApiOverrideable)ShadowRenderer.FRUSTUM);
            OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, instance.getShadowFBWrapper());
            OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());
            OverrideInjector.INSTANCE.unbind(IDhApiGenericObjectShaderProgram.class, instance.getGenericShader());
            if (instance.shouldOverride) {
               if (instance.getGenericShader() != null) {
                  OverrideInjector.INSTANCE.bind(IDhApiGenericObjectShaderProgram.class, instance.getGenericShader());
               }

               if (ShadowRenderingState.areShadowsCurrentlyBeingRendered() && instance.shouldOverrideShadow) {
                  OverrideInjector.INSTANCE.bind(IDhApiFramebuffer.class, instance.getShadowFBWrapper());
                  OverrideInjector.INSTANCE.bind(IDhApiShadowCullingFrustum.class, (IDhApiOverrideable)ShadowRenderer.FRUSTUM);
               } else {
                  OverrideInjector.INSTANCE.bind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());
               }
            }
         }
      };
      DhApi.events.bind(DhApiBeforeRenderSetupEvent.class, beforeRenderPassEvent);
   }

   private static void setupBeforeRenderPassEvent() {
      DhApiBeforeRenderPassEvent beforeCleanupEvent = new DhApiBeforeRenderPassEvent() {
         public void beforeRender(DhApiEventParam<DhApiRenderParam> event) {
            DHCompatInternal instance = LodRendererEvents.getInstance();
            if (instance.shouldOverride) {
               Delayed.configs.graphics().ambientOcclusion().enabled().setValue(false);
               Delayed.configs.graphics().fog().drawMode().setValue(EDhApiFogDrawMode.FOG_DISABLED);
               if (((DhApiRenderParam)event.value).renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT) {
                  Iris.logger.error("Unexpected; somehow the Opaque + Translucent pass ran with shaders on.");
               }
            } else {
               Delayed.configs.graphics().ambientOcclusion().enabled().clearValue();
               Delayed.configs.graphics().fog().drawMode().clearValue();
            }

            if (((DhApiRenderParam)event.value).renderPass == EDhApiRenderPass.OPAQUE && instance.shouldOverride) {
               if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                  instance.getShadowShader().bind();
               } else {
                  instance.getSolidShader().bind();
               }

               LodRendererEvents.atTranslucent = false;
            }

            if (((DhApiRenderParam)event.value).renderPass == EDhApiRenderPass.OPAQUE) {
               float partialTicks = ((DhApiRenderParam)event.value).partialTicks;
               if (instance.shouldOverride) {
                  if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                     instance.getShadowShader()
                        .fillUniformData(MatrixUtils.toMinusOneToOne(ShadowRenderer.PROJECTION), ShadowRenderer.MODELVIEW, -1000, partialTicks);
                  } else {
                     instance.getSolidShader()
                        .fillUniformData(
                           DHCompat.getProjection(((DhApiRenderParam)event.value).nearClipPlane, ((DhApiRenderParam)event.value).farClipPlane),
                           CapturedRenderingState.INSTANCE.getGbufferModelView(),
                           -1000,
                           partialTicks
                        );
                  }
               }
            }

            if (((DhApiRenderParam)event.value).renderPass == EDhApiRenderPass.TRANSPARENT) {
               float partialTicks = ((DhApiRenderParam)event.value).partialTicks;
               int depthTextureId = (Integer)Delayed.renderProxy.getDhDepthTextureId().payload;
               if (instance.shouldOverrideShadow && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                  instance.getShadowShader().bind();
                  instance.getShadowFB().bind();
                  LodRendererEvents.atTranslucent = true;
                  return;
               }

               if (instance.shouldOverride && instance.getTranslucentFB() != null) {
                  instance.copyTranslucents(LodRendererEvents.textureWidth, LodRendererEvents.textureHeight);
                  instance.getTranslucentShader().bind();
                  GL46C.glDisable(2884);
                  instance.getTranslucentShader()
                     .fillUniformData(
                        DHCompat.getProjection(((DhApiRenderParam)event.value).nearClipPlane, ((DhApiRenderParam)event.value).farClipPlane),
                        CapturedRenderingState.INSTANCE.getGbufferModelView(),
                        -1000,
                        partialTicks
                     );
                  instance.getTranslucentFB().bind();
               }

               LodRendererEvents.atTranslucent = true;
            }
         }
      };
      DhApi.events.bind(DhApiBeforeRenderPassEvent.class, beforeCleanupEvent);
   }

   private static void setupBeforeApplyShaderEvent() {
      DhApiBeforeApplyShaderRenderEvent beforeApplyShaderEvent = new DhApiBeforeApplyShaderRenderEvent() {
         public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (Iris.isPackInUseQuick()) {
               DHCompatInternal instance = LodRendererEvents.getInstance();
               OverrideInjector.INSTANCE.unbind(IDhApiShadowCullingFrustum.class, (IDhApiOverrideable)ShadowRenderer.FRUSTUM);
               OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, instance.getShadowFBWrapper());
               OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());
               event.cancelEvent();
            }
         }
      };
      DhApi.events.bind(DhApiBeforeApplyShaderRenderEvent.class, beforeApplyShaderEvent);
   }
}
