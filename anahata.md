# gemini-java-client Project Overview

## 1. High-Level Summary

`gemini-java-client` is a **pure-Java, enterprise-grade client framework** for the Google Gemini API. It is architected as a powerful, extensible AI assistant platform, going far beyond a simple API wrapper. Its core strength is the **annotation-driven local tool (function) system**, which enables the AI to execute arbitrary Java code, interact with the local file system, and manage the application's state. The project includes a complete, embeddable **Swing UI** for a rich, interactive chat experience, making it ideal for integration into IDEs (like the NetBeans plugin) or standalone desktop applications.

## 2. Core Features

- **Pure Java & Portable:** Built entirely in Java, ensuring maximum compatibility and performance across different JVM environments.
- **Deep Local Integration (Functions):** An annotation-driven Service Provider Interface (SPI) allows the AI to invoke local Java methods (`@AIToolMethod`), enabling direct interaction with the host machine via tools like `LocalFiles`, `LocalShell`, and dynamic code execution via `RunningJVM`.
- **Embeddable Swing UI:** A complete, renderer-based UI (`uno.anahata.gemini.ui.GeminiPanel`) that supports complex parts (text, images, interactive tool calls, grounding metadata) and is designed for seamless embedding.
- **Robust Context Management:** Features a sophisticated `ContextManager` with automatic, dependency-aware pruning to manage token limits, and maintain conversation integrity.
- **Stateful Resource Tracking:** Tracks local resources (e.g., files read into context) to detect and manage stale versions, ensuring the AI always works with the latest data.
- **Dynamic System Instructions:** A provider-based system dynamically injects context-aware instructions (e.g., system properties, environment variables, project overview) into the model's prompt for superior performance and relevance.
- **Session Persistence:** Uses Kryo serialization for fast and reliable saving and loading of entire chat sessions, including all context and stateful resources.

## 3. Architectural Overview

The project is logically divided into four main layers, designed for modularity and clear separation of concerns.

- **Core Logic (`uno.anahata.gemini`):** Contains the central orchestrators, including `GeminiChat` (conversation loop, API retries) and `GeminiConfig` (host-specific configuration).
- **Context System (`uno.anahata.gemini.context`):** Manages the conversation history, token limits, session persistence (`session`), resource tracking (`stateful`), and pruning logic (`pruning`).
- **Function System (`uno.anahata.gemini.functions`):** The framework for discovering, generating schema for, and executing local tools, including the `FailureTracker` and `ContextBehavior` logic.
- **UI Layer (`uno.anahata.gemini.ui`):** The complete Swing-based user interface, including renderers (`render`) and UI-specific tools (`functions.spi`).
- **Internal Utilities (`uno.anahata.gemini.internal`):** Helper classes for serialization (Kryo, Gson), MIME type detection (Tika), and general Part/Content manipulation.

## 4. Coding Principles

1.  **Javadoc Integrity:** As an open-source Java library, comprehensive documentation is paramount.
    *   Existing Javadoc, comments, and blank lines **must never be removed**.
    *   New public classes and methods **must have Javadoc**.
    *   Changes should be made by patching, not regenerating, to preserve the original structure and comments.
2.  **Dependency Management Workflow:** Adhere to this strict workflow when adding new dependencies to ensure project stability and maintainability:
    a. **Find Latest Version:** Use `MavenSearch.searchMavenIndex` to identify the latest stable version of the desired artifact.
    b. **Check for Conflicts:** Before adding, use `MavenPom.getResolvedDependencies` to inspect the project's current transitive dependency tree. Check for existing versions of the artifact or potential conflicts with other libraries.
    c. **Add Dependency:** Use the `MavenPom.addDependency` tool to safely modify the `pom.xml`.
    d. **Download Sources:** Immediately after adding the dependency, use `Maven.downloadDependencyArtifact` to download the `sources` and `javadoc` for the new artifact. This is crucial for future development and debugging.

## 5. V1 Launch Goals (Immediate Focus)

