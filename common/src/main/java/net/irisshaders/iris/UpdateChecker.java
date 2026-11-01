package net.irisshaders.iris;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.Nullable;

public class UpdateChecker {
   private final String currentVersion;
   private CompletableFuture<UpdateChecker.UpdateInfo> info;
   private CompletableFuture<UpdateChecker.BetaInfo> betaInfo;
   private boolean shouldShowUpdateMessage;
   private boolean shouldShowBetaUpdateMessage;
   private boolean usedIrisInstaller;

   public UpdateChecker(String currentVersion) {
      this.currentVersion = currentVersion;
      if (Objects.equals(System.getProperty("iris.installer", "false"), "true")) {
         this.usedIrisInstaller = true;
      }
   }

   public void checkForUpdates(IrisConfig irisConfig) {
      if (irisConfig.shouldDisableUpdateMessage()) {
         this.shouldShowUpdateMessage = false;
      } else {
         this.info = CompletableFuture.supplyAsync(
            () -> {
               try {
                  File updateFile = IrisPlatformHelpers.getInstance().getGameDir().resolve("irisUpdateInfo.json").toFile();
                  if (DateUtils.isSameDay(new Date(), new Date(updateFile.lastModified()))) {
                     Iris.logger.warn("[Iris Update Check] Cached update file detected, using that!");

                     UpdateChecker.UpdateInfo updateInfo;
                     try {
                        updateInfo = (UpdateChecker.UpdateInfo)new Gson()
                           .fromJson(FileUtils.readFileToString(updateFile, StandardCharsets.UTF_8), UpdateChecker.UpdateInfo.class);
                     } catch (JsonSyntaxException | NullPointerException e) {
                        Iris.logger.error("[Iris Update Check] Cached file invalid, will delete!", e);
                        Files.delete(updateFile.toPath());
                        return null;
                     }

                     try {
                        if (IrisPlatformHelpers.INSTANCE.compareVersions(this.currentVersion, updateInfo.semanticVersion) < 0) {
                           this.shouldShowUpdateMessage = true;
                           Iris.logger.warn("[Iris Update Check] New update detected, showing update message!");
                           return updateInfo;
                        }

                        return null;
                     } catch (Exception e) {
                        Iris.logger.error("[Iris Update Check] Caught a VersionParsingException while parsing semantic versions!", e);
                     }
                  }

                  try (InputStream in = new URL("https://github.com/IrisShaders/Iris-Update-Index/releases/latest/download/updateIndex.json").openStream()) {
                     String updateIndex;
                     try {
                        updateIndex = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject().get(StandardMacros.getMcVersion()).getAsString();
                     } catch (NullPointerException e) {
                        Iris.logger.warn("[Iris Update Check] This version doesn't have an update index, skipping.");
                        return null;
                     }

                     String json = IOUtils.toString(new URL(updateIndex), StandardCharsets.UTF_8);
                     UpdateChecker.UpdateInfo updateInfo = (UpdateChecker.UpdateInfo)new Gson().fromJson(json, UpdateChecker.UpdateInfo.class);
                     BufferedWriter writer = new BufferedWriter(new FileWriter(updateFile));
                     writer.write(json);
                     writer.close();

                     try {
                        if (IrisPlatformHelpers.INSTANCE.compareVersions(this.currentVersion, updateInfo.semanticVersion) < 0) {
                           this.shouldShowUpdateMessage = true;
                           Iris.logger.info("[Iris Update Check] New update detected, showing update message!");
                           return updateInfo;
                        }

                        return null;
                     } catch (Exception e) {
                        Iris.logger.error("[Iris Update Check] Caught a VersionParsingException while parsing semantic versions!", e);
                     }
                  }
               } catch (FileNotFoundException e) {
                  Iris.logger.warn("[Iris Update Check] Unable to download " + e.getMessage());
               } catch (IOException e) {
                  Iris.logger.warn("[Iris Update Check] Failed to get update info!", e);
               }

               return null;
            }
         );
      }
   }

   private void checkBetaUpdates() {
      this.betaInfo = CompletableFuture.supplyAsync(
         () -> {
            try (InputStream in = URI.create("https://raw.githubusercontent.com/IrisShaders/Iris-Installer-Files/master/betaTag.json").toURL().openStream()) {
               UpdateChecker.BetaInfo updateInfo = (UpdateChecker.BetaInfo)new Gson()
                  .fromJson(JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject(), UpdateChecker.BetaInfo.class);
               if (0 < updateInfo.betaVersion && "".equalsIgnoreCase(updateInfo.betaTag)) {
                  this.shouldShowUpdateMessage = true;
                  Iris.logger.info("[Iris Beta Update Check] New update detected, showing update message!");
                  return updateInfo;
               }

               return null;
            } catch (FileNotFoundException e) {
               Iris.logger.warn("[Iris Beta Update Check] Unable to download " + e.getMessage());
            } catch (IOException e) {
               Iris.logger.warn("[Iris Beta Update Check] Failed to get update info!", e);
            }

            return null;
         }
      );
   }

   @Nullable
   public UpdateChecker.UpdateInfo getUpdateInfo() {
      if (this.info != null && this.info.isDone()) {
         try {
            return this.info.get();
         } catch (InterruptedException | ExecutionException e) {
            Iris.logger.error("Failed to get update info!", e);
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public Optional<UpdateChecker.BetaInfo> getBetaInfo() {
      if (this.betaInfo != null && this.betaInfo.isDone()) {
         try {
            return Optional.ofNullable(this.betaInfo.get());
         } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
         }
      } else {
         return Optional.empty();
      }
   }

   public Optional<Component> getUpdateMessage() {
      if (this.shouldShowUpdateMessage) {
         UpdateChecker.UpdateInfo info = this.getUpdateInfo();
         if (info == null) {
            return Optional.empty();
         } else {
            String languageCode = Minecraft.getInstance().options.languageCode.toLowerCase(Locale.ROOT);
            String originalText = info.updateInfo.containsKey(languageCode) ? info.updateInfo.get(languageCode) : info.updateInfo.get("en_us");
            String[] textParts = originalText.split("\\{link}");
            if (textParts.length > 1) {
               MutableComponent component1 = Component.literal(textParts[0]);
               MutableComponent component2 = Component.literal(textParts[1]);
               MutableComponent link = Component.literal(this.usedIrisInstaller ? "the Iris Installer" : info.modHost)
                  .withStyle(arg -> arg.withClickEvent(new OpenUrl(this.usedIrisInstaller ? info.installer : info.modDownload)).withUnderlined(true));
               return Optional.of(component1.append(link).append(component2));
            } else {
               MutableComponent link = Component.literal(this.usedIrisInstaller ? "the Iris Installer" : info.modHost)
                  .withStyle(arg -> arg.withClickEvent(new OpenUrl(this.usedIrisInstaller ? info.installer : info.modDownload)).withUnderlined(true));
               return Optional.of(Component.literal(textParts[0]).append(link));
            }
         }
      } else {
         return Optional.empty();
      }
   }

   public Optional<URI> getUpdateLink() {
      if (this.shouldShowUpdateMessage) {
         UpdateChecker.UpdateInfo info = this.getUpdateInfo();
         return Optional.of(this.usedIrisInstaller ? info.installer : info.modDownload);
      } else {
         return Optional.empty();
      }
   }

   public static class BetaInfo {
      public String betaTag;
      public int betaVersion;
   }

   public static class UpdateInfo {
      public String semanticVersion;
      public Map<String, String> updateInfo;
      public String modHost;
      public URI modDownload;
      public URI installer;
   }
}
