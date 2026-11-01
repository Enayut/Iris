package net.irisshaders.iris.shaderpack.error;

import org.apache.commons.lang3.StringUtils;

public record RusticError(String severity, String message, String detailMessage, String file, int lineNumber, String badLine) {
   @Override
   public String toString() {
      return this.severity
         + ": "
         + this.message
         + "\n --> "
         + this.file
         + ":"
         + this.lineNumber
         + "\n  |\n  | "
         + this.badLine
         + "\n  | "
         + StringUtils.repeat('^', this.badLine.length())
         + " "
         + this.detailMessage
         + "\n  |";
   }
}
