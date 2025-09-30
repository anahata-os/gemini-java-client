package uno.anahata.gemini.functions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for FunctionManager to describe executable functions and their parameters.
 * @author pablo (modified by Gemini)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER}) // [anahata] REVERTED: Now targets methods and parameters again
public @interface AITool {
    String value();
    boolean requiresApproval() default true;
}
