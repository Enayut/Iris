package net.irisshaders.iris.parsing;

import java.util.Arrays;
import kroppeb.stareval.Util;
import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.function.AbstractTypedFunction;
import kroppeb.stareval.function.FunctionContext;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;

public class VectorConstructor extends AbstractTypedFunction {
   public VectorConstructor(Type inner, int size) {
      super(new VectorType.ArrayVector(inner, size), (Type[])Util.make(new Type[size], params -> Arrays.fill(params, inner)));
   }

   public VectorType.ArrayVector getReturnType() {
      return (VectorType.ArrayVector)super.getReturnType();
   }

   public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
      VectorType.ArrayVector vectorType = this.getReturnType();
      vectorType.map(params, context, functionReturn, (i, p, ctx, fr) -> p[i].evaluateTo(ctx, fr));
   }
}
