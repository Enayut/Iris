package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Objects;
import net.irisshaders.iris.shaderpack.transform.line.LineTransform;

public class FileNode {
   private final AbsolutePackPath path;
   private final ImmutableList<String> lines;
   private final ImmutableMap<Integer, AbsolutePackPath> includes;

   private FileNode(AbsolutePackPath path, ImmutableList<String> lines, ImmutableMap<Integer, AbsolutePackPath> includes) {
      this.path = path;
      this.lines = lines;
      this.includes = includes;
   }

   public FileNode(AbsolutePackPath path, ImmutableList<String> lines) {
      this.path = path;
      this.lines = lines;
      AbsolutePackPath currentDirectory = path.parent().orElseThrow(() -> new IllegalArgumentException("Not a valid shader file name: " + path));
      this.includes = findIncludes(currentDirectory, lines);
   }

   private static ImmutableMap<Integer, AbsolutePackPath> findIncludes(AbsolutePackPath currentDirectory, ImmutableList<String> lines) {
      Builder<Integer, AbsolutePackPath> foundIncludes = ImmutableMap.builder();

      for (int i = 0; i < lines.size(); i++) {
         String line = ((String)lines.get(i)).trim();
         if (line.startsWith("#include")) {
            String target = line.substring("#include ".length()).trim();
            if (target.startsWith("\"")) {
               target = target.substring(1);
            }

            if (target.endsWith("\"")) {
               target = target.substring(0, target.length() - 1);
            }

            foundIncludes.put(i, currentDirectory.resolve(target));
         }
      }

      return foundIncludes.build();
   }

   public AbsolutePackPath getPath() {
      return this.path;
   }

   public ImmutableList<String> getLines() {
      return this.lines;
   }

   public ImmutableMap<Integer, AbsolutePackPath> getIncludes() {
      return this.includes;
   }

   public FileNode map(LineTransform transform) {
      com.google.common.collect.ImmutableList.Builder<String> newLines = ImmutableList.builder();
      int index = 0;

      for (UnmodifiableIterator var4 = this.lines.iterator(); var4.hasNext(); index++) {
         String line = (String)var4.next();
         String transformedLine = transform.transform(index, line);
         if (this.includes.containsKey(index) && !Objects.equals(line, transformedLine)) {
            throw new IllegalStateException("Attempted to modify an #include line in LineTransform.");
         }

         newLines.add(transformedLine);
      }

      return new FileNode(this.path, newLines.build(), this.includes);
   }
}
