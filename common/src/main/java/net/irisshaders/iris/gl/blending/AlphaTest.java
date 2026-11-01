package net.irisshaders.iris.gl.blending;

public record AlphaTest(AlphaTestFunction function, float reference) {
   public static final AlphaTest ALWAYS = new AlphaTest(AlphaTestFunction.ALWAYS, 0.0F);

   public String toExpression(String indentation) {
      return this.toExpression("gl_FragData[0].a", "iris_currentAlphaTest", indentation);
   }

   public String toExpression(String alphaAccessor, String alphaThreshold, String indentation) {
      String expr = this.function.getExpression();
      if (this.function == AlphaTestFunction.ALWAYS) {
         return "// alpha test disabled\n";
      } else if (this.reference == Float.MAX_VALUE) {
         return indentation + "if (!(" + alphaAccessor + " > iris_vertexColorAlpha)) {\n" + indentation + "    discard;\n" + indentation + "}\n";
      } else {
         return this.function == AlphaTestFunction.NEVER
            ? "discard;\n"
            : indentation + "if (!(" + alphaAccessor + " " + expr + " " + alphaThreshold + ")) {\n" + indentation + "    discard;\n" + indentation + "}\n";
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }

      if (obj == null) {
         return false;
      }

      if (this.getClass() != obj.getClass()) {
         return false;
      }

      AlphaTest other = (AlphaTest)obj;
      return this.function != other.function ? false : Float.floatToIntBits(this.reference) == Float.floatToIntBits(other.reference);
   }
}
