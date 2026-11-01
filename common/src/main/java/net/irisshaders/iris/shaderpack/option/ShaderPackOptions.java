package net.irisshaders.iris.shaderpack.option;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;

public class ShaderPackOptions {
   private final OptionSet optionSet;
   private final OptionValues optionValues;
   private final IncludeGraph includes;

   public ShaderPackOptions(IncludeGraph graph, Map<String, String> changedConfigs) {
      Map<AbsolutePackPath, OptionAnnotatedSource> allAnnotations = new HashMap<>();
      OptionSet.Builder setBuilder = OptionSet.builder();
      graph.computeWeaklyConnectedComponents().forEach(subgraph -> {
         Builder<AbsolutePackPath, OptionAnnotatedSource> annotationBuilder = ImmutableMap.builder();
         Set<String> referencedBooleanDefines = new HashSet<>();
         subgraph.getNodes().forEach((path, node) -> {
            OptionAnnotatedSource annotatedSource = new OptionAnnotatedSource(node.getLines());
            annotationBuilder.put(path, annotatedSource);
            referencedBooleanDefines.addAll(annotatedSource.getBooleanDefineReferences().keySet());
         });
         ImmutableMap<AbsolutePackPath, OptionAnnotatedSource> annotations = annotationBuilder.build();
         Set<String> referencedBooleanDefinesU = Collections.unmodifiableSet(referencedBooleanDefines);
         annotations.forEach((path, annotatedSource) -> {
            OptionSet set = annotatedSource.getOptionSet(path, referencedBooleanDefinesU);
            setBuilder.addAll(set);
         });
         allAnnotations.putAll(annotations);
      });
      this.optionSet = setBuilder.build();
      this.optionValues = new MutableOptionValues(this.optionSet, changedConfigs);
      this.includes = graph.map(path -> allAnnotations.get(path).asTransform(this.optionValues));
   }

   public OptionSet getOptionSet() {
      return this.optionSet;
   }

   public OptionValues getOptionValues() {
      return this.optionValues;
   }

   public IncludeGraph getIncludes() {
      return this.includes;
   }
}
