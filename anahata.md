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
- **Configuration-Driven:** The `GeminiConfig` system allows the client to be adapted to different environments by providing host-specific system instructions and toolsets.
- **Robust Session Management:** The `ContextManager` handles the conversation history. Session persistence is managed by `SessionManager` using the efficient Kryo serialization library.

## 3. Architectural Breakdown

The project is logically divided into four main areas:

### a. Core Logic (`uno.anahata.gemini`)

This package contains the central orchestrators of the application.

| Class | Summary |
| :--- | :--- |
| **`GeminiChat.java`** | The main orchestrator. Manages the conversation loop, builds system instructions, handles API retries, and processes function calls/responses. |
| **`ContextManager.java`** | The state machine for the conversation. Manages the `List<ChatMessage>`, handles stateful resource replacement, and performs automatic context pruning. |
| **`GeminiConfig.java`** | Abstract base for host-specific configuration (API keys, working folder, function confirmation preferences). |
| **`GeminiAPI.java`** | Manages the Google GenAI client, handles API key pooling (round-robin), and model selection. |

### b. Function & Tool System (`uno.anahata.gemini.functions`)

This is the system that grants the AI its advanced capabilities.

| Class/Package | Summary |
| :--- | :--- |
| **`FunctionManager.java`** | Discovers, registers, and executes local tools (`@AIToolMethod`). Generates the function schema for the Gemini API. |
| **`AIToolMethod.java`** | Annotation for defining a tool method, including its description and context behavior. |
| **`ContextBehavior.java`** | Enum defining how a tool's output affects the context (`EPHEMERAL` or `STATEFUL_REPLACE`). |
| **`FailureTracker.java`** | Prevents the model from getting stuck in a loop of repeatedly failing tool calls. |
| **`schema/GeminiSchemaGenerator.java`** | Generates the JSON schema for Java classes/methods using reflection. |
| **`spi/*`** | Package containing concrete, generic tool implementations (`LocalFiles`, `RunningJVM`, `ContextWindow`, etc.). |

### c. UI Layer (`uno.anahata.gemini.ui`)

This package contains the entire Swing-based user interface.

| Class/Package | Summary |
| :--- | :--- |
| **`Main.java`** | **The standalone application entry point** (`main` method) for launching the client in a `JFrame`. |
| **`GeminiPanel.java`** | The main Swing component housing the entire chat interface, toolbar, and configuration tabs. |
| **`ContentRenderer.java`** | The master renderer that orchestrates the display of a `ChatMessage` by delegating to specific `PartRenderer` implementations. |
| **`render/*`** | Sub-package containing specific renderers for different `Part` types (Text, FunctionCall, Blob, etc.). |

### d. Internal Utilities (`uno.anahata.gemini.internal`)

This package contains helper classes and custom serializers.

| Class | Summary |
| :--- | :--- |
| **`KryoUtils.java`** | Manages the Kryo instance and registration of serializers for session persistence. |
| **`GsonUtils.java`** | Manages the Gson instance for handling JSON in tool calls, not for session persistence. |
| **`PartUtils.java`** | Helpers for summarizing and converting `Part` objects (e.g., file to Blob). |

## V1 Launch Goals (Immediate Focus)

-   **API Robustness:** Implement additional retries for API errors (e.g., 429 Quota Exceeded), increasing the wait time exponentially but not stopping the process entirely.
-   **Performance:**
    -   Investigate and improve the initial startup time of the `AnahataTopComponent` in the NetBeans host.
-   **GeminiPanel UI Enhancements:**
    -   Add a real-time status indicator to the UI showing:
        -   A running timer (in seconds) for the current API call round trip.
        -   A traffic light system for API call status: Green for "in-progress," Yellow for "retrying," and Red for "failed."
        -   Display the total time of the last round trip upon completion or failure.

## V2 Mega-Refactor Plan (Future Focus)

This is the long-term architectural plan to be executed after V1 launch.

1.  **Project Modularity:** Split the project into three modules: `anahata-ai` (core interfaces), `anahata-ai-gemini` (Gemini implementation), and `anahata-ai-swing` (reusable UI).
2.  **Multi-Model Abstraction Layer:** Create a generic interface for chat models to allow plugging in other providers like OpenAI or Claude.
3.  **Tooling Model Refactor:** Migrate from static tool methods to an instance-based model for better testability and state management.
4.  **Active Workspace Implementation:** Implement the "Active Workspace" concept where stateful resources (like files) are automatically injected into the user prompt on every turn. This will allow `LocalFiles.readFile` to simply add the file to the workspace, eliminating the current context bloat where `writeFile` keeps the file content twice in the context (FunctionCall and FunctionResponse).
