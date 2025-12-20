package uno.anahata.ai.tools;

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

    /**
     * Indicates whether this parameter is required by the tool.
     * Defaults to true.
     * @return True if required, false otherwise.
     */
    boolean required() default true;
}
