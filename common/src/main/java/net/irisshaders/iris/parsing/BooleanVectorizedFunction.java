package net.irisshaders.iris.parsing;

import java.util.Collection;
import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.expression.VariableExpression;
import kroppeb.stareval.function.FunctionContext;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import kroppeb.stareval.function.TypedFunction;
import kroppeb.stareval.function.Type.Primitive;
import kroppeb.stareval.function.TypedFunction.Parameter;

public class BooleanVectorizedFunction implements TypedFunction {
   final TypedFunction inner;
   final int size;
   final Parameter[] parameters;
   private final BooleanVectorizedFunction.ElementAccessExpression[] vectorAccessors;
   private int index;

   public BooleanVectorizedFunction(TypedFunction inner, int size) {
      this.inner = inner;
      this.size = size;
      Parameter[] innerTypes = inner.getParameters();
      this.parameters = new Parameter[innerTypes.length];
      this.vectorAccessors = new BooleanVectorizedFunction.ElementAccessExpression[innerTypes.length];

      for (int i = 0; i < innerTypes.length; i++) {
         this.parameters[i] = new Parameter(VectorType.of((Primitive)innerTypes[i].type(), size));
         this.vectorAccessors[i] = new BooleanVectorizedFunction.ElementAccessExpression(innerTypes[i].type());
      }
   }

   public Type getReturnType() {
      return Type.Boolean;
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

      for (int i = 0; i < this.size; i++) {
         this.index = i;
         this.inner.evaluateTo(this.vectorAccessors, context, functionReturn);
         if (!functionReturn.booleanReturn) {
            return;
         }
      }
   }

   class ElementAccessExpression implements Expression {
      final Type parameterType;
      Object vector;

      ElementAccessExpression(Type parameterType) {
         this.parameterType = parameterType;
      }

      public void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
         this.parameterType.getValueFromArray(this.vector, BooleanVectorizedFunction.this.index, functionReturn);
      }

      public void listVariables(Collection<? super VariableExpression> variables) {
         throw new IllegalStateException();
      }
   }
}
