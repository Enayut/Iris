package net.irisshaders.iris.shaderpack.programs;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import org.jetbrains.annotations.Nullable;

public class ProgramFallbackResolver {
   private final ProgramSet programs;
   private final Map<ProgramId, ProgramSource> cache;

   public ProgramFallbackResolver(ProgramSet programs) {
      this.programs = programs;
      this.cache = new HashMap<>();
   }

   public Optional<ProgramSource> resolve(ProgramId id) {
      return Optional.ofNullable(this.resolveNullable(id));
   }

   public boolean has(ProgramId id) {
      return this.programs.get(id).isPresent();
   }

   @Nullable
   public ProgramSource resolveNullable(ProgramId id) {
      if (this.cache.containsKey(id)) {
         return this.cache.get(id);
      }

      ProgramSource source = this.programs.get(id).orElse(null);
      if (source == null) {
         ProgramId fallback = id.getFallback().orElse(null);
         if (fallback != null) {
            source = this.resolveNullable(fallback);
         }
      }

      this.cache.put(id, source);
      return source;
   }
}
