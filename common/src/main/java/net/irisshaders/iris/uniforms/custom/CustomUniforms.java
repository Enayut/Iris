package net.irisshaders.iris.uniforms.custom;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import kroppeb.stareval.element.ExpressionElement;
import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.expression.VariableExpression;
import kroppeb.stareval.function.FunctionContext;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import kroppeb.stareval.parser.Parser;
import kroppeb.stareval.resolver.ExpressionResolver;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.LocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.parsing.IrisFunctions;
import net.irisshaders.iris.parsing.IrisOptions;
import net.irisshaders.iris.parsing.VectorType;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;

public class CustomUniforms implements FunctionContext {
   private final Map<String, CachedUniform> variables = new Object2ObjectLinkedOpenHashMap();
   private final Map<String, Expression> variablesExpressions = new Object2ObjectLinkedOpenHashMap();
   private final CustomUniformFixedInputUniformsHolder inputHolder;
   private final List<CachedUniform> uniformOrder;
   private final Map<Object, Object2IntMap<CachedUniform>> locationMap = new Object2ObjectOpenHashMap();
   private final Map<CachedUniform, List<CachedUniform>> dependsOn;

   private CustomUniforms(CustomUniformFixedInputUniformsHolder inputHolder, Map<String, CustomUniforms.Builder.Variable> variables) {
      this.inputHolder = inputHolder;
      ExpressionResolver resolver = new ExpressionResolver(IrisFunctions.functions, name -> {
         Type type = this.inputHolder.getType(name);
         if (type != null) {
            return type;
         }

         CustomUniforms.Builder.Variable variable = variables.get(name);
         return variable != null ? variable.type : null;
      }, true);

      for (CustomUniforms.Builder.Variable variable : variables.values()) {
         try {
            Expression expression = resolver.resolveExpression(variable.type, variable.expression);
            CachedUniform cachedUniform = CachedUniform.forExpression(variable.name, variable.type, expression, this);
            this.addVariable(expression, cachedUniform);
            if (variable.uniform) {
               List<CachedUniform> uniforms = new ArrayList<>();
               uniforms.add(cachedUniform);
            }
         } catch (Exception e) {
            Iris.logger.warn("Failed to resolve uniform " + variable.name + ", reason: " + e.getMessage() + " ( = " + variable.expression + ")", e);
         }
      }

      this.dependsOn = new Object2ObjectOpenHashMap();
      Map<CachedUniform, List<CachedUniform>> requiredBy = new Object2ObjectOpenHashMap();
      Object2IntMap<CachedUniform> dependsOnCount = new Object2IntOpenHashMap();

      for (CachedUniform input : this.inputHolder.getAll()) {
         requiredBy.put(input, new ObjectArrayList());
      }

      for (CachedUniform input : this.variables.values()) {
         requiredBy.put(input, new ObjectArrayList());
      }

      FunctionReturn functionReturn = new FunctionReturn();
      Set<VariableExpression> requires = new ObjectOpenHashSet();
      Set<CachedUniform> brokenUniforms = new ObjectOpenHashSet();

      for (Entry<String, Expression> entry : this.variablesExpressions.entrySet()) {
         requires.clear();
         entry.getValue().listVariables(requires);
         if (!requires.isEmpty()) {
            CachedUniform uniform = this.variables.get(entry.getKey());
            List<CachedUniform> dependencies = new ArrayList<>();

            for (VariableExpression v : requires) {
               Expression evaluated = v.partialEval(this, functionReturn);
               if (evaluated instanceof CachedUniform) {
                  dependencies.add((CachedUniform)evaluated);
               } else {
                  brokenUniforms.add(uniform);
               }
            }

            if (!dependencies.isEmpty()) {
               this.dependsOn.put(uniform, dependencies);
               dependsOnCount.put(uniform, dependencies.size());

               for (CachedUniform dependency : dependencies) {
                  requiredBy.get(dependency).add(uniform);
               }
            }
         }
      }

      List<CachedUniform> ordered = new ObjectArrayList();
      List<CachedUniform> free = new ObjectArrayList();

      for (CachedUniform entry : requiredBy.keySet()) {
         if (!dependsOnCount.containsKey(entry)) {
            free.add(entry);
         }
      }

      while (!free.isEmpty()) {
         CachedUniform pop = free.removeLast();
         if (!brokenUniforms.contains(pop)) {
            ordered.add(pop);
         } else {
            brokenUniforms.addAll(requiredBy.get(pop));
         }

         for (CachedUniform dependent : requiredBy.get(pop)) {
            int count = dependsOnCount.mergeInt(dependent, -1, Integer::sum);
            assert count >= 0;
            if (count == 0) {
               free.add(dependent);
               dependsOnCount.removeInt(dependent);
            }
         }
      }

      if (!brokenUniforms.isEmpty()) {
         Iris.logger
            .warn(
               "The following uniforms won't work, either because they are broken, or reference a broken uniform: \n"
                  + brokenUniforms.stream().map(CachedUniform::getName).collect(Collectors.joining(", "))
            );
      }

      if (!dependsOnCount.isEmpty()) {
         throw new IllegalStateException(
            "Circular reference detected between: "
               + dependsOnCount.object2IntEntrySet()
                  .stream()
                  .map(entryx -> ((CachedUniform)entryx.getKey()).getName() + " (" + entryx.getIntValue() + ")")
                  .collect(Collectors.joining(", "))
         );
      }

      this.uniformOrder = ordered;
   }

