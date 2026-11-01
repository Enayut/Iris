package net.irisshaders.iris.shaderpack.programs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.parsing.ComputeDirectiveParser;
import net.irisshaders.iris.shaderpack.parsing.ConstDirectiveParser;
import net.irisshaders.iris.shaderpack.parsing.DispatchingDirectiveHolder;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

public class ProgramSet implements ProgramSetInterface {
   private final PackDirectives packDirectives;
   private final ComputeSource[] shadowCompute;
   private final ComputeSource[] finalCompute;
   private final ComputeSource[] setup;
   private final ShaderPack pack;
   private final EnumMap<ProgramId, ProgramSource> gbufferPrograms = new EnumMap<>(ProgramId.class);
   private final EnumMap<ProgramArrayId, ProgramSource[]> compositePrograms = new EnumMap<>(ProgramArrayId.class);
   private final EnumMap<ProgramArrayId, ComputeSource[][]> computePrograms = new EnumMap<>(ProgramArrayId.class);

   public ProgramSet(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, ShaderProperties shaderProperties, ShaderPack pack) {
      this.packDirectives = new PackDirectives(PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, shaderProperties);
      this.pack = pack;
      boolean readTesselation = pack.hasFeature(FeatureFlags.TESSELLATION_SHADERS);
      this.shadowCompute = this.readComputeArray(directory, sourceProvider, "shadow", shaderProperties);
      this.setup = this.readProgramArray(directory, sourceProvider, "setup", shaderProperties);

      try (ExecutorService service = Executors.newFixedThreadPool(10)) {
         for (ProgramArrayId id : ProgramArrayId.values()) {
            ProgramSource[] sources = this.readProgramArray(directory, sourceProvider, id.getSourcePrefix(), shaderProperties, readTesselation);
            this.compositePrograms.put(id, sources);
            ComputeSource[][] computes = new ComputeSource[id.getNumPrograms()][];
            boolean hasNoComputes = true;

            for (int i = 0; i < id.getNumPrograms(); i++) {
               computes[i] = this.readComputeArray(directory, sourceProvider, id.getSourcePrefix() + (i == 0 ? "" : i), shaderProperties);
               if (computes[i].length > 0) {
                  hasNoComputes = false;
               }
            }

            this.computePrograms.put(id, hasNoComputes ? new ComputeSource[0][] : computes);
         }

         Future[] sources = new Future[ProgramId.values().length];

         for (ProgramId programId : ProgramId.values()) {
            sources[programId.ordinal()] = service.submit(
               () -> readProgramSource(
                  directory, sourceProvider, programId.getSourceName(), this, shaderProperties, programId.getBlendModeOverride(), readTesselation
               )
            );
         }

         for (ProgramId id : ProgramId.values()) {
            this.gbufferPrograms.put(id, (ProgramSource)sources[id.ordinal()].get());
         }
      } catch (ExecutionException | InterruptedException e) {
         throw new RuntimeException(e);
      }

      this.finalCompute = this.readComputeArray(directory, sourceProvider, "final", shaderProperties);
      this.locateDirectives();
   }

   private static ProgramSource readProgramSource(
      AbsolutePackPath directory,
      Function<AbsolutePackPath, String> sourceProvider,
      String program,
      ProgramSet programSet,
      ShaderProperties properties,
      boolean readTesselation
   ) {
      return readProgramSource(directory, sourceProvider, program, programSet, properties, null, readTesselation);
   }

   private static ProgramSource readProgramSource(
      AbsolutePackPath directory,
      Function<AbsolutePackPath, String> sourceProvider,
      String program,
      ProgramSet programSet,
      ShaderProperties properties,
      BlendModeOverride defaultBlendModeOverride,
      boolean readTesselation
   ) {
      AbsolutePackPath vertexPath = directory.resolve(program + ".vsh");
      String vertexSource = sourceProvider.apply(vertexPath);
      AbsolutePackPath geometryPath = directory.resolve(program + ".gsh");
      String geometrySource = sourceProvider.apply(geometryPath);
      String tessControlSource = null;
      String tessEvalSource = null;
      if (readTesselation) {
         AbsolutePackPath tessControlPath = directory.resolve(program + ".tcs");
         tessControlSource = sourceProvider.apply(tessControlPath);
         AbsolutePackPath tessEvalPath = directory.resolve(program + ".tes");
         tessEvalSource = sourceProvider.apply(tessEvalPath);
      }

      AbsolutePackPath fragmentPath = directory.resolve(program + ".fsh");
      String fragmentSource = sourceProvider.apply(fragmentPath);
      if (vertexSource == null && fragmentSource != null) {
         Iris.logger
            .warn("Found a program (" + program + ") that has a fragment shader but no vertex shader? This is very legacy behavior and might not work right.");
         vertexSource = "#version 120\n\nvarying vec4 irs_texCoords[3];\nvarying vec4 irs_Color;\n\nvoid main() {\n\tgl_Position = ftransform();\n\tirs_texCoords[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;\n\tirs_texCoords[1] = gl_TextureMatrix[1] * gl_MultiTexCoord1;\n\tirs_texCoords[2] = gl_TextureMatrix[1] * gl_MultiTexCoord2;\n\tirs_Color = gl_Color;\n}\n";
      }

      return new ProgramSource(
         program, vertexSource, geometrySource, tessControlSource, tessEvalSource, fragmentSource, programSet, properties, defaultBlendModeOverride
      );
   }

