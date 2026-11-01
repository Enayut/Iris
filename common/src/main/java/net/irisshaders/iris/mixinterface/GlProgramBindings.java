package net.irisshaders.iris.mixinterface;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout.UniformDescription;
import java.util.List;

public interface GlProgramBindings {
   void iris$setupBindings(List<UniformDescription> var1, int var2);
}
