package net.irisshaders.iris.gui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallbackI;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.sdl.SDL_DialogFileFilter.Buffer;
import org.lwjgl.system.MemoryUtil;

public final class FileDialogUtil {
   private FileDialogUtil() {
   }

   public static CompletableFuture<Optional<Path>> fileSelectDialog(
      FileDialogUtil.DialogType dialog, String title, @Nullable Path origin, @Nullable String filterLabel, String... filters
   ) {
      CompletableFuture<Optional<Path>> future = new CompletableFuture<>();
      Buffer filterBuffer = filters.length == 0 ? null : SDL_DialogFileFilter.calloc(filters.length);
      if (filterBuffer != null) {
         for (int i = 0; i < filters.length; i++) {
            String pattern = toSDLFilter(filters[i]);
            ((SDL_DialogFileFilter)filterBuffer.get(i))
               .name(MemoryUtil.memUTF8(filterLabel != null ? filterLabel : filters[i]))
               .pattern(MemoryUtil.memUTF8(pattern));
         }
      }

      int properties = SDLProperties.SDL_CreateProperties();
      if (properties == 0) {
         freeFilters(filterBuffer);
         future.completeExceptionally(new IllegalStateException("Failed to create SDL dialog properties"));
         return future;
      }

      SDLProperties.SDL_SetStringProperty(properties, "SDL.filedialog.title", title);
      if (origin != null) {
         SDLProperties.SDL_SetStringProperty(properties, "SDL.filedialog.location", origin.toAbsolutePath().toString());
      }

      if (filterBuffer != null) {
         SDLProperties.SDL_SetPointerProperty(properties, "SDL.filedialog.filters", filterBuffer.address());
         SDLProperties.SDL_SetNumberProperty(properties, "SDL.filedialog.nfilters", filterBuffer.remaining());
      }

      SDL_DialogFileCallbackI callback = (userdata, fileList, selectedFilter) -> {
         try {
            if (fileList != 0L) {
               PointerBuffer files = MemoryUtil.memPointerBuffer(fileList, 1);
               long firstFile = files.get(0);
               if (firstFile == 0L) {
                  future.complete(Optional.empty());
               } else {
                  String path = MemoryUtil.memUTF8(firstFile);
                  future.complete(Optional.of(Paths.get(path)));
               }

               return;
            }

            future.completeExceptionally(new IllegalStateException("SDL file dialog failed"));
         } catch (Throwable t) {
            future.completeExceptionally(t);
            return;
         } finally {
            SDLProperties.SDL_DestroyProperties(properties);
            freeFilters(filterBuffer);
         }
      };

      int type = switch (dialog) {
         case SAVE -> 1;
         case OPEN -> 0;
      };
      SDLDialog.SDL_ShowFileDialogWithProperties(type, callback, 0L, properties);
      return future;
   }

   private static String toSDLFilter(String filter) {
      if (filter.equals("*") || filter.equals("*.*")) {
         return "*";
      } else {
         return filter.startsWith("*.") ? filter.substring(2) : filter;
      }
   }

   private static void freeFilters(@Nullable Buffer filters) {
      if (filters != null) {
         for (int i = 0; i < filters.capacity(); i++) {
            SDL_DialogFileFilter filter = (SDL_DialogFileFilter)filters.get(i);
            MemoryUtil.memFree(filter.name());
            MemoryUtil.memFree(filter.pattern());
         }

         filters.free();
      }
   }

   public enum DialogType {
      SAVE,
      OPEN;
   }
}
