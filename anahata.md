# gemini-java-client Project Overview

## 1. High-Level Summary

`gemini-java-client` is a **pure-Java, enterprise-grade client framework** for the Google Gemini API. It is architected as a powerful, extensible AI assistant platform, going far beyond a simple API wrapper. Its core strength is the **annotation-driven local tool (function) system**, which enables the AI to execute arbitrary Java code, interact with the local file system, and manage the application's state. The project includes a complete, embeddable **Swing UI** for a rich, interactive chat experience, making it ideal for integration into IDEs (like the NetBeans plugin) or standalone desktop applications.

## 2. Core Features

- **Pure Java & Portable:** Built entirely in Java, ensuring maximum compatibility and performance across different JVM environments.
- **Deep Local Integration (Functions):** An annotation-driven Service Provider Interface (SPI) allows the AI to invoke local Java methods (`@AIToolMethod`), enabling direct interaction with the host machine via tools like `LocalFiles`, `LocalShell`, and dynamic code execution via `RunningJVM`.
- **Embeddable Swing UI:** A complete, renderer-based UI (`uno.anahata.gemini.ui.GeminiPanel`) that supports complex parts (text, images, interactive tool calls, grounding metadata) and is designed for seamless embedding.
- **Robust Context Management:** Features a sophisticated `ContextManager` with automatic, dependency-aware pruning to manage token limits and maintain conversation integrity.
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