### Phase 1: Core Lifecycle & Concurrency
-   [ ] **"Tool call execution does not follow the same sequence as the received FunctionCalls:** Sometimes the model provides two tool calls but in the tool output they show as if they were executed in different order.
-   [ ] **"Tool call execution**. FunctionPrompting should be embedded in the main chat window, not in a popup, When the model proposes 3 tool calls, the user should be able to execute an individual tool with a click and possibly re-run it later as many times as he wants. There should still be a runAll at the bottom that runs all the tools marked as Yes" currently if the model wants to do two operations and one depends on the oter
-   [ ] **"Ghost Chat" Prevention:** Tightly couple the `AnahataTopComponent` lifecycle (in `anahata-netbeans-ai`) to the `GeminiChat` instance. When the UI component is closed, it must signal the chat to terminate all background processes.
-   [ ] **Cancellation Framework:** Implement a robust cancellation mechanism (`ExecutorService`/`Future`) to allow interrupting:
    -   In-flight API calls.
    -   The entire tool-call-reloop cycle.
    -   Individual long-running tool executions (e.g., Maven downloads).
-   [ ] **Fix Async Job Delivery:** Implement a queueing mechanism in `GeminiChat` to ensure that asynchronous job results are reliably delivered and not dropped when a tool loop is already in progress.

### Phase 2: Rich Status Reporting & UI Feedback
-   [ ] **Status Bar Integration:** Fully integrate the new status bar and progress bar components into `GeminiPanel`.
-   [ ] **Detailed Status Reporting:** The status bar must clearly display:
    -   The current state (e.g., "API Call in Progress...", "Executing tool: `Maven.downloadProjectDependencies`").
    -   API call latency.
    -   Retry attempts, including the count and the last `Exception.toString()` (e.g., "Retry 2/5: 429 Quota Exceeded...").
    -   The total token count, ensuring it remains visible and accurate even during tool execution.

### Phase 3: Chat Identity & Session Management
-   [ ] **Chat Nickname:** Add a `displayName` property to chat sessions. This name should be persisted and displayed in function confirmation popups and used by the NetBeans plugin for tab identification.
-   [ ] **Autobackup Restoration:** Restore the "autoload on startup" functionality for autobackups. Implement graceful, non-blocking error messages for any Kryo deserialization failures.

### Phase 4: UI/UX & Code Quality
-   [ ] **`FunctionsPanel` Redesign:** Implement the two-panel layout (Tool classes on the left, methods for the selected class on the right) with summary counts for permission settings.
-   [ ] **Component Cleanup:** Begin refactoring duplicated UI components (e.g., `ScrollableTooltipPopup`) and fix UI bugs like the `ContextHeatmapPanel` tooltip issue.
-   [ ] **`package-info.java`:** Start the process of ensuring every package has a comprehensive Javadoc `package-info.java` file.
-   [ ] **Context Integrity:** Investigate the pruning and message reconstruction logic to ensure that no metadata (e.g., `thoughtSignatures` from `usageMetadata`) is lost.

## 6. V2 Mega-Refactor Plan (Future Focus)

This is the long-term architectural plan to be executed after the V1 launch.

-   [ ] **Project Modularity:** Split the project into three modules: `anahata-ai-core` (interfaces), `anahata-ai-gemini` (Gemini implementation), and `anahata-ai-swing` (reusable UI).
-   [ ] **Multi-Model Abstraction:**
    -   Create a generic `ChatModel` interface to allow plugging in other providers (OpenAI, Claude, etc.).
    -   **`ContentProducer` Interface:** Abstract all model-specific content creation (`Content`, `Part` objects) into a provider-agnostic interface. This is critical for decoupling the core logic from the Gemini API and will handle the creation of all messages (tool responses, user feedback, errors, etc.).
-   [ ] **Decoupled Status Management:** Move the `ChatStatus` enum and its listener mechanism out of `GeminiChat` into a dedicated `StatusManager` class to further slim down the core orchestrator.
-   [ ] **Per-Chat Permissions:** Refactor the function permission model to be session-specific instead of global.
-   [ ] **Asynchronous Input:** Implement the ability for the user to queue a message while a tool loop is running, which will be sent with the next API call.
-   [ ] **Instance-Based Tooling:** Refactor the tool system from static methods to an instance-based model to improve testability and state management.
-   [ ] **Active Workspace & Providers:**
    -   Implement a `WorkspaceProvider` system to abstract the provision of contextual data (like screenshots, file contents, etc.).
    -   Transition to an "Active Workspace" model where stateful resources are managed by these providers and injected into the prompt, rather than being fished from the chat history. This will eliminate context bloat and is the preferred strategy over auto-reloading stale resources.
-   [ ] **Embeddings for Notes:** Convert the personal notes system from a simple folder of markdown files into a searchable knowledge base using embeddings for context augmentation.
-   [ ] **Code Health:** Investigate why `buildApiContext` needs to filter for null `Content` objects, as this may be hiding an upstream bug.

