package net.irisshaders.iris.shaderpack.materialmap;

import java.util.Objects;

public class NamespacedId {
   private final String namespace;
   private final String name;

   public NamespacedId(String combined) {
      int colonIdx = combined.indexOf(58);
      if (colonIdx == -1) {
         this.namespace = "minecraft";
         this.name = combined;
      } else {
         this.namespace = combined.substring(0, colonIdx);
         this.name = combined.substring(colonIdx + 1);
      }
   }

   public NamespacedId(String namespace, String name) {
      this.namespace = Objects.requireNonNull(namespace);
      this.name = Objects.requireNonNull(name);
   }

   public String getNamespace() {
      return this.namespace;
   }

   public String getName() {
      return this.name;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         NamespacedId that = (NamespacedId)o;
         return this.namespace.equals(that.namespace) && this.name.equals(that.name);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = 1;
      result = 31 * result + (this.namespace == null ? 0 : this.namespace.hashCode());
      return 31 * result + (this.name == null ? 0 : this.name.hashCode());
   }

   @Override
   public String toString() {
      return this.namespace + ":" + this.name;
   }
}
