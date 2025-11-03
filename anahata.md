# gemini-java-client Project Overview

## 1. High-Level Summary

`gemini-java-client` is a comprehensive, pure-Java client framework for the Google Gemini API. It is designed to be more than a simple API wrapper, providing a full-featured, extensible chat application architecture with a focus on integrating powerful local tools (functions). The project includes a ready-to-use Swing UI implementation, making it suitable for both standalone desktop applications and for embedding into larger host applications like the Anahata NetBeans Plugin.

## 2. Core Features

- **Pure Java:** No external non-Java dependencies are required, ensuring high portability.
- **Swing UI Framework:** A complete, renderer-based UI (`GeminiPanel`) is provided for displaying complex chat interactions, including text, images, and interactive tool calls.
- **Extensible Local Tools (SPI):** The framework's power lies in its Service Provider Interface (SPI) for local functions. This allows the AI to interact directly with the user's machine to:
    - Read, write, and modify local files (`LocalFiles`).
    - Execute arbitrary shell commands (`LocalShell`).
    - Compile and run Java code within the host application's JVM (`RunningJVM`).
    - **Context-Aware File Operations:** The `LocalFiles` tool uses `FileInfo` POJOs with last modified timestamps to prevent concurrent modification conflicts.
- **Configuration-Driven:** The `GeminiConfig` system allows the client to be adapted to different environments by providing host-specific system instructions and toolsets.
- **Robust Session Management:** The `ContextManager` handles the conversation history and can save/load chat sessions to disk.

## 3. Architectural Breakdown

The project is logically divided into four main areas:

### a. Core Logic (`uno.anahata.gemini`)

This package contains the central orchestrators of the application.

| Class | Summary |
| :--- | :--- |
| **`GeminiChat.java`** | The main orchestrator. Manages the conversation loop, builds system instructions, handles API retries, and processes function calls/responses. |
| **`ContextManager.java`** | The state machine for the conversation. Manages the `List<ChatMessage>`, handles stateful resource replacement (e.g., file updates), and performs automatic context pruning. |
| **`GeminiConfig.java`** | Abstract base for host-specific configuration (API keys, working folder, function confirmation preferences). |
| **`GeminiAPI.java`** | Manages the Google GenAI client, handles API key pooling (round-robin), and model selection. |
| **`ChatMessage.java`** | The core data model for a single message, including content, usage metadata, and links between function calls and responses. |
| **`Executors.java`** | Static utility for providing a cached thread pool for asynchronous tasks. |

### b. Function & Tool System (`uno.anahata.gemini.functions`)

This is the system that grants the AI its advanced capabilities.

| Class/Package | Summary |
| :--- | :--- |
| **`FunctionManager.java`** | Discovers, registers, and executes local tools (`@AIToolMethod`). Generates the function schema for the Gemini API. |
| **`AIToolMethod.java`** | Annotation for defining a tool method, including its description and context behavior. |
| **`AIToolParam.java`** | Annotation for describing a tool method's parameter. |
| **`ContextBehavior.java`** | Enum defining how a tool's output affects the context (`EPHEMERAL` or `STATEFUL_REPLACE`). |
| **`JobInfo.java`** | POJO for tracking asynchronous tool execution results. |
| **`FailureTracker.java`** | Prevents the model from getting stuck in a loop of repeatedly failing tool calls. |
| **`pojos/FileInfo.java`** | POJO used by file-related tools, implementing `StatefulResource` to track file metadata. |
| **`pojos/ProposeChangeResult.java`** | POJO for the result of a user-approved file change, also implementing `StatefulResource`. |
| **`schema/GeminiSchemaGenerator.java`** | Generates the JSON schema for Java classes/methods using reflection and Swagger annotations. |
| **`spi/*`** | Package containing concrete tool implementations (`LocalFiles`, `RunningJVM`, `ContextWindow`, etc.). |

### c. UI Layer (`uno.anahata.gemini.ui`)

This package contains the entire Swing-based user interface.

| Class/Package | Summary |
| :--- | :--- |
| **`Main.java`** | **The standalone application entry point** (`main` method) for launching the client in a `JFrame`. |
| **`GeminiPanel.java`** | The main Swing component housing the entire chat interface, toolbar, and configuration tabs. |
| **`ChatPanel.java`** | The panel containing the message history display and the input area (now a multi-line `JTextArea`). |
| **`ContentRenderer.java`** | The master renderer that orchestrates the display of a `ChatMessage` by delegating to specific `PartRenderer` implementations. |
| **`render/*`** | Sub-package containing specific renderers for different `Part` types (Text, FunctionCall, Blob, etc.). |
| **`SwingGeminiConfig.java`** | Concrete `GeminiConfig` implementation for the Swing environment, including UI theme definitions. |

