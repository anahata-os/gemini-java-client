/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a static method as a tool (function) that can be
 * called by the AI model.
 * <p>
 * Methods annotated with {@code @AIToolMethod} are automatically discovered
 * by the {@link ToolManager} and exposed to the Gemini API as function
 * declarations.
 * </p>
 *
 * @author Anahata
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AIToolMethod {
    /**
     * A detailed, human-readable description of what the tool does.
     * This description is sent to the model to help it understand when and
     * how to use the tool.
     *
     * @return The tool description.
     */
    String value();

    /**
     * Determines whether the user must explicitly approve the execution of
     * this tool via a confirmation dialog.
     * <p>
     * Set to {@code false} for safe, read-only operations (e.g., reading a file).
     * Defaults to {@code true} for safety.
     * </p>
     *
     * @return {@code true} if approval is required, {@code false} otherwise.
     */
    boolean requiresApproval() default true;
    
    /**
     * Defines how the output of this tool should be treated within the chat context.
     * <p>
     * Use {@link ContextBehavior#EPHEMERAL} for one-off actions and
     * {@link ContextBehavior#STATEFUL_REPLACE} for tools that return persistent
     * resources like file contents.
     * </p>
     *
     * @return The context behavior.
     */
    ContextBehavior behavior() default ContextBehavior.EPHEMERAL;
}