   private void addVariable(Expression expression, CachedUniform uniform) throws Exception {
      String name = uniform.getName();
      if (this.variables.containsKey(name)) {
         throw new Exception("Duplicated variable: " + name);
      }

      if (this.inputHolder.containsKey(name)) {
         throw new Exception("Variable shadows build in uniform: " + name);
      }

      this.variables.put(name, uniform);
      this.variablesExpressions.put(name, expression);
   }

   public void assignTo(LocationalUniformHolder targetHolder) {
      Object2IntMap<CachedUniform> locations = new Object2IntOpenHashMap();

      for (CachedUniform uniform : this.uniformOrder) {
         try {
            OptionalInt location = targetHolder.location(uniform.getName(), Type.convert(uniform.getType()));
            if (location.isPresent()) {
               locations.put(uniform, location.getAsInt());
            }
         } catch (Exception e) {
            throw new RuntimeException(uniform.getName(), e);
         }
      }

      this.locationMap.put(targetHolder, locations);
   }

   public void mapholderToPass(LocationalUniformHolder holder, Object pass) {
      this.locationMap.put(pass, this.locationMap.remove(holder));
   }

   public void update() {
      for (CachedUniform value : this.uniformOrder) {
         value.update();
      }
   }

   public void push(Object pass) {
      Object2IntMap<CachedUniform> uniforms = this.locationMap.get(pass);
      if (uniforms != null) {
         uniforms.forEach(CachedUniform::pushIfChanged);
      }
   }

   public void optimise() {
      Object2IntMap<CachedUniform> dependedByCount = new Object2IntOpenHashMap();

      for (List<CachedUniform> dependencies : this.dependsOn.values()) {
         for (CachedUniform dependency : dependencies) {
            dependedByCount.mergeInt(dependency, 1, Integer::sum);
         }
      }

      for (Object2IntMap<CachedUniform> map : this.locationMap.values()) {
         ObjectIterator var12 = map.keySet().iterator();

         while (var12.hasNext()) {
            CachedUniform cachedUniform = (CachedUniform)var12.next();
            dependedByCount.mergeInt(cachedUniform, 1, Integer::sum);
         }
      }

      Set<CachedUniform> unused = new ObjectOpenHashSet();

      for (int i = this.uniformOrder.size() - 1; i >= 0; i--) {
         CachedUniform uniform = this.uniformOrder.get(i);
         if (!dependedByCount.containsKey(uniform)) {
            unused.add(uniform);
            List<CachedUniform> dependencies = this.dependsOn.get(uniform);
            if (dependencies != null) {
               for (CachedUniform dependency : dependencies) {
                  dependedByCount.computeIntIfPresent(dependency, (key, value) -> value - 1);
               }
            }
         }
      }

      this.uniformOrder.removeAll(unused);
   }

   public boolean hasVariable(String name) {
      return this.inputHolder.containsKey(name) || this.variables.containsKey(name);
   }

   public Expression getVariable(String name) {
      CachedUniform inputUniform = this.inputHolder.getUniform(name);
      if (inputUniform != null) {
         return inputUniform;
      } else {
         CachedUniform customUniform = this.variables.get(name);
         if (customUniform != null) {
            return customUniform;
         } else {
            throw new RuntimeException("Unknown variable: " + name);
         }
      }
   }

   public static class Builder {
      private static final Map<String, Type> types = new com.google.common.collect.ImmutableMap.Builder()
         .put("bool", Type.Boolean)
         .put("float", Type.Float)
         .put("int", Type.Int)
         .put("vec2", VectorType.VEC2)
         .put("vec3", VectorType.VEC3)
         .put("vec4", VectorType.VEC4)
         .build();
      final Map<String, CustomUniforms.Builder.Variable> variables = new Object2ObjectLinkedOpenHashMap();

      public void addVariable(String type, String name, String expression, boolean isUniform) {
         if (this.variables.containsKey(name)) {
            Iris.logger.warn("Ignoring duplicated custom uniform name: " + name);
         } else {
            Type parsedType = types.get(type);
            if (parsedType == null) {
               Iris.logger.warn("Ignoring invalid uniform type: " + type + " of " + name);
            } else {
               try {
                  ExpressionElement ast = Parser.parse(expression, IrisOptions.options);
                  this.variables.put(name, new CustomUniforms.Builder.Variable(parsedType, name, ast, isUniform));
               } catch (Exception e) {
                  Iris.logger.warn("Failed to parse custom variable/uniform " + name + " with expression " + expression, e);
               }
            }
         }
      }

      public CustomUniforms build(CustomUniformFixedInputUniformsHolder inputHolder) {
         return new CustomUniforms(inputHolder, this.variables);
      }

      @SafeVarargs
      public final CustomUniforms build(Consumer<UniformHolder>... uniforms) {
         CustomUniformFixedInputUniformsHolder.Builder inputs = new CustomUniformFixedInputUniformsHolder.Builder();

         for (Consumer<UniformHolder> uniform : uniforms) {
            uniform.accept(inputs);
         }

         return this.build(inputs.build());
      }

      private record Variable(Type type, String name, ExpressionElement expression, boolean uniform) {
      }
   }
}