### d. Internal Utilities (`uno.anahata.gemini.internal`)

This package contains helper classes and custom serializers.

| Class | Summary |
| :--- | :--- |
| **`GsonUtils.java`** | Manages the Gson instance, including custom type adapters for `Optional` and pretty-printing JSON. |
| **`ContentAdapter.java`** | Custom Gson adapter for serializing/deserializing the Google GenAI SDK's `Content` object for session persistence. |
| **`PartAdapter.java`** | Custom Gson adapter for serializing/deserializing the Google GenAI SDK's `Part` object for session persistence. |
| **`FunctionUtils.java`** | Helpers for creating tool call fingerprints and extracting stateful resource IDs from tool responses. |
| **`PartUtils.java`** | Helpers for summarizing and converting `Part` objects (e.g., file to Blob). |

## 4. Current Status & Known Issues

- **Pruning Dependency Fix Implemented:** The critical issue where pruning a FunctionCall or FunctionResponse without its counterpart caused a 400 Bad Request error has been addressed. The `ContextManager` now automatically resolves and prunes the dependent call/response pair to maintain API protocol integrity.

## TODO - 2025-10-31 (Consolidated & Updated)

-   **Generic Tool Disabling:** Implement a mechanism in `FunctionManager` or `GeminiChat` to disable `@AIToolMethod`s if a `SystemInstructionProvider` already supplies its data (e.g., prevent `IDE.getAllIDEAlerts` from being offered if `IdeAlertsInstructionsProvider` is active and providing the same data).
-   **Kryo Session Serialization:** Investigate and implement session serialization using Kryo as a replacement for the current JSON-based approach. If successful, this could simplify the codebase by removing the need for custom GSON adapters (`ContentAdapter`, `PartAdapter`).
-   **Multi-Model Abstraction Layer:** Create a generic interface for chat models to allow plugging in other providers like OpenAI or Claude.
-   **GeminiPanel UI Enhancements:**
    -   Add a real-time status indicator to the UI showing:
        -   A running timer (in seconds) for the current API call round trip.
        -   A traffic light system for API call status: Green for "in-progress," Yellow for "retrying" (displaying the error and retry count), and Red for "failed" (displaying the final error and retry count).
        -   Display the total time of the last round trip upon completion or failure.
    -   **EDT Responsiveness:** Investigate and fix performance issues where the Swing Event Dispatch Thread (EDT) becomes unresponsive for long periods during model responses. This likely involves moving more processing off the EDT.

## Future UI Refactoring Plan (2025-11-01)

The following is a summary of a deferred plan to refactor the UI for better performance and usability:

1.  **`SystemInstructionsPanel` Overhaul**:
    *   **Problem**: The panel tries to render all provider content at once, causing major UI freezes.
    *   **Solution**: Refactor the layout into a `JSplitPane`.
        *   **Left Pane**: A navigation list of all `SystemInstructionProvider`s, showing their name, an on/off toggle, and the size of the content they produce.
        *   **Right Pane**: A viewer that renders the content of *only* the selected provider, eliminating the freeze.
    *   **Smart Rendering**: The right pane will intelligently reuse existing renderers (`FunctionResponsePartRenderer` for JSON, `TextPartRenderer` for markdown/text) to ensure proper formatting.

2.  **New `StatefulResourcesPanel`**:
    *   **Problem**: The current `StatefulResourcesProvider` produces a poorly formatted markdown table.
    *   **Solution**: Create a new, dedicated tab named `StatefulResourcesPanel`.
        *   This panel will feature a proper Swing `JTable` for a clean, sortable view of all stateful resources.
        *   The `StatefulResourcesProvider` will be changed to produce clean JSON for the model.

3.  **`ContextHeatmapPanel` Refactoring**:
    *   **Problem**: The current heatmap is not ideal for detailed analysis.
    *   **Solution**: Refactor the panel into a `JTable`-based view.
        *   It will list all context entries (messages) with sortable columns for ID, Role, Size/Token Count, and Content Summary.

4.  **Phase 2 - Visualizations**:
    *   Add a charting library (`JFreeChart` or `XChart`).
    *   Implement pie charts in the `SystemInstructionsPanel` (showing token contribution by provider) and `StatefulResourcesPanel` (showing file size distribution).
