package net.irisshaders.iris.parsing;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.function.AbstractTypedFunction;
import kroppeb.stareval.function.B2BFunction;
import kroppeb.stareval.function.BB2BFunction;
import kroppeb.stareval.function.F2FFunction;
import kroppeb.stareval.function.F2IFunction;
import kroppeb.stareval.function.FF2BFunction;
import kroppeb.stareval.function.FF2FFunction;
import kroppeb.stareval.function.FFF2BFunction;
import kroppeb.stareval.function.FFF2FFunction;
import kroppeb.stareval.function.FunctionContext;
import kroppeb.stareval.function.FunctionResolver;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.I2IFunction;
import kroppeb.stareval.function.II2BFunction;
import kroppeb.stareval.function.II2IFunction;
import kroppeb.stareval.function.III2BFunction;
import kroppeb.stareval.function.III2IFunction;
import kroppeb.stareval.function.Type;
import kroppeb.stareval.function.V2FFunction;
import kroppeb.stareval.function.V2IFunction;
import kroppeb.stareval.function.TypedFunction;
import kroppeb.stareval.function.FunctionResolver.Builder;
import kroppeb.stareval.function.Type.Primitive;
import kroppeb.stareval.function.TypedFunction.Parameter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

public class IrisFunctions {
   public static final FunctionResolver functions;
   static final Builder builder = new Builder();

   static <T extends TypedFunction> void addVectorized(String name, T function) {
      if (function.getReturnType() instanceof Primitive) {
         add(name, new VectorizedFunction(function, 2));
         add(name, new VectorizedFunction(function, 3));
         add(name, new VectorizedFunction(function, 4));
      } else {
         throw new IllegalArgumentException(name + " is not vectorizable");
      }
   }

   static <T extends TypedFunction> void addVectorizable(String name, T function) {
      add(name, function);
      addVectorized(name, function);
   }

   static <T extends TypedFunction> void addBooleanVectorizable(String name, T function) {
      assert function.getReturnType().equals(Type.Boolean);
      add(name, function);
      if (function.getReturnType() instanceof Primitive) {
         add(name, new BooleanVectorizedFunction(function, 2));
         add(name, new BooleanVectorizedFunction(function, 3));
         add(name, new BooleanVectorizedFunction(function, 4));
      } else {
         throw new IllegalArgumentException(name + " is not vectorizable");
      }
   }

