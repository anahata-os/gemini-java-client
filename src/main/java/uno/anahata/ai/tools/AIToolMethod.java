package uno.anahata.ai.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a method that can be called by the AI model.
 * This annotation should be placed on the static method itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AIToolMethod {
    /**
     * A detailed description of what the tool does.
     * @return The description.
     */
    String value();

    /**
     * Whether the user should be prompted for approval before this tool is executed.
     * Set to false for safe, read-only operations.
     * @return True if approval is required, false otherwise.
     */
    boolean requiresApproval() default true;
    
    /**
     * Defines how the output of this tool should be treated within the chat context.
     * @return The context behavior.
     */
    ContextBehavior behavior() default ContextBehavior.EPHEMERAL;
}
