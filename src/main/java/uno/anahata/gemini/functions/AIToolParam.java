package uno.anahata.gemini.functions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a parameter for a method that is exposed to the AI model.
 * This annotation should be placed on the parameters of a method annotated with @AIToolMethod.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AIToolParam {
    /**
     * A detailed description of what this parameter represents.
     * @return The description.
     */
    String value();
}
