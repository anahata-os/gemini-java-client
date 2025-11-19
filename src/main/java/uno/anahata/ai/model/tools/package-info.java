/**
 * Defines the domain model for the tool (function) calling subsystem.
 * <p>
 * This package provides a set of model-agnostic POJOs and enums that represent
 * the entire lifecycle of a tool call, from its declaration to its final execution
 * result. By creating this abstraction layer, we decouple the core logic of the
 * {@code ToolManager} and the UI from the specific data types of any single AI
 * provider (like Google's {@code FunctionCall} or {@code FunctionResponse}).
 * <p>
 * The key components are:
 * <ul>
 *   <li>{@link uno.anahata.ai.model.tools.MethodDeclaration}: A model-agnostic definition of a tool.</li>
 *   <li>{@link uno.anahata.ai.model.tools.MethodInvocation}: A request from the model to execute a tool.</li>
 *   <li>{@link uno.anahata.ai.model.tools.MethodInvocationResult}: A rich object containing the final outcome of an invocation.</li>
 *   <li>State Enums ({@link uno.anahata.ai.model.tools.ToolPreference}, {@link uno.anahata.ai.model.tools.PromptDecision}, {@link uno.anahata.ai.model.tools.InvocationStatus}):
 *       Clearly defined enums that model the different states and choices within the
 *       tool lifecycle, eliminating the ambiguity of the previous implementation.</li>
 * </ul>
 *
 * @author pablo-ai
 */
package uno.anahata.ai.model.tools;