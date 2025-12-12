# Refactoring Plan: Active Workspace, Pruning, and Core Architecture

## 1. Vision & Goals

This document outlines a major architectural refactoring of the `gemini-java-client` framework. The primary goals are to:
- **Improve Model Efficiency & Recency:** Implement the "Active Workspace" to ensure the model always has the most relevant, up-to-date context.
- **Simplify Context Management:** Make all tool calls ephemeral and radically simplify the pruning logic.
- **Enhance Robustness & Scalability:** Decouple large classes, improve the tooling model, and prepare the project for future growth.
- **Improve Developer Experience:** Introduce clearer contracts, better annotations, and move towards a more modern, instance-based tooling architecture.

## 2. Current Progress

- The foundational `ChatMessage` class has already been refactored to include the `Map<Part, List<Part>> dependencies` map, which is the cornerstone of our intelligent pruning strategy.

---

## Phase 1: Core Architectural Refactoring

This phase focuses on improving the core components to make them more modular, testable, and extensible before implementing the Active Workspace.

### 2.1. `FunctionManager` Decomposition
`FunctionManager` is currently a large class with multiple responsibilities. It will be broken down:

- **`FunctionExecutor` Delegate:** A new `FunctionExecutor` class will be created. Its sole responsibility will be to take a `FunctionCall` and a `Method` object and handle the actual invocation, parameter marshalling, and exception handling. `FunctionManager` will delegate all execution tasks to this class.
- **`TypeConverter` Delegate:** The logic for converting Java return types into the appropriate GenAI `FunctionResponse` map (`toString()` vs. JSON serialization) will be extracted into a new `TypeConverter` class.
- **Move Inner Classes:** The inner classes (`ExecutedToolCall`, `FunctionProcessingResult`, `FunctionInfo`) will be moved to their own standalone files in a new `uno.anahata.gemini.functions.dto` package to improve modularity.
- **Use `@Getter`:** All DTOs/POJOs will use Lombok's `@Getter` instead of having manually written getters.

### 2.2. Tooling Model: Static vs. Instance-Based ("Servlet-Style")
The current model of static tool methods is limiting. We will explore and likely migrate to an instance-based model.

- **Concept:** Instead of being static, tool methods will be instance methods. Each `Chat` instance will hold a map of instantiated tool objects.
- **`BaseTool` Class:** A `BaseTool` class will be created. Tool classes can extend this to get access to helper methods like `getCurrentChat()`, `getActiveWorkspace()`, or `unloadResource(String resourceId)`.
- **Benefits:** This eliminates the need for `Chat.getCallingInstance()`, makes tools easier to test, and allows them to hold state if necessary.

### 2.3. Tool Return Type Handling
The model needs to know how to interpret the `FunctionResponse`. We will provide explicit control over this.

- **`@ResponseFormat` Annotation:** A new annotation, `@ResponseFormat(Format.JSON)` or `@ResponseFormat(Format.STRING)`, will be created.
- **Usage:** When placed on an `@AIToolMethod`, this will instruct the new `TypeConverter` on how to serialize the method's return value into the `FunctionResponse` map. The default will be `JSON` for objects and `STRING` for primitives. This solves the ambiguity of how to handle complex return types.

---

## Phase 2: The Active Workspace Implementation

This phase implements the core "Whiteboard" concept for state management.

### 3.1. Core Concept
- **All Tool Calls Become Ephemeral:** The `FunctionCall`/`FunctionResponse` pair for any tool that modifies state is now considered a transient log entry. Its purpose is to trigger a change, not to hold the state itself. The "two-turn rule" will automatically clean up these log entries.
- **Client-Side State:** A new `ActiveWorkspaceManager` class will be created. It will hold a `Map<String, StatefulResource>` representing the definitive "current state" of all resources.
- **Prompt Injection:** Before every request to the model, `Chat` will serialize the entire content of the `ActiveWorkspaceManager` into a series of `Part`s and prepend them to the user's message.

### 3.2. `StatefulResource` Enhancements
- **`shouldRefresh()` Method:** The `StatefulResource` interface will gain a new method: `boolean shouldRefresh()`.
- **Implementation:** Before injecting a resource into the workspace, the `ActiveWorkspaceManager` will check this flag. If `true`, it will re-invoke the source tool (e.g., `LocalFiles.readFile`) to get the latest version from disk. This is perfect for screenshots or for a user-toggled "always fresh" mode.

### 3.3. Tool Signature Changes
- Methods that currently return a `StatefulResource` (e.g., `LocalFiles.readFile` returning `FileInfo`) will be changed to return a simple status message (e.g., `String` or a `StatusResponse` DTO). Their new primary job is to update the `ActiveWorkspaceManager`.

---

## Phase 3: Long-Term & Ecosystem Improvements

These are larger-scale explorations for the health of the project.

### 4.1. Project Modularity
- **`gemini-java-client-swing`:** Explore splitting all Swing/UI-related classes (`ChatPanel`, `SwingFunctionPrompter`, etc.) into a separate project. This would leave `gemini-java-client` as a pure, headless library that can be used in any environment.

### 4.2. Java Version Upgrade
- **Current State:** The project compiles to Java 8.
- **Exploration:** Analyze the benefits of upgrading to a modern LTS version (e.g., Java 17 or 21). Benefits include:
    - **Language Features:** Records, Text Blocks, Switch Expressions, Sealed Classes.
    - **Performance:** G1 GC improvements, general runtime optimizations.
    - **Virtual Threads:** Potentially a massive simplification for handling asynchronous operations.

### 4.3. `Chat` Refactoring
- `Chat` is becoming a "God Class". We need to plan for its decomposition, delegating responsibilities like prompt construction, response processing, and state management to smaller, more focused classes.

---

## 5. Untested Areas & Open Questions

- **Asynchronous `JobInfo`:** The async execution flow has not been tested. It needs a thorough review to ensure it integrates with the new architecture.
- **`RunningJVM` State Persistence:** The ability for the `compileAndExecuteJava` tool to maintain state across calls (via the `chatTemp` map) needs to be validated and potentially redesigned to fit the new instance-based tool model.