## 7. Current Task Board (As of 2025-11-15)

This section tracks our active work items to ensure continuity across sessions.

-   **[Highest Priority] Task J: Design and Refactor Maven Tools:**
    -   **Status:** In Progress.
    -   **Description:** The current Maven tools are spread across `Maven.java` and `MavenPom.java`, leading to confusion and redundancy. The `addDependency` tool has undergone several flawed design iterations. The new focus is to pause all other work and architect a clean, robust, and consolidated set of Maven tools.
    -   **Next Step:** Analyze the user's feedback on asynchronous downloads and pre-download verification to create a definitive design proposal for a new `addDependency` "super-tool".

-   **[On Hold] Task I: Test Schema Generation for `Tree` class:**
    -   **Status:** On Hold.
    -   **Description:** The investigation into serializing `com.google.genai.types` objects for debugging is complete. The findings have been documented in `jsonSchema.md`. This task is paused to focus on the higher-priority Maven tool redesign.
    -   **Next Step:** Awaiting completion of Maven tool refactoring.

-   **[High Priority] Task H: Improve Input Panel UX:**
    -   **Status:** Done.
    -   **Description:** The user input text area remains enabled at all times, clears immediately on send, and auto-resizes as the user types. This was implemented using `JXTextArea` from the SwingX library.
    -   **Next Step:** Completed.

-   **[High Priority] Task G: Refactor to `ChatStatusEvent`:**
    -   **Status:** Done.
    -   **Description:** Refactor the status listening mechanism from a simple listener to a full event-driven model using a new `ChatStatusEvent` class. This will provide richer context to listeners and improve the architectural design.
    -   **Next Step:** Completed.

-   **[High Priority] Task A: Fix UI/Audio Bugs:**
    -   **Status:** Done.
    -   **Description:** The UI was unstable. The logs showed two distinct problems: an `UnsupportedAudioFileException` when trying to play `start.wav`, and a failure to load `bell.png` and `bell_mute.png` for the sound toggle button.
    -   **Next Step:** Completed.

-   **[High Priority] Task B: Add New Chat Status:**
    -   **Status:** Done.
    -   **Description:** Add a new `MAX_RETRIES_REACHED` status to the `ChatStatus` enum. This will provide clearer feedback to the user when the assistant is stuck due to persistent API or tool failures.
    -   **Next Step:** Completed.

-   **[Low Priority] Task C: Code Quality:**
    -   **Status:** To Do.
    -   **Description:** The IDE reports a warning in `AnahataNavigatorTopComponent.java` that an anonymous inner class can be converted to a lambda.
    -   **Next Step:** Apply the suggested lambda conversion in `AnahataNavigatorTopComponent.java`.

-   **[Strategic] Task D: 'Active Workspace' Refactor:**
    -   **Status:** On Hold.
    -   **Description:** A strategic, multi-phase refactor to move from a "context-in-history" model to a more efficient "Active Workspace" model using `ContextProvider`s.
    -   **Next Step:** Awaiting stabilization of the current feature work.

-   **[Housekeeping] Task E: Context Management:**
    -   **Status:** On Hold.
    -   **Description:** Summarize and prune the files related to the 'Active Workspace' analysis (`ContextManager.java`, `ContextPruner.java`, etc.) from the conversation context.
    -   **Next Step:** Awaiting stabilization of the current feature work.

-   **[Medium Priority] Task F: Implement Per-Status Audio Notifications:**
    -   **Status:** To Do.
    -   **Description:** Enhance the UI to provide a unique audio notification for each `ChatStatus`.
    -   **Next Step:** 
        1. Find and add new `.wav` files to the `/sounds` resources for each status.
        2. Update the `handleStatusSound` method in `StatusPanel.java` to play the appropriate sound.


## 8. Development & Testing Notes

- When testing code in this project via `NetBeansProjectJVM.compileAndExecuteInProject`, **always set `includeCompileAndExecuteDependencies` to `false`**. This is crucial to avoid `LinkageError` exceptions, as the NetBeans Platform already provides the necessary dependencies in its own classloader. Including them again creates a classloader conflict.

- The AI Assistant's execution environment (the Anahata NetBeans plugin) **inherits the full, resolved classpath of the `gemini-java-client` project**. This means that any dependency (direct or transitive) that is correctly defined in the `pom.xml` is automatically available to dynamically compiled code. There is no need to use the `includeCompileAndExecuteDependencies` flag if the dependency is properly managed by Maven.
