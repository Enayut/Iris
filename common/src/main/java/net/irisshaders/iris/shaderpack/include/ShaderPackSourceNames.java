package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.loading.ProgramId;

public class ShaderPackSourceNames {
   public static final ImmutableList<String> POTENTIAL_STARTS = findPotentialStarts();

   public static boolean findPresentSources(Builder<AbsolutePackPath> starts, Path packRoot, AbsolutePackPath directory, ImmutableList<String> candidates) throws IOException {
      Path directoryPath = directory.resolved(packRoot);
      if (!Files.exists(directoryPath)) {
         return false;
      }

      boolean anyFound = false;

      Set<String> found;
      try (Stream<Path> stream = Files.list(directoryPath)) {
         found = stream.<String>map(path -> path.getFileName().toString()).collect(Collectors.toSet());
      }

      UnmodifiableIterator var12 = candidates.iterator();

      while (var12.hasNext()) {
         String candidate = (String)var12.next();
         if (found.contains(candidate)) {
            starts.add(directory.resolve(candidate));
            anyFound = true;
         }
      }

      return anyFound;
   }

   private static ImmutableList<String> findPotentialStarts() {
      Builder<String> potentialFileNames = ImmutableList.builder();

      for (ProgramArrayId programArrayId : ProgramArrayId.values()) {
         for (int i = 0; i < programArrayId.getNumPrograms(); i++) {
            String name = programArrayId.getSourcePrefix();
            String suffix = "";
            if (i > 0) {
               suffix = Integer.toString(i);
            }

            addComputeStarts(potentialFileNames, name + suffix);
         }
      }

      for (ProgramId programId : ProgramId.values()) {
         if (programId != ProgramId.Final && programId != ProgramId.Shadow) {
            addStarts(potentialFileNames, programId.getSourceName());
         } else {
            addComputeStarts(potentialFileNames, programId.getSourceName());
         }
      }

      return potentialFileNames.build();
   }

   private static void addStarts(Builder<String> potentialFileNames, String baseName) {
      potentialFileNames.add(baseName + ".vsh");
      potentialFileNames.add(baseName + ".tcs");
      potentialFileNames.add(baseName + ".tes");
      potentialFileNames.add(baseName + ".gsh");
      potentialFileNames.add(baseName + ".fsh");
   }

   private static void addComputeStarts(Builder<String> potentialFileNames, String baseName) {
      addStarts(potentialFileNames, baseName);

      for (int j = 0; j < 27; j++) {
         String suffix2;
         if (j == 0) {
            suffix2 = "";
         } else {
            char letter = (char)(97 + j - 1);
            suffix2 = "_" + letter;
         }

         potentialFileNames.add(baseName + suffix2 + ".csh");
      }
   }
}
