package net.irisshaders.iris.shaderpack.transform;

public interface Transformations {
   boolean contains(String var1);

   void injectLine(Transformations.InjectionPoint var1, String var2);

   void replaceExact(String var1, String var2);

   void replaceRegex(String var1, String var2);

   String getPrefix();

   void setPrefix(String var1);

   void define(String var1, String var2);

   enum InjectionPoint {
      DEFINES,
      BEFORE_CODE,
      END;
   }
}
