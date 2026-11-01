package net.irisshaders.iris.pipeline.programs;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout.UniformDescription;
import java.util.List;

public interface IrisProgram {
   void iris$setupState(List<UniformDescription> var1);

   void iris$clearState();

   int iris$getBlockIndex(int var1, CharSequence var2);

   boolean iris$isSetUp();
}