   private static ComputeSource readComputeSource(
      AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String program, ProgramSet programSet, ShaderProperties properties
   ) {
      AbsolutePackPath computePath = directory.resolve(program + ".csh");
      String computeSource = sourceProvider.apply(computePath);
      return computeSource == null ? null : new ComputeSource(program, computeSource, programSet, properties);
   }

   private ProgramSource[] readProgramArray(
      AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String name, ShaderProperties shaderProperties, boolean readTesselation
   ) {
      ProgramSource[] programs = new ProgramSource[100];

      for (int i = 0; i < programs.length; i++) {
         String suffix = i == 0 ? "" : Integer.toString(i);
         programs[i] = readProgramSource(directory, sourceProvider, name + suffix, this, shaderProperties, readTesselation);
      }

      return programs;
   }

   private ComputeSource[] readProgramArray(
      AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String name, ShaderProperties properties
   ) {
      ComputeSource[] programs = new ComputeSource[100];

      for (int i = 0; i < programs.length; i++) {
         String suffix = i == 0 ? "" : Integer.toString(i);
         programs[i] = readComputeSource(directory, sourceProvider, name + suffix, this, properties);
      }

      return programs;
   }

   private ComputeSource[] readComputeArray(
      AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String name, ShaderProperties properties
   ) {
      ComputeSource[] programs = new ComputeSource[27];
      programs[0] = readComputeSource(directory, sourceProvider, name, this, properties);

      for (char c = 'a'; c <= 'z'; c++) {
         String suffix = "_" + c;
         programs[c - '`'] = readComputeSource(directory, sourceProvider, name + suffix, this, properties);
         if (programs[c - '`'] == null) {
            break;
         }
      }

      return Arrays.stream(programs).allMatch(Objects::isNull) ? new ComputeSource[0] : programs;
   }

   private void locateDirectives() {
      List<ProgramSource> programs = new ArrayList<>();
      List<ComputeSource> computes = new ArrayList<>();
      programs.addAll(Arrays.asList(this.getComposite(ProgramArrayId.ShadowComposite)));
      programs.addAll(Arrays.asList(this.getComposite(ProgramArrayId.Begin)));
      programs.addAll(Arrays.asList(this.getComposite(ProgramArrayId.Prepare)));

      for (ComputeSource[][] sources : this.computePrograms.values()) {
         for (ComputeSource[] source : sources) {
            computes.addAll(Arrays.asList(source));
         }
      }

      programs.addAll(this.gbufferPrograms.values());

      for (ComputeSource computeSource : this.setup) {
         if (computeSource != null) {
            computes.add(computeSource);
         }
      }

      programs.addAll(Arrays.asList(this.getComposite(ProgramArrayId.Deferred)));
      programs.addAll(Arrays.asList(this.getComposite(ProgramArrayId.Composite)));
      Collections.addAll(computes, this.finalCompute);
      Collections.addAll(computes, this.shadowCompute);

      for (ComputeSource source : computes) {
         if (source != null) {
            source.getSource().map(ConstDirectiveParser::findDirectives).ifPresent(constDirectives -> {
               for (ConstDirectiveParser.ConstDirective directive : constDirectives) {
                  if (directive.getType() == ConstDirectiveParser.Type.IVEC3 && directive.getKey().equals("workGroups")) {
                     ComputeDirectiveParser.setComputeWorkGroups(source, directive);
                  } else if (directive.getType() == ConstDirectiveParser.Type.VEC2 && directive.getKey().equals("workGroupsRender")) {
                     ComputeDirectiveParser.setComputeWorkGroupsRelative(source, directive);
                  }
               }
            });
         }
      }

      DispatchingDirectiveHolder packDirectiveHolder = new DispatchingDirectiveHolder();
      this.packDirectives.acceptDirectivesFrom(packDirectiveHolder);

      for (ProgramSource source : programs) {
         if (source != null) {
            source.getFragmentSource().map(ConstDirectiveParser::findDirectives).ifPresent(directives -> {
               for (ConstDirectiveParser.ConstDirective directive : directives) {
                  packDirectiveHolder.processDirective(directive);
               }
            });
         }
      }

      this.packDirectives
         .getRenderTargetDirectives()
         .getRenderTargetSettings()
         .forEach((index, settings) -> Iris.logger.debug("Render target settings for colortex" + index + ": " + settings));
   }

   public ComputeSource[] getSetup() {
      return this.setup;
   }

   public Optional<ProgramSource> get(ProgramId programId) {
      ProgramSource source = this.gbufferPrograms.getOrDefault(programId, null);
      return source != null ? source.requireValid() : Optional.empty();
   }

   public ComputeSource[] getShadowCompute() {
      return this.shadowCompute;
   }

   public ComputeSource[] getFinalCompute() {
      return this.finalCompute;
   }

   public PackDirectives getPackDirectives() {
      return this.packDirectives;
   }

   public ShaderPack getPack() {
      return this.pack;
   }

   public ProgramSource[] getComposite(ProgramArrayId programArrayId) {
      return this.compositePrograms.getOrDefault(programArrayId, new ProgramSource[programArrayId.getNumPrograms()]);
   }

   public ComputeSource[][] getCompute(ProgramArrayId programArrayId) {
      return this.computePrograms.getOrDefault(programArrayId, new ComputeSource[0][0]);
   }
}
