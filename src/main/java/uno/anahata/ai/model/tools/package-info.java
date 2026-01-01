/* Licensed under the Apache License, Version 2.0 */
/**
 * Defines the domain model for the tool (function) calling subsystem.
 * <p>
 * This package provides a set of model-agnostic POJOs and enums that represent
 * the entire lifecycle of a tool call, from its declaration to its final execution
 * result. By creating this abstraction layer, we decouple the core logic of the
 * {@code ToolManager} and the UI from the specific data types of any single AI
 * provider (like Google's {@code FunctionCall} or {@code FunctionResponse}).
 *
 * @author anahata
 */
package uno.anahata.ai.model.tools;