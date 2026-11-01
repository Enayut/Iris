package net.irisshaders.iris.parsing;

import java.util.Collection;
import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.expression.VariableExpression;
import kroppeb.stareval.function.FunctionContext;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import kroppeb.stareval.function.TypedFunction;
import kroppeb.stareval.function.TypedFunction.Parameter;

public class VectorizedFunction implements TypedFunction {
   final TypedFunction inner;
   final int size;
   final VectorType.ArrayVector returnType;
   final Parameter[] parameters;
   private final VectorizedFunction.ElementAccessExpression[] vectorAccessors;
   private int index;
   private final VectorType.ArrayVector.IntObjectObjectObjectConsumer<VectorizedFunction, FunctionContext, FunctionReturn> mapper = (ix, self, ctx, fr) -> {
      self.index = ix;
      self.inner.evaluateTo(self.vectorAccessors, ctx, fr);
   };

   public VectorizedFunction(TypedFunction inner, int size) {
      this.inner = inner;
      this.size = size;
      this.returnType = new VectorType.ArrayVector(inner.getReturnType(), size);
      Parameter[] innerTypes = inner.getParameters();
      this.parameters = new Parameter[innerTypes.length];
      this.vectorAccessors = new VectorizedFunction.ElementAccessExpression[innerTypes.length];

      for (int i = 0; i < innerTypes.length; i++) {
         this.parameters[i] = new Parameter(new VectorType.ArrayVector(innerTypes[i].type(), size));
         this.vectorAccessors[i] = new VectorizedFunction.ElementAccessExpression(innerTypes[i].type());
      }
   }

   public Type getReturnType() {
      return this.returnType;
   }

   public Parameter[] getParameters() {
      return this.parameters;
   }

   public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
      for (int p = 0; p < params.length; p++) {
         Expression param = params[p];
         param.evaluateTo(context, functionReturn);
         this.vectorAccessors[p].vector = functionReturn.objectReturn;
      }

      this.returnType.map(this, context, functionReturn, this.mapper);
   }

   class ElementAccessExpression implements Expression {
      final Type parameterType;
      Object vector;

      ElementAccessExpression(Type parameterType) {
         this.parameterType = parameterType;
      }

      public void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
         this.parameterType.getValueFromArray(this.vector, VectorizedFunction.this.index, functionReturn);
      }

      public void listVariables(Collection<? super VariableExpression> variables) {
         throw new IllegalStateException();
      }
   }
}
