package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableMap.Builder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.error.RusticError;
import net.irisshaders.iris.shaderpack.transform.line.LineTransform;

public class IncludeGraph {
   private final ImmutableMap<AbsolutePackPath, FileNode> nodes;
   private final ImmutableMap<AbsolutePackPath, RusticError> failures;

   private IncludeGraph(ImmutableMap<AbsolutePackPath, FileNode> nodes, ImmutableMap<AbsolutePackPath, RusticError> failures) {
      this.nodes = nodes;
      this.failures = failures;
   }

   public IncludeGraph(Path root, ImmutableList<AbsolutePackPath> startingPaths, boolean isZip) {
      Map<AbsolutePackPath, AbsolutePackPath> cameFrom = new HashMap<>();
      Map<AbsolutePackPath, Integer> lineNumberInclude = new HashMap<>();
      Map<AbsolutePackPath, FileNode> nodes = new HashMap<>();
      Map<AbsolutePackPath, RusticError> failures = new HashMap<>();
      List<AbsolutePackPath> queue = new ArrayList<>(startingPaths);
      Set<AbsolutePackPath> seen = new HashSet<>(startingPaths);

      while (!queue.isEmpty()) {
         AbsolutePackPath next = queue.removeLast();

         String source;
         try {
            Path p = next.resolved(root);
            if (Iris.getIrisConfig().areDebugOptionsEnabled() && !isZip) {
               String absolute = p.toAbsolutePath().toString().replace("\\", "/");
               absolute = absolute.substring(absolute.lastIndexOf("shaders/") + 8);
               String canonical = p.toFile().getCanonicalPath().replace("\\", "/");
               canonical = canonical.substring(canonical.lastIndexOf("shaders/") + 8);
               if (!absolute.equals(canonical)) {
                  throw new FileIncludeException("'" + next.getPathString() + "' doesn't exist, did you mean '" + canonical + "'?");
               }
            }

            source = readFile(p);
         } catch (IOException e) {
            AbsolutePackPath src = cameFrom.get(next);
            if (src == null) {
               throw new RuntimeException("unexpected error: failed to read " + next.getPathString(), e);
            }

            String topLevelMessage;
            String detailMessage;
            if (e instanceof FileIncludeException) {
               topLevelMessage = "failed to resolve #include directive\n" + e.getMessage();
               detailMessage = "file not found";
            } else if (e instanceof NoSuchFileException) {
               topLevelMessage = "failed to resolve #include directive";
               detailMessage = "file not found";
            } else {
               topLevelMessage = "unexpected I/O error while resolving #include directive: " + e;
               detailMessage = "IO error";
            }

            String badLine = ((String)nodes.get(src).getLines().get(lineNumberInclude.get(next))).trim();
            RusticError topLevelError = new RusticError("error", topLevelMessage, detailMessage, src.getPathString(), lineNumberInclude.get(next) + 1, badLine);
            failures.put(next, topLevelError);
            continue;
         }

         ImmutableList<String> lines = ImmutableList.copyOf(source.split("\\R"));
         FileNode node = new FileNode(next, lines);
         boolean selfInclude = false;
         UnmodifiableIterator var27 = node.getIncludes().entrySet().iterator();

         while (true) {
            if (var27.hasNext()) {
               Entry<Integer, AbsolutePackPath> include = (Entry<Integer, AbsolutePackPath>)var27.next();
               int line = include.getKey();
               AbsolutePackPath included = include.getValue();
               if (!next.equals(included)) {
                  if (!seen.contains(included)) {
                     queue.add(included);
                     seen.add(included);
                     cameFrom.put(included, next);
                     lineNumberInclude.put(included, line);
                  }
                  continue;
               }

               selfInclude = true;
               failures.put(
                  next,
                  new RusticError("error", "trivial #include cycle detected", "file includes itself", next.getPathString(), line + 1, (String)lines.get(line))
               );
            }

            if (!selfInclude) {
               nodes.put(next, node);
            }
            break;
         }
      }

      this.nodes = ImmutableMap.copyOf(nodes);
      this.failures = ImmutableMap.copyOf(failures);
      this.detectCycle();
   }

   private static String readFile(Path path) throws IOException {
      return Files.readString(path);
   }

   private void detectCycle() {
      List<AbsolutePackPath> cycle = new ArrayList<>();
      Set<AbsolutePackPath> visited = new HashSet<>();
      UnmodifiableIterator var3 = this.nodes.keySet().iterator();

      while (var3.hasNext()) {
         AbsolutePackPath start = (AbsolutePackPath)var3.next();
         if (this.exploreForCycles(start, cycle, visited)) {
            AbsolutePackPath lastFilePath = null;
            StringBuilder error = new StringBuilder();

            for (AbsolutePackPath node : cycle) {
               if (lastFilePath == null) {
                  lastFilePath = node;
               } else {
                  FileNode lastFile = (FileNode)this.nodes.get(lastFilePath);
                  int lineNumber = -1;
                  UnmodifiableIterator badLine = lastFile.getIncludes().entrySet().iterator();

                  while (badLine.hasNext()) {
                     Entry<Integer, AbsolutePackPath> include = (Entry<Integer, AbsolutePackPath>)badLine.next();
                     if (include.getValue() == node) {
                        lineNumber = include.getKey() + 1;
                     }
                  }

                  String badLinex = (String)lastFile.getLines().get(lineNumber - 1);
                  String detailMessage = node.equals(start) ? "final #include in cycle" : "#include involved in cycle";
                  if (lastFilePath.equals(start)) {
                     error.append(new RusticError("error", "#include cycle detected", detailMessage, lastFilePath.getPathString(), lineNumber, badLinex));
                  } else {
                     error.append("\n  = ")
                        .append(new RusticError("note", "cycle involves another file", detailMessage, lastFilePath.getPathString(), lineNumber, badLinex));
                  }

                  lastFilePath = node;
               }
            }

            error.append(
               "  note: #include directives are resolved before any other preprocessor directives, any form of #include guard will not work\n\n  note: other cycles may still exist, only the first detected non-trivial cycle will be reported\n"
            );
            Iris.logger.error(error.toString());
            throw new IllegalStateException("Cycle detected in #include graph, see previous messages for details");
         }
      }
   }

   private boolean exploreForCycles(AbsolutePackPath frontier, List<AbsolutePackPath> path, Set<AbsolutePackPath> visited) {
      if (visited.contains(frontier)) {
         path.add(frontier);
         return true;
      }

      path.add(frontier);
      visited.add(frontier);
      UnmodifiableIterator var4 = ((FileNode)this.nodes.get(frontier)).getIncludes().values().iterator();

      while (var4.hasNext()) {
         AbsolutePackPath included = (AbsolutePackPath)var4.next();
         if (this.nodes.containsKey(included) && this.exploreForCycles(included, path, visited)) {
            return true;
         }
      }

      path.removeLast();
      visited.remove(frontier);
      return false;
   }

   public ImmutableMap<AbsolutePackPath, FileNode> getNodes() {
      return this.nodes;
   }

   public List<IncludeGraph> computeWeaklyConnectedComponents() {
      return Collections.singletonList(this);
   }

   public IncludeGraph map(Function<AbsolutePackPath, LineTransform> transformProvider) {
      Builder<AbsolutePackPath, FileNode> mappedNodes = ImmutableMap.builder();
      this.nodes.forEach((path, node) -> mappedNodes.put(path, node.map(transformProvider.apply(path))));
      return new IncludeGraph(mappedNodes.build(), this.failures);
   }

   public ImmutableMap<AbsolutePackPath, RusticError> getFailures() {
      return this.failures;
   }
}
