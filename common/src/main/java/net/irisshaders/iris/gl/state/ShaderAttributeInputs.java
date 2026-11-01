package net.irisshaders.iris.gl.state;

import com.mojang.renderpearl.api.GpuFormat.ComponentType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import java.util.List;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

public class ShaderAttributeInputs {
   private boolean ie;
   private boolean color;
   private boolean tex;
   private boolean overlay;
   private boolean light;
   private boolean normal;
   private boolean newLines;
   private boolean glint;
   private boolean specialGlint;
   private boolean text;
   private int entityComponents;

   public ShaderAttributeInputs(VertexFormat format, boolean isFullbright, boolean isLines, boolean glint, boolean text, boolean ie) {
      this.ie = ie;
      this.text = text;
      this.glint = glint;
      this.newLines = isLines;
      if (format == null) {
         format = WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat();
      }

      this.specialGlint = glint && format.contains("UV3");
      format.getElements().forEach(n -> {
         String namex = n.name();
         if ("Color".equals(namex) || "a_Color".equals(namex)) {
            this.color = true;
         }

         if ("LineWidth".equals(namex)) {
            this.newLines = true;
         }

         if ("UV0".equals(namex) || "a_TexCoord".equals(namex)) {
            this.tex = true;
         }

         if ("UV1".equals(namex)) {
            this.overlay = true;
         }

         if (("UV2".equals(namex) || "a_LightAndData".equals(namex)) && !isFullbright) {
            this.light = true;
         }

         if ("Normal".equals(namex) || "iris_Normal".equals(namex)) {
            this.normal = true;
         }
      });
      List<VertexFormatElement> elements = format.getElements();

      for (int index = 0; index < elements.size(); index++) {
         VertexFormatElement element = elements.get(index);
         String name = element.name();
         if ("mc_Entity".equals(name)
            && (element.format().componentType() == ComponentType.SINT_16 || element.format().componentType() == ComponentType.SINT_32)) {
            this.entityComponents = element.format().componentCount();
         }
      }
   }

   public ShaderAttributeInputs(boolean color, boolean tex, boolean overlay, boolean light, boolean normal) {
      this.color = color;
      this.tex = tex;
      this.overlay = overlay;
      this.light = light;
      this.normal = normal;
   }

   public boolean hasColor() {
      return this.color;
   }

   public boolean hasTex() {
      return this.tex;
   }

   public boolean hasOverlay() {
      return this.overlay;
   }

   public boolean hasLight() {
      return this.light;
   }

   public boolean hasNormal() {
      return this.normal;
   }

   public boolean isNewLines() {
      return this.newLines;
   }

   public boolean isGlint() {
      return this.glint;
   }

   public boolean isSpecialGlint() {
      return this.specialGlint;
   }

   public int getEntityComponents() {
      return this.entityComponents;
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = 1;
      result = 31 * result + (this.color ? 1231 : 1237);
      result = 31 * result + (this.tex ? 1231 : 1237);
      result = 31 * result + (this.overlay ? 1231 : 1237);
      result = 31 * result + (this.light ? 1231 : 1237);
      result = 31 * result + (this.normal ? 1231 : 1237);
      result = 31 * result + (this.newLines ? 1231 : 1237);
      result = 31 * result + (this.glint ? 1231 : 1237);
      result = 31 * result + (this.specialGlint ? 1231 : 1237);
      result = 31 * result + (this.text ? 1231 : 1237);
      return 31 * result + this.entityComponents;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         ShaderAttributeInputs other = (ShaderAttributeInputs)obj;
         if (this.color != other.color) {
            return false;
         } else if (this.tex != other.tex) {
            return false;
         } else if (this.overlay != other.overlay) {
            return false;
         } else if (this.light != other.light) {
            return false;
         } else if (this.normal != other.normal) {
            return false;
         } else if (this.newLines != other.newLines) {
            return false;
         } else if (this.glint != other.glint) {
            return false;
         } else if (this.specialGlint != other.specialGlint) {
            return false;
         } else {
            return this.text != other.text ? false : this.entityComponents == other.entityComponents;
         }
      }
   }

   public boolean isText() {
      return this.text;
   }

   public boolean isIE() {
      return this.ie;
   }
}
