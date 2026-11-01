package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList.Builder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IncludeProcessor {
   private final IncludeGraph graph;
   private final Map<AbsolutePackPath, ImmutableList<String>> cache;

   public IncludeProcessor(IncludeGraph graph) {
      this.graph = graph;
      this.cache = new HashMap<>();
   }

   public ImmutableList<String> getIncludedFile(AbsolutePackPath path) {
      ImmutableList<String> lines = this.cache.get(path);
      if (lines == null) {
         lines = this.process(path);
         this.cache.put(path, lines);
      }

      return lines;
   }

   private ImmutableList<String> process(AbsolutePackPath path) {
      FileNode fileNode = (FileNode)this.graph.getNodes().get(path);
      if (fileNode == null) {
         return null;
      }

      Builder<String> builder = ImmutableList.builder();
      ImmutableList<String> lines = fileNode.getLines();
      ImmutableMap<Integer, AbsolutePackPath> includes = fileNode.getIncludes();

      for (int i = 0; i < lines.size(); i++) {
         AbsolutePackPath include = (AbsolutePackPath)includes.get(i);
         if (include != null) {
            builder.addAll(Objects.requireNonNull(this.getIncludedFile(include)));
         } else {
            builder.add((String)lines.get(i));
         }
      }

      return builder.build();
   }
}
