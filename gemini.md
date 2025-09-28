# Project: gemini-java-client - Core Components Overview

This document summarizes the key Java classes and their responsibilities within the `gemini-java-client` project, based on the latest source code analysis.

## Core Functionality

The `gemini-java-client` project provides a robust framework for interacting with the Google Gemini API. It features a sophisticated function-calling mechanism, a Swing-based user interface, and a component-native rendering pipeline.

### 1. API Interaction and Configuration

*   **`GeminiAPI.java`**: Manages the connection to the Google Gemini API. It handles loading API keys from `gemini-api-keys.txt`, rotates through a pool of keys to manage quotas, and provides a `Client` instance for making API calls. It also allows for dynamic model selection (e.g., "gemini-2.5-flash", "gemini-2.5-pro").
*   **`GeminiConfig.java`**: An abstract base class for application-specific configurations. It defines the contract for providing the `GeminiAPI`, application instance ID, core system instructions, host-specific directives, and automatic function classes.
*   **`StandaloneSwingGeminiConfig.java`**: A concrete implementation of `GeminiConfig` tailored for standalone Swing applications.

### 2. Conversation and Context Management

*   **`GeminiChat.java`**: The central orchestrator of the conversation. It constructs the system instructions, manages the main request-response loop, and integrates the `FunctionManager2` to handle tool calls.
*   **`ContextManager.java`**: Manages the conversation history (`List<Content>`). It logs each entry to the `history` directory for persistence, tracks the total token count, and notifies listeners of changes.
*   **`ContextListener.java`**: An interface for components (like `GeminiPanel`) to react to context events (`contentAdded`, `contextCleared`, `contextModified`).
*   **`ContextWindow.java` (SPI)**: A core tool that provides functions for the AI to inspect and manage its own context, including pruning entries to manage token limits.

### 3. Function Calling (Tool Usage)

*   **`FunctionManager2.java`**: The primary manager for AI-executable functions. It discovers methods annotated with `@AITool`, builds `FunctionDeclaration` objects for the Gemini API, processes `FunctionCall` responses from the model, invokes the corresponding Java methods, and generates `FunctionResponse` objects.
*   **`AITool.java`**: An annotation used to mark methods and their parameters as AI-executable tools, providing the necessary descriptions for the model.
*   **`FunctionPrompter.java`**: An interface that decouples the function approval process from the core logic, allowing for different UI implementations.
*   **`RunningJVM.java` (SPI)**: A powerful tool that allows the AI to compile and execute Java source code dynamically within the application's JVM.
*   **`LocalFiles.java` (SPI)**: The primary tool for file system operations. It uses a `FileInfo` POJO to perform context-aware reads and writes, preventing race conditions by checking `lastModified` timestamps.
*   **`Patch.java` (SPI)**: A specialized tool for applying unified diff patches to files, ensuring atomicity and safety by using the `FileInfo` timestamp as a precondition.
*   **`LocalShell.java` (SPI)**: Provides a tool for executing shell commands and capturing their output.
*   **`Images.java` (SPI)**: A tool for generating images from a text prompt.

### 4. User Interface (Swing)

*   **`GeminiPanel.java`**: The main Swing UI component for the chat interface. It orchestrates the display of messages, handles user input, manages file attachments via drag-and-drop, and integrates with the `GeminiChat` lifecycle.
*   **`SwingFunctionPrompter.java`**: A Swing implementation of `FunctionPrompter` that displays a modal dialog for the user to confirm, deny, or set preferences for proposed function calls.
*   **`AttachmentsPanel.java`**: A `JPanel` for managing files attached by the user before sending a message.
*   **`UICapture.java`**: A utility for capturing screenshots of the desktop and all visible JFrames within the application.

### 5. Component-Based Rendering Pipeline

The rendering pipeline uses a pure-Swing, component-based approach for robust and predictable layouts.

*   **`ComponentContentRenderer2.java`**: The core renderer for `Content` objects. It builds a hierarchy of standard Swing components (`JPanel`, `JLabel`, etc.) and delegates the rendering of individual `Part` objects to specialized `PartRenderer`s.
*   **`PartRenderer.java`**: An interface for rendering a `Part` into a `JComponent`.
*   **`TextPartRenderer.java`**: Renders text, parsing Markdown and creating a mix of `JEditorPane`s for styled text and dedicated components for code blocks.
*   **`CodeBlockRenderer.java`**: Creates syntax-highlighted `JEditorPane`s for code, using an `EditorKitProvider` to get the appropriate syntax kit.
*   **`FunctionCallPartRenderer.java`**: Renders `FunctionCall` objects into a structured `JPanel` with collapsible sections for each argument.
*   **`FunctionResponsePartRenderer.java`**: Renders `FunctionResponse` objects into a collapsible `JPanel`, with distinct styles for success and error states.
*   **`InteractiveFunctionCallRenderer.java`**: A decorator for `FunctionCallPartRenderer` that adds the interactive Yes/No/Always/Never buttons used in the `SwingFunctionPrompter`.

## Interactions

1.  The `GeminiPanel` captures user input and sends it to the `GeminiChat`.
2.  `GeminiChat` adds the user's message to the `ContextManager` and sends the entire context to the `GeminiAPI`.
3.  If the model returns a `FunctionCall`, `GeminiChat` passes it to `FunctionManager2`.
4.  `FunctionManager2` uses `SwingFunctionPrompter` to get user approval.
5.  For approved calls, `FunctionManager2` invokes the appropriate static method in the SPI packages (e.g., `LocalFiles.readFile`).
6.  The result is wrapped in a `FunctionResponse`, added to the `ContextManager`, and the loop continues by sending the updated context back to the model.
7.  When the model returns a final text response, `GeminiPanel` (acting as a `ContextListener`) is notified and uses the `ComponentContentRenderer2` pipeline to render the new `Content` object and add it to the display.
