package net.irisshaders.iris.pipeline.transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import org.apache.commons.io.FilenameUtils;

public class ShaderPrinter {
   private static final Path debugOutDir = IrisPlatformHelpers.getInstance().getGameDir().resolve("patched_shaders");
   private static boolean outputLocationCleared = false;
   private static int programCounter = 0;

   public static void resetPrintState() {
      outputLocationCleared = false;
      programCounter = 0;
   }

   public static void deleteIfClearing() {
      if (!outputLocationCleared) {
         try {
            if (Files.exists(debugOutDir)) {
               try (Stream<Path> stream = Files.list(debugOutDir)) {
                  stream.forEach(path -> {
                     try {
                        Files.delete(path);
                     } catch (IOException e) {
                        throw new RuntimeException(e);
                     }
                  });
               }
            }

            Files.createDirectories(debugOutDir);
         } catch (IOException e) {
            Iris.logger.warn("Failed to initialize debug patched shader source location", e);
         }

         outputLocationCleared = true;
      }
   }

   public static ShaderPrinter.ProgramPrintBuilder printProgram(String name) {
      return new ShaderPrinter.ProgramPrintBuilder(name);
   }

   public static class ProgramPrintBuilder {
      private final boolean isActive = Iris.getIrisConfig().areDebugOptionsEnabled();
      private final String prefix = this.isActive ? String.format("%03d_", ++ShaderPrinter.programCounter) : null;
      private final List<String> sources = this.isActive ? new ArrayList<>(PatchShaderType.values().length * 2) : null;
      private String name;
      private boolean done = false;

      public ProgramPrintBuilder(String name) {
         this.setName(name);
      }

      public ShaderPrinter.ProgramPrintBuilder setName(String name) {
         this.name = name;
         return this;
      }

      private void addItem(String extension, String content) {
         if (content != null && this.sources != null) {
            this.sources.add(this.prefix + this.name + extension);
            this.sources.add(content);
         }
      }

      public ShaderPrinter.ProgramPrintBuilder addSource(PatchShaderType type, String source) {
         if (this.sources == null) {
            return this;
         }

         this.addItem(type.extension, source);
         return this;
      }

      public ShaderPrinter.ProgramPrintBuilder addSources(Map<PatchShaderType, String> sources) {
         if (sources == null) {
            return this;
         }

         for (Entry<PatchShaderType, String> entry : sources.entrySet()) {
            this.addSource(entry.getKey(), entry.getValue());
         }

         return this;
      }

      public ShaderPrinter.ProgramPrintBuilder addJson(String json) {
         if (this.sources == null) {
            return this;
         }

         this.addItem(".json", json);
         return this;
      }

      public void print() {
         if (!this.done) {
            this.done = true;
            if (this.isActive) {
               if (!ShaderPrinter.outputLocationCleared) {
                  try {
                     if (Files.exists(ShaderPrinter.debugOutDir)) {
                        try (Stream<Path> stream = Files.list(ShaderPrinter.debugOutDir)
                              .filter(s -> !FilenameUtils.getExtension(s.toString()).contains("properties"))) {
                           stream.forEach(path -> {
                              try {
                                 Files.delete(path);
                              } catch (IOException e) {
                                 throw new RuntimeException(e);
                              }
                           });
                        }
                     }

                     Files.createDirectories(ShaderPrinter.debugOutDir);
                  } catch (IOException e) {
                     Iris.logger.warn("Failed to initialize debug patched shader source location", e);
                  }

                  ShaderPrinter.outputLocationCleared = true;
               }

               try {
                  for (int i = 0; i < this.sources.size(); i += 2) {
                     Files.writeString(ShaderPrinter.debugOutDir.resolve(this.sources.get(i)), this.sources.get(i + 1));
                  }
               } catch (IOException e) {
                  Iris.logger.warn("Failed to write debug patched shader source", e);
               }
            }
         }
      }
   }
}
