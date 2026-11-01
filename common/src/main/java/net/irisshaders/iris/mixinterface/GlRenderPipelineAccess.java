package net.irisshaders.iris.mixinterface;

import com.mojang.renderpearl.backend.api.BackendRenderPipeline.CreateInfo;

public interface GlRenderPipelineAccess {
   CreateInfo getCreateInfo();
}
