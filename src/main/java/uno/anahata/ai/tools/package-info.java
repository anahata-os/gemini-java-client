/* Licensed under the Apache License, Version 2.0 */
/**
 * Provides the core framework for defining and managing local tools (functions) that the AI model can execute.
 * <p>
 * This package is central to extending the AI's capabilities beyond simple text generation. It allows developers
 * to expose Java methods as tools that the model can call to interact with the local system, application, or any
 * other service.
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.functions.ToolManager}: The main orchestrator for tool management. It discovers
 *       tools via annotations, generates the necessary JSON schema for the Gemini API, executes function calls,
 *       and handles the user confirmation workflow.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.AIToolMethod}: An annotation used to mark a Java method as a tool
 *       callable by the AI. It includes properties for description and context management behavior.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.AIToolParam}: An annotation for describing the parameters of a tool
 *       method, which is used to generate a more informative schema for the model.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.FunctionPrompter}: An interface that decouples the tool confirmation
 *       process from any specific UI, allowing for flexible implementations (e.g., Swing dialogs, console prompts).</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.FailureTracker}: A utility to prevent the model from getting stuck in
 *       a loop of repeatedly calling a failing tool.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.ContextBehavior}: An enum that defines how the result of a tool call
 *       should affect the conversation context (e.g., as an ephemeral event or a stateful resource update).</li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.functions.schema}: Contains the logic for generating the JSON schema required
 *       by the Gemini API from Java method signatures.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.spi}: Provides a Service Provider Interface with a set of pre-built,
 *       generic tools for common tasks like file I/O, shell command execution, and JVM interaction.</li>
 * </ul>
 */
package uno.anahata.ai.tools;