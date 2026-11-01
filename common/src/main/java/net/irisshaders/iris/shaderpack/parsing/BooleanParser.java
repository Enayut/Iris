package net.irisshaders.iris.shaderpack.parsing;

import java.util.EmptyStackException;
import java.util.Stack;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;

public class BooleanParser {
   public static boolean parse(String expression, OptionValues valueLookup) {
      try {
         StringBuilder option = new StringBuilder();
         Stack<BooleanParser.Operation> operationStack = new Stack<>();
         Stack<Boolean> valueStack = new Stack<>();

         for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            switch (c) {
               case ' ':
                  break;
               case '!':
                  operationStack.push(BooleanParser.Operation.NOT);
                  break;
               case '&':
                  if (!option.isEmpty()) {
                     valueStack.push(processValue(option.toString(), valueLookup, operationStack));
                     option = new StringBuilder();
                  }

                  if (operationStack.isEmpty() || !operationStack.peek().equals(BooleanParser.Operation.AND)) {
                     operationStack.push(BooleanParser.Operation.OPEN);
                  }

                  i++;
                  operationStack.push(BooleanParser.Operation.AND);
                  break;
               case '(':
                  operationStack.push(BooleanParser.Operation.OPEN);
                  break;
               case ')':
                  if (!option.isEmpty()) {
                     valueStack.push(processValue(option.toString(), valueLookup, operationStack));
                     option = new StringBuilder();
                  }

                  if (!operationStack.isEmpty() && operationStack.peek().equals(BooleanParser.Operation.AND)) {
                     evaluate(operationStack, valueStack, true);
                  }

                  evaluate(operationStack, valueStack, true);
                  break;
               case '|':
                  if (!option.isEmpty()) {
                     valueStack.push(processValue(option.toString(), valueLookup, operationStack));
                     option = new StringBuilder();
                  }

                  if (!operationStack.isEmpty() && operationStack.peek().equals(BooleanParser.Operation.AND)) {
                     evaluate(operationStack, valueStack, true);
                  }

                  i++;
                  operationStack.push(BooleanParser.Operation.OR);
                  break;
               default:
                  option.append(c);
            }
         }

         if (!option.isEmpty()) {
            valueStack.push(processValue(option.toString(), valueLookup, operationStack));
         }

         evaluate(operationStack, valueStack, false);
         boolean result = valueStack.pop();
         if (valueStack.isEmpty() && operationStack.isEmpty()) {
            return result;
         }

         Iris.logger.warn("Failed to parse the following boolean operation correctly, stacks not empty, defaulting to true!: '{}'", expression);
         return true;
      } catch (EmptyStackException emptyStackException) {
         Iris.logger.warn("Failed to parse the following boolean operation correctly, stacks empty when it shouldn't, defaulting to true!: '{}'", expression);
         return true;
      }
   }

   private static boolean processValue(String value, OptionValues valueLookup, Stack<BooleanParser.Operation> operationStack) {
      boolean booleanValue = switch (value) {
         case "true", "1" -> true;
         case "false", "0" -> false;
         default -> valueLookup != null && valueLookup.getBooleanValueOrDefault(value);
      };
      if (!operationStack.isEmpty() && operationStack.peek() == BooleanParser.Operation.NOT) {
         operationStack.pop();
         return !booleanValue;
      } else {
         return booleanValue;
      }
   }

   private static void evaluate(Stack<BooleanParser.Operation> operationStack, Stack<Boolean> valueStack, boolean currentBracket) {
      boolean value = valueStack.pop();

      while (!operationStack.isEmpty() && (!currentBracket || operationStack.peek() != BooleanParser.Operation.OPEN)) {
         value = operationStack.pop().compute(value, valueStack);
      }

      if (!operationStack.isEmpty() && operationStack.peek() == BooleanParser.Operation.OPEN) {
         operationStack.pop();
         if (!operationStack.isEmpty() && operationStack.peek() == BooleanParser.Operation.NOT) {
            value = operationStack.pop().compute(value, valueStack);
         }
      }

      valueStack.push(value);
   }

   private enum Operation {
      AND {
         @Override
         boolean compute(boolean value, Stack<Boolean> valueStack) {
            return valueStack.pop() && value;
         }
      },
      OR {
         @Override
         boolean compute(boolean value, Stack<Boolean> valueStack) {
            return valueStack.pop() || value;
         }
      },
      NOT {
         @Override
         boolean compute(boolean value, Stack<Boolean> valueStack) {
            return !value;
         }
      },
      OPEN;

      boolean compute(boolean value, Stack<Boolean> valueStack) {
         return value;
      }
   }
}
