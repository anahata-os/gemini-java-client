/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to provide metadata for a parameter of a method annotated
 * with {@link AIToolMethod}.
 * <p>
 * This metadata is used to generate the JSON schema for the function
 * declaration sent to the Gemini API.
 * </p>
 *
 * @author Anahata
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AIToolParam {
    /**
     * A detailed description of what this parameter represents and any
     * constraints on its value.
     *
     * @return The parameter description.
     */
    String value();

    /**
     * Indicates whether this parameter is required for the tool to function.
     * <p>
     * If {@code true}, the model will be instructed that it must provide a
     * value for this parameter.
     * </p>
     *
     * @return {@code true} if required, {@code false} otherwise.
     */
    boolean required() default true;
}