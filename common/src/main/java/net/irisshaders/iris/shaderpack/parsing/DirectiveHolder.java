package net.irisshaders.iris.shaderpack.parsing;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.joml.Vector2f;
import org.joml.Vector3i;
import org.joml.Vector4f;

public interface DirectiveHolder {
   void acceptUniformDirective(String var1, Runnable var2);

   void acceptCommentStringDirective(String var1, Consumer<String> var2);

   void acceptCommentIntDirective(String var1, IntConsumer var2);

   void acceptCommentFloatDirective(String var1, FloatConsumer var2);

   void acceptConstBooleanDirective(String var1, BooleanConsumer var2);

   void acceptConstStringDirective(String var1, Consumer<String> var2);

   void acceptConstIntDirective(String var1, IntConsumer var2);

   void acceptConstFloatDirective(String var1, FloatConsumer var2);

   void acceptConstVec2Directive(String var1, Consumer<Vector2f> var2);

   void acceptConstIVec3Directive(String var1, Consumer<Vector3i> var2);

   void acceptConstVec4Directive(String var1, Consumer<Vector4f> var2);
}
