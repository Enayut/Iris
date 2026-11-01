package net.irisshaders.iris.shaderpack.parsing;

public class CommentDirective {
   private final CommentDirective.Type type;
   private final String directive;
   private final int location;

   CommentDirective(CommentDirective.Type type, String directive, int location) {
      this.type = type;
      this.directive = directive;
      this.location = location;
   }

   public CommentDirective.Type getType() {
      return this.type;
   }

   public String getDirective() {
      return this.directive;
   }

   public int getLocation() {
      return this.location;
   }

   public enum Type {
      DRAWBUFFERS,
      RENDERTARGETS;
   }
}