   static <T> void addUnaryOpJOML(String name, final VectorType.JOMLVector<T> type, final BiConsumer<T, T> function) {
      builder.add(name, new AbstractTypedFunction(type, new Type[]{type}) {
         private final T vector = type.create();

         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            T a = (T)functionReturn.objectReturn;
            function.accept(a, this.vector);
            functionReturn.objectReturn = this.vector;
         }
      });
   }

   static <T> void addBinaryOpJOML(String name, final VectorType.JOMLVector<T> type, final IrisFunctions.TriConsumer<T, T, T> function) {
      builder.add(name, new AbstractTypedFunction(type, new Type[]{type, type}) {
         private final T vector = type.create();

         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            T a = (T)functionReturn.objectReturn;
            params[1].evaluateTo(context, functionReturn);
            T b = (T)functionReturn.objectReturn;
            function.accept(a, b, this.vector);
            functionReturn.objectReturn = this.vector;
         }
      });
   }

   static <T> void addTernaryOpJOML(String name, final VectorType.JOMLVector<T> type, final IrisFunctions.QuadConsumer<T, T, T, T> function) {
      builder.add(name, new AbstractTypedFunction(type, new Type[]{type, type, type}) {
         private final T vector = type.create();

         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            T a = (T)functionReturn.objectReturn;
            params[1].evaluateTo(context, functionReturn);
            T b = (T)functionReturn.objectReturn;
            params[2].evaluateTo(context, functionReturn);
            T c = (T)functionReturn.objectReturn;
            function.accept(a, b, c, this.vector);
            functionReturn.objectReturn = this.vector;
         }
      });
   }

   static <T> void addBinaryToBooleanOpJOML(
      String name, VectorType.JOMLVector<T> type, final boolean inverted, final IrisFunctions.ObjectObject2BooleanFunction<T, T> function
   ) {
      builder.add(name, new AbstractTypedFunction(type, new Type[]{type, type}) {
         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            T a = (T)functionReturn.objectReturn;
            params[1].evaluateTo(context, functionReturn);
            T b = (T)functionReturn.objectReturn;
            functionReturn.objectReturn = function.apply(a, b) != inverted;
         }
      });
   }

   static <T extends TypedFunction> void add(String name, T function) {
      builder.add(name, function);
   }

   static void addCast(String name, final Type from, final Type to, final Consumer<FunctionReturn> function) {
      add(name, new TypedFunction() {
         public Type getReturnType() {
            return to;
         }

         public Parameter[] getParameters() {
            return new Parameter[]{new Parameter(from)};
         }

         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            function.accept(functionReturn);
         }
      });
   }

   static void addImplicitCast(Type from, Type to, Consumer<FunctionReturn> function) {
      addCast("<cast>", from, to, function);
      addExplicitCast(from, to, function);
   }

   static void addExplicitCast(Type from, Type to, Consumer<FunctionReturn> function) {
      addCast("to" + to.getClass().getSimpleName(), from, to, function);
   }

   public static void main(String[] args) {
      functions.logAllFunctions();
   }

   static {
      addVectorizable("negate", (I2IFunction)a -> -a);
      add("negate", (F2FFunction)a -> -a);
      addUnaryOpJOML("negate", VectorType.VEC2, Vector2f::negate);
      addUnaryOpJOML("negate", VectorType.VEC3, Vector3f::negate);
      addUnaryOpJOML("negate", VectorType.VEC4, Vector4f::negate);
      addVectorizable("add", (II2IFunction)Integer::sum);
      add("add", (FF2FFunction)Float::sum);
      addBinaryOpJOML("add", VectorType.VEC2, Vector2f::add);
      addBinaryOpJOML("add", VectorType.VEC3, Vector3f::add);
      addBinaryOpJOML("add", VectorType.VEC4, Vector4f::add);
      addVectorizable("subtract", (II2IFunction)(a, b) -> a - b);
      add("subtract", (FF2FFunction)(a, b) -> a - b);
      addBinaryOpJOML("subtract", VectorType.VEC2, Vector2f::sub);
      addBinaryOpJOML("subtract", VectorType.VEC3, Vector3f::sub);
      addBinaryOpJOML("subtract", VectorType.VEC4, Vector4f::sub);
      addVectorizable("multiply", (II2IFunction)(a, b) -> a * b);
      add("multiply", (FF2FFunction)(a, b) -> a * b);
      addBinaryOpJOML("multiply", VectorType.VEC2, Vector2f::mul);
      addBinaryOpJOML("multiply", VectorType.VEC3, Vector3f::mul);
      addBinaryOpJOML("multiply", VectorType.VEC4, Vector4f::mul);
      add("divide", (FF2FFunction)(a, b) -> a / b);
      addBinaryOpJOML("divide", VectorType.VEC2, Vector2f::div);
      addBinaryOpJOML("divide", VectorType.VEC3, Vector3f::div);
      addBinaryOpJOML("divide", VectorType.VEC4, Vector4f::div);
      addVectorizable("remainder", (II2IFunction)(a, b) -> a % b);
      add("remainder", (FF2FFunction)(a, b) -> a % b);
      addBooleanVectorizable("equals", (II2BFunction)(a, b) -> a == b);
      add("equals", (FF2BFunction)(a, b) -> a == b);
      addBinaryToBooleanOpJOML("equal", VectorType.VEC2, false, Vector2f::equals);
      addBinaryToBooleanOpJOML("equal", VectorType.VEC3, false, Vector3f::equals);
      addBinaryToBooleanOpJOML("equal", VectorType.VEC4, false, Vector4f::equals);
      addBooleanVectorizable("notEquals", (II2BFunction)(a, b) -> a != b);
      add("notEquals", (FF2BFunction)(a, b) -> a != b);
      addBinaryToBooleanOpJOML("equal", VectorType.VEC2, true, Vector2f::equals);
      addBinaryToBooleanOpJOML("equal", VectorType.VEC3, true, Vector3f::equals);
      addBinaryToBooleanOpJOML("equal", VectorType.VEC4, true, Vector4f::equals);
      add("lessThanOrEquals", (II2BFunction)(a, b) -> a <= b);
      add("lessThanOrEquals", (FF2BFunction)(a, b) -> a <= b);
      add("moreThanOrEquals", (II2BFunction)(a, b) -> a >= b);
      add("moreThanOrEquals", (FF2BFunction)(a, b) -> a >= b);
      add("lessThan", (II2BFunction)(a, b) -> a < b);
      add("lessThan", (FF2BFunction)(a, b) -> a < b);
      add("moreThan", (II2BFunction)(a, b) -> a > b);
      add("moreThan", (FF2BFunction)(a, b) -> a > b);
      addVectorizable("equals", (BB2BFunction)(a, b) -> a == b);
      addVectorizable("notEquals", (BB2BFunction)(a, b) -> a != b);
      addVectorizable("and", (BB2BFunction)(a, b) -> a && b);
      addVectorizable("or", (BB2BFunction)(a, b) -> a || b);
      addVectorizable("not", (B2BFunction)a -> !a);
      add("torad", (F2FFunction)a -> (float)Math.toRadians(a));
      add("todeg", (F2FFunction)a -> (float)Math.toDegrees(a));
      add("radians", (F2FFunction)a -> (float)Math.toRadians(a));
      add("degrees", (F2FFunction)a -> (float)Math.toDegrees(a));
      add("sin", (F2FFunction)a -> (float)Math.sin(a));
      add("cos", (F2FFunction)a -> (float)Math.cos(a));
      add("tan", (F2FFunction)a -> (float)Math.tan(a));
      add("asin", (F2FFunction)a -> (float)Math.asin(a));
      add("acos", (F2FFunction)a -> (float)Math.acos(a));
      add("atan", (F2FFunction)a -> (float)Math.atan(a));
      add("atan", (FF2FFunction)(y, x) -> (float)Math.atan2(y, x));
      add("atan2", (FF2FFunction)(y, x) -> (float)Math.atan2(y, x));
      add("pow", (FF2FFunction)(a, b) -> (float)Math.pow(a, b));
      add("exp", (F2FFunction)a -> (float)Math.exp(a));
      add("log", (F2FFunction)a -> (float)Math.log(a));
      add("exp2", (F2FFunction)a -> (float)Math.pow(2.0, a));
      add("log2", (F2FFunction)a -> (float)(Math.log(a) / Math.log(2.0)));
      add("sqrt", (F2FFunction)a -> (float)Math.sqrt(a));
      add("log10", (F2FFunction)a -> (float)Math.log10(a));
      add("log", (FF2FFunction)(base, value) -> (float)(Math.log(value) / Math.log(base)));
      add("exp10", (F2FFunction)a -> (float)Math.pow(10.0, a));
      addVectorizable("abs", (I2IFunction)Math::abs);
      add("abs", (F2FFunction)Math::abs);
      addUnaryOpJOML("abs", VectorType.VEC2, Vector2f::absolute);
      addUnaryOpJOML("abs", VectorType.VEC3, Vector3f::absolute);
      addUnaryOpJOML("abs", VectorType.VEC4, Vector4f::absolute);
      add("sign", (F2FFunction)Math::signum);
      add("signum", (F2FFunction)Math::signum);
      add("floor", (F2FFunction)a -> (float)Math.floor(a));
      add("floor", (F2IFunction)a -> (int)Math.floor(a));
      addUnaryOpJOML("floor", VectorType.VEC2, Vector2f::floor);
      addUnaryOpJOML("floor", VectorType.VEC3, Vector3f::floor);
      addUnaryOpJOML("floor", VectorType.VEC4, Vector4f::floor);
      add("ceil", (F2FFunction)a -> (float)Math.ceil(a));
      add("ceil", (F2IFunction)a -> (int)Math.ceil(a));
      addUnaryOpJOML("ceil", VectorType.VEC2, Vector2f::ceil);
      addUnaryOpJOML("ceil", VectorType.VEC3, Vector3f::ceil);
      addUnaryOpJOML("ceil", VectorType.VEC4, Vector4f::ceil);
      add("frac", (F2FFunction)a -> (float)(a - Math.floor(a)));
      addVectorizable("min", (II2IFunction)Math::min);
      add("min", (FF2FFunction)Math::min);
      addBinaryOpJOML("min", VectorType.VEC2, Vector2f::min);
      addBinaryOpJOML("min", VectorType.VEC3, Vector3f::min);
      addBinaryOpJOML("min", VectorType.VEC4, Vector4f::min);
      addVectorizable("max", (II2IFunction)Math::max);
      add("max", (FF2FFunction)Math::max);
      addBinaryOpJOML("max", VectorType.VEC2, Vector2f::max);
      addBinaryOpJOML("max", VectorType.VEC3, Vector3f::max);
      addBinaryOpJOML("max", VectorType.VEC4, Vector4f::max);

      for (int length = 3; length <= 16; length++) {
         Type[] inputs = new Type[length];
         Arrays.fill(inputs, Type.Float);
         add("min", new AbstractTypedFunction(Type.Float, inputs) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               float min = functionReturn.floatReturn;

               for (int i = 1; i < params.length; i++) {
                  params[1].evaluateTo(context, functionReturn);
                  min = Math.min(min, functionReturn.floatReturn);
               }

               functionReturn.floatReturn = min;
            }
         });
         inputs = new Type[length];
         Arrays.fill(inputs, Type.Float);
         add("max", new AbstractTypedFunction(Type.Float, inputs) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               float max = functionReturn.floatReturn;

               for (int i = 1; i < params.length; i++) {
                  params[1].evaluateTo(context, functionReturn);
                  max = Math.max(max, functionReturn.floatReturn);
               }

               functionReturn.floatReturn = max;
            }
         });
         inputs = new Type[length];
         Arrays.fill(inputs, Type.Int);
         addVectorizable("min", new AbstractTypedFunction(Type.Int, inputs) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               int min = functionReturn.intReturn;

               for (int i = 1; i < params.length; i++) {
                  params[1].evaluateTo(context, functionReturn);
                  min = Math.min(min, functionReturn.intReturn);
               }

               functionReturn.intReturn = min;
            }
         });
         inputs = new Type[length];
         Arrays.fill(inputs, Type.Int);
         addVectorizable("max", new AbstractTypedFunction(Type.Int, inputs) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               int max = functionReturn.intReturn;

               for (int i = 1; i < params.length; i++) {
                  params[1].evaluateTo(context, functionReturn);
                  max = Math.max(max, functionReturn.intReturn);
               }

               functionReturn.intReturn = max;
            }
         });
      }

      addVectorizable("clamp", (III2IFunction)(val, min, max) -> Math.max(min, Math.min(max, val)));
      add("clamp", (FFF2FFunction)(val, min, max) -> Math.max(min, Math.min(max, val)));
      addTernaryOpJOML("clamp", VectorType.VEC2, (val, min, max, dest) -> {
         val.min(max, dest);
         dest.max(min);
      });
      addTernaryOpJOML("clamp", VectorType.VEC3, (val, min, max, dest) -> {
         val.min(max, dest);
         dest.max(min);
      });
      addTernaryOpJOML("clamp", VectorType.VEC4, (val, min, max, dest) -> {
         val.min(max, dest);
         dest.max(min);
      });
      add("mix", (FFF2FFunction)(x, y, a) -> x + (y - x) * a);
      addVectorizable("edge", (II2IFunction)(edge, x) -> x < edge ? 0 : 1);
      add("edge", (FF2FFunction)(edge, x) -> x < edge ? 0.0F : 1.0F);
      addVectorizable("fmod", (II2IFunction)Math::floorMod);
      add("fmod", (FF2FFunction)(a, b) -> (a % b + b) % b);
      Random random = new Random();
      addVectorizable("randomInt", (V2IFunction)random::nextInt);
      addVectorizable("randomInt", (I2IFunction)random::nextInt);
      addVectorizable("randomInt", (II2IFunction)(a, b) -> random.nextInt(b - a) + a);
      add("random", (V2FFunction)random::nextFloat);
      add("random", (FF2FFunction)(min, max) -> min + random.nextFloat() * (max - min));

      for (Primitive type : Type.AllPrimitives) {
         add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               params[functionReturn.booleanReturn ? 1 : 2].evaluateTo(context, functionReturn);
            }
         });
      }

      for (Type type : VectorType.AllVectorTypes) {
         add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               params[functionReturn.booleanReturn ? 1 : 2].evaluateTo(context, functionReturn);
            }
         });
      }

      for (int length = 2; length <= 16; length++) {
         for (Primitive type : Type.AllPrimitives) {
            Type[] params = new Type[length * 2 + 1];

            for (int i = 0; i < length * 2; i += 2) {
               params[i] = Type.Boolean;
               params[i + 1] = type;
            }

            params[length * 2] = type;
            final int finalLength = length * 2;
            add("if", new AbstractTypedFunction(type, params) {
               public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                  for (int i = 0; i < finalLength; i += 2) {
                     params[i].evaluateTo(context, functionReturn);
                     if (functionReturn.booleanReturn) {
                        params[i + 1].evaluateTo(context, functionReturn);
                        return;
                     }

                     params[finalLength].evaluateTo(context, functionReturn);
                  }
               }
            });
         }
      }

      builder.addDynamicFunction(
         "smooth", Type.Float, () -> new AbstractTypedFunction(Type.Float, new Parameter[]{new Parameter(Type.Float, false)}, 0, false) {
            private final SmoothFloat smoothFloat = new SmoothFloat();

            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               float target = functionReturn.floatReturn;
               functionReturn.floatReturn = this.smoothFloat.updateAndGet(target, 1.0F, 1.0F);
            }
         }
      );
      builder.addDynamicFunction(
         "smooth",
         Type.Float,
         () -> new AbstractTypedFunction(Type.Float, new Parameter[]{new Parameter(Type.Float, true), new Parameter(Type.Float, false)}, 1, false) {
            private final SmoothFloat smoothFloat = new SmoothFloat();

            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[1].evaluateTo(context, functionReturn);
               float target = functionReturn.floatReturn;
               functionReturn.floatReturn = this.smoothFloat.updateAndGet(target, 1.0F, 1.0F);
            }
         }
      );
      builder.addDynamicFunction(
         "smooth",
         Type.Float,
         () -> new AbstractTypedFunction(Type.Float, new Parameter[]{new Parameter(Type.Float, false), new Parameter(Type.Float, false)}, 0, false) {
            private final SmoothFloat smoothFloat = new SmoothFloat();

            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               float target = functionReturn.floatReturn;
               params[1].evaluateTo(context, functionReturn);
               float fadeTime = functionReturn.floatReturn;
               functionReturn.floatReturn = this.smoothFloat.updateAndGet(target, fadeTime, fadeTime);
            }
         }
      );
      builder.addDynamicFunction(
         "smooth",
         Type.Float,
         () -> new AbstractTypedFunction(
            Type.Float, new Parameter[]{new Parameter(Type.Float, true), new Parameter(Type.Float, false), new Parameter(Type.Float, false)}, 1, false
         ) {
            private final SmoothFloat smoothFloat = new SmoothFloat();

            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[1].evaluateTo(context, functionReturn);
               float target = functionReturn.floatReturn;
               params[2].evaluateTo(context, functionReturn);
               float fadeTime = functionReturn.floatReturn;
               functionReturn.floatReturn = this.smoothFloat.updateAndGet(target, fadeTime, fadeTime);
            }
         }
      );
      builder.addDynamicFunction(
         "smooth",
         Type.Float,
         () -> new AbstractTypedFunction(
            Type.Float, new Parameter[]{new Parameter(Type.Float, false), new Parameter(Type.Float, false), new Parameter(Type.Float, false)}, 0, false
         ) {
            private final SmoothFloat smoothFloat = new SmoothFloat();

            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               float target = functionReturn.floatReturn;
               params[1].evaluateTo(context, functionReturn);
               float fadeUpTime = functionReturn.floatReturn;
               params[2].evaluateTo(context, functionReturn);
               float fadeDownTime = functionReturn.floatReturn;
               functionReturn.floatReturn = this.smoothFloat.updateAndGet(target, fadeUpTime, fadeDownTime);
            }
         }
      );
      builder.addDynamicFunction(
         "smooth",
         Type.Float,
         () -> new AbstractTypedFunction(
            Type.Float,
            new Parameter[]{
               new Parameter(Type.Float, true), new Parameter(Type.Float, false), new Parameter(Type.Float, false), new Parameter(Type.Float, false)
            },
            1,
            false
         ) {
            private final SmoothFloat smoothFloat = new SmoothFloat();

            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[1].evaluateTo(context, functionReturn);
               float target = functionReturn.floatReturn;
               params[2].evaluateTo(context, functionReturn);
               float fadeUpTime = functionReturn.floatReturn;
               params[3].evaluateTo(context, functionReturn);
               float fadeDownTime = functionReturn.floatReturn;
               functionReturn.floatReturn = this.smoothFloat.updateAndGet(target, fadeUpTime, fadeDownTime);
            }
         }
      );
      addImplicitCast(Type.Int, Type.Float, r -> r.floatReturn = r.intReturn);
      addExplicitCast(Type.Float, Type.Int, r -> r.intReturn = (int)r.floatReturn);
      add("between", (III2BFunction)(a, min, max) -> a >= min && a <= max);
      add("between", (FFF2BFunction)(a, min, max) -> a >= min && a <= max);
      add("equals", (FFF2BFunction)(a, b, epsilon) -> Math.abs(a - b) <= epsilon);

      for (int length = 2; length <= 32; length++) {
         Type[] params = new Type[length];
         Arrays.fill(params, Type.Float);
         final int finalLength = length;
         add("in", new AbstractTypedFunction(Type.Boolean, params) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               float value = functionReturn.floatReturn;

               for (int i = 1; i < finalLength; i++) {
                  params[i].evaluateTo(context, functionReturn);
                  if (functionReturn.floatReturn == value) {
                     functionReturn.booleanReturn = true;
                     return;
                  }
               }

               functionReturn.booleanReturn = false;
            }
         });
      }

      for (Primitive type : new Primitive[]{Type.Boolean, Type.Int}) {
         for (int size = 2; size <= 4; size++) {
            TypedFunction function = new VectorConstructor(type, size);
            add(Character.toLowerCase(type.getClass().getSimpleName().charAt(0)) + "vec" + size, function);
         }
      }

      add("vec2", new AbstractTypedFunction(VectorType.VEC2, new Type[]{Type.Float, Type.Float}) {
         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            float x = functionReturn.floatReturn;
            params[1].evaluateTo(context, functionReturn);
            float y = functionReturn.floatReturn;
            functionReturn.objectReturn = new Vector2f(x, y);
         }
      });
      add("vec3", new AbstractTypedFunction(VectorType.VEC3, new Type[]{Type.Float, Type.Float, Type.Float}) {
         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            float x = functionReturn.floatReturn;
            params[1].evaluateTo(context, functionReturn);
            float y = functionReturn.floatReturn;
            params[2].evaluateTo(context, functionReturn);
            float z = functionReturn.floatReturn;
            functionReturn.objectReturn = new Vector3f(x, y, z);
         }
      });
      add("vec4", new AbstractTypedFunction(VectorType.VEC4, new Type[]{Type.Float, Type.Float, Type.Float, Type.Float}) {
         public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
            params[0].evaluateTo(context, functionReturn);
            float x = functionReturn.floatReturn;
            params[1].evaluateTo(context, functionReturn);
            float y = functionReturn.floatReturn;
            params[2].evaluateTo(context, functionReturn);
            float z = functionReturn.floatReturn;
            params[3].evaluateTo(context, functionReturn);
            float w = functionReturn.floatReturn;
            functionReturn.objectReturn = new Vector4f(x, y, z, w);
         }
      });
      String[][] accessNames = new String[][]{{"0", "r", "x", "s"}, {"1", "g", "y", "t"}, {"2", "b", "z", "p"}, {"3", "a", "w", "q"}};

      for (String access : accessNames[0]) {
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC2}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector2f)functionReturn.objectReturn).x;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC2}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector2i)functionReturn.objectReturn).x;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC3}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector3f)functionReturn.objectReturn).x;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC3}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector3i)functionReturn.objectReturn).x;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector4f)functionReturn.objectReturn).x;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector4i)functionReturn.objectReturn).x;
            }
         });
      }

      for (String access : accessNames[1]) {
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC2}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector2f)functionReturn.objectReturn).y;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC2}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector2i)functionReturn.objectReturn).y;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC3}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector3f)functionReturn.objectReturn).y;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC3}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector3i)functionReturn.objectReturn).y;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector4f)functionReturn.objectReturn).y;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector4i)functionReturn.objectReturn).y;
            }
         });
      }

      for (String access : accessNames[2]) {
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC3}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector3f)functionReturn.objectReturn).z;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC3}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector3i)functionReturn.objectReturn).z;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector4f)functionReturn.objectReturn).z;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector4i)functionReturn.objectReturn).z;
            }
         });
      }

      for (String access : accessNames[3]) {
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Float, new Type[]{VectorType.VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.floatReturn = ((Vector4f)functionReturn.objectReturn).w;
            }
         });
         add("<access$" + access + ">", new AbstractTypedFunction(Type.Int, new Type[]{VectorType.I_VEC4}) {
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
               params[0].evaluateTo(context, functionReturn);
               functionReturn.intReturn = ((Vector4i)functionReturn.objectReturn).w;
            }
         });
      }

      for (int i = 0; i < 4; i++) {
         for (String access : accessNames[i]) {
            final int finalI = i;
            add("<access$" + access + ">", new AbstractTypedFunction(VectorType.VEC4, new Type[]{MatrixType.MAT4}) {
               public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                  params[0].evaluateTo(context, functionReturn);
                  functionReturn.objectReturn = ((Matrix4f)functionReturn.objectReturn).getColumn(finalI, new Vector4f());
               }
            });
         }
      }

      functions = builder.build();
   }

   interface ObjectObject2BooleanFunction<T, U> {
      boolean apply(T var1, U var2);
   }

   interface QuadConsumer<T, U, V, W> {
      void accept(T var1, U var2, V var3, W var4);
   }

   interface TriConsumer<T, U, V> {
      void accept(T var1, U var2, V var3);
   }
}
