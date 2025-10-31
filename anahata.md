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

- **`GeminiChat`**: The heart of the client. It manages the main conversation loop, constructs the system instructions, sends content to the Gemini API, and orchestrates the response processing, including the function-calling loop.
- **`ContextManager`**: The state machine for the conversation. It holds the `List<Content>` that represents the chat history and notifies listeners of any changes. It also manages stateful resources (like files) and handles the automatic pruning of dependent function calls/responses.
- **`GeminiAPI`**: Manages the connection details, including a round-robin system for using a pool of API keys.
- **`GeminiConfig`**: An abstract class that defines the contract for host-specific configurations, allowing the core logic to remain agnostic of its environment.

### b. Function & Tool System (`uno.anahata.gemini.functions`)

This is the system that grants the AI its advanced capabilities.

- **`FunctionManager`**: Discovers all public static methods annotated with `@AITool` from the classes in the `spi` package. It builds the `FunctionDeclaration` list for the API and processes `FunctionCall` responses by invoking the correct Java methods with the arguments provided by the model.
- **`spi` (Service Provider Interface)**: This sub-package contains the concrete implementations of the tools. Key classes include:
    - `LocalFiles`: For all file system operations.
    - `LocalShell`: For executing shell commands.
    - `RunningJVM`: For compiling and executing Java code on the fly.
    - `ContextWindow`: For managing the chat context itself (pruning, etc.).
- **`FunctionPrompter`**: An interface that decouples the function approval process from the UI. `SwingFunctionPrompter` is the implementation that shows the interactive confirmation dialog.

### c. UI Layer (`uno.anahata.gemini.ui`)

This package contains the entire Swing-based user interface.

- **`GeminiPanel`**: The main `JPanel` that houses the entire chat UI, including the toolbar, chat display area, and input field.
- **`render` (sub-package)**: A sophisticated rendering engine for displaying the chat history.
    - **`ContentRenderer`**: The master renderer that takes a `Content` object and orchestrates its display.
    - **`PartRenderer`**: An interface for components that can render a specific type of `Part` (e.g., text, function call). Implementations like `TextPartRenderer`, `FunctionCallPartRenderer`, and `FunctionResponsePartRenderer` build the specific Swing components for each part type.

### d. Internal Utilities (`uno.anahata.gemini.internal`)

This package contains helper classes and custom serializers.

- **`GsonUtils`**: A critical utility for handling JSON serialization and deserialization. It is responsible for pretty-printing JSON for display in the UI.
- **`ContentAdapter` & `PartAdapter`**: Custom GSON type adapters that ensure the Google GenAI library's core `Content` and `Part` objects are correctly serialized for session persistence.

## 4. Current Status & Known Issues

- **Pruning Dependency Fix Implemented:** The critical issue where pruning a FunctionCall or FunctionResponse without its counterpart caused a 400 Bad Request error has been addressed. The `ContextManager` now automatically resolves and prunes the dependent call/response pair to maintain API protocol integrity.

## TODO - 2025-10-31 (Consolidated)

-   **Generic Tool Disabling:** Implement a mechanism in `FunctionManager` or `GeminiChat` to disable `@AIToolMethod`s if a `SystemInstructionProvider` already supplies its data (e.g., prevent `IDE.getAllIDEAlerts` from being offered if `IdeAlertsInstructionsProvider` is active and providing the same data).
-   **Kryo Session Serialization:** Investigate and implement session serialization using Kryo as a replacement for the current JSON-based approach. If successful, this could simplify the codebase by removing the need for custom GSON adapters (`ContentAdapter`, `PartAdapter`).
-   **GeminiPanel UI Enhancements:**
    -   Replace the input `JTextField` with a `JTextArea` that supports multi-line input and uses Ctrl+Enter to send messages.
    -   Add a real-time status indicator to the UI showing:
        -   A running timer (in seconds) for the current API call round trip.
        -   A traffic light system for API call status: Green for "in-progress," Yellow for "retrying" (displaying the error and retry count), and Red for "failed" (displaying the final error and retry count).
        -   Display the total time of the last round trip upon completion or failure.
-   **EDT Responsiveness:** Investigate and fix performance issues where the Swing Event Dispatch Thread (EDT) becomes unresponsive for long periods during model responses. This likely involves moving more processing off the EDT.