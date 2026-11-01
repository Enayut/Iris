package net.irisshaders.iris.parsing;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import kroppeb.stareval.function.Type.ObjectType;
import kroppeb.stareval.function.Type.Primitive;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

public abstract class VectorType extends ObjectType {
   public static final VectorType.JOMLVector<Vector2f> VEC2 = new VectorType.JOMLVector<>("vec2", Vector2f::new);
   public static final VectorType.JOMLVector<Vector3f> VEC3 = new VectorType.JOMLVector<>("vec3", Vector3f::new);
   public static final VectorType.JOMLVector<Vector4f> VEC4 = new VectorType.JOMLVector<>("vec4", Vector4f::new);
   public static final VectorType.JOMLVector<Vector2i> I_VEC2 = new VectorType.JOMLVector<>("ivec2", Vector2i::new);
   public static final VectorType.JOMLVector<Vector3i> I_VEC3 = new VectorType.JOMLVector<>("ivec3", Vector3i::new);
   public static final VectorType.JOMLVector<Vector4i> I_VEC4 = new VectorType.JOMLVector<>("ivec4", Vector4i::new);
   public static final VectorType B_VEC2 = new VectorType.ArrayVector(Type.Boolean, 2);
   public static final VectorType B_VEC3 = new VectorType.ArrayVector(Type.Boolean, 3);
   public static final VectorType B_VEC4 = new VectorType.ArrayVector(Type.Boolean, 4);
   public static final VectorType.ArrayVector[] AllArrayVectorTypes = Stream.of(Type.Int, Type.Boolean)
      .flatMap(type -> IntStream.rangeClosed(2, 4).mapToObj(i -> new VectorType.ArrayVector(type, i)))
      .toArray(VectorType.ArrayVector[]::new);
   public static final VectorType[] AllVectorTypes = Arrays.stream(Type.AllPrimitives)
      .flatMap(type -> IntStream.rangeClosed(2, 4).mapToObj(i -> of(type, i)))
      .toArray(VectorType[]::new);

   public static VectorType of(Primitive primitive, int size) {
      if (primitive.equals(Type.Float)) {
         return switch (size) {
            case 2 -> VEC2;
            case 3 -> VEC3;
            case 4 -> VEC4;
            default -> throw new IllegalArgumentException("not a valid vector");
         };
      } else {
         return new VectorType.ArrayVector(primitive, size);
      }
   }

   public static class ArrayVector extends VectorType {
      private final Type inner;
      private final int size;

      public ArrayVector(Type inner, int size) {
         this.inner = inner;
         this.size = size;
      }

      public Object createObject() {
         return this.inner.createArray(this.size);
      }

      public void setValue(Object vector, int index, FunctionReturn functionReturn) {
         this.inner.setValueFromReturn(vector, index, functionReturn);
      }

      public void getValue(Object vector, int index, FunctionReturn functionReturn) {
         this.inner.getValueFromArray(vector, index, functionReturn);
      }

      public <T1, T2> void map(
         T1 item1, T2 item2, FunctionReturn functionReturn, VectorType.ArrayVector.IntObjectObjectObjectConsumer<T1, T2, FunctionReturn> mapper
      ) {
         Object array = this.createObject();

         for (int i = 0; i < this.size; i++) {
            mapper.accept(i, item1, item2, functionReturn);
            this.setValue(array, i, functionReturn);
         }

         functionReturn.objectReturn = array;
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof VectorType.ArrayVector that) ? false : this.size == that.size && this.inner.equals(that.inner);
         }
      }

      public int hashCode() {
         return Objects.hash(this.inner, this.size);
      }

      public String toString() {
         String base = this.inner.equals(Type.Float) ? "" : this.inner.toString().substring(0, 1);
         return "__" + base + "vec" + this.size;
      }

      public interface IntObjectObjectObjectConsumer<TB, TC, TD> {
         void accept(int var1, TB var2, TC var3, TD var4);
      }
   }

   public static class JOMLVector<T> extends VectorType {
      private final String name;
      private final Supplier<T> supplier;

      public JOMLVector(String name, Supplier<T> supplier) {
         this.name = name;
         this.supplier = supplier;
      }

      public String toString() {
         return this.name;
      }

      public T create() {
         return this.supplier.get();
      }
   }
}
