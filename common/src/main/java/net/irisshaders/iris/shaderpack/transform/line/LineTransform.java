package net.irisshaders.iris.shaderpack.transform.line;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;

public interface LineTransform {
   static ImmutableList<String> apply(ImmutableList<String> lines, LineTransform transform) {
      Builder<String> newLines = ImmutableList.builder();
      int index = 0;

      for (UnmodifiableIterator var4 = lines.iterator(); var4.hasNext(); index++) {
         String line = (String)var4.next();
         newLines.add(transform.transform(index, line));
      }

      return newLines.build();
   }

   String transform(int var1, String var2);
}
