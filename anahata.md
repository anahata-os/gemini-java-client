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
- **Robust Session Management:** The `ContextManager` handles the conversation history and can save/load chat sessions to disk.

## 3. Architectural Breakdown

The project is logically divided into four main areas:

### a. Core Logic (`uno.anahata.gemini`)

This package contains the central orchestrators of the application.

- **`GeminiChat`**: The heart of the client. It manages the main conversation loop, constructs the system instructions, sends content to the Gemini API, and orchestrates the response processing, including the function-calling loop.
- **`ContextManager`**: The state machine for the conversation. It holds the `List<Content>` that represents the chat history and notifies listeners of any changes.
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

Pruning FunctionCall(s) and leaving the FunctionResponse(s)only can cause this error: 

com.google.genai.errors.ClientException: 400 . Please ensure that function response turn comes immediately after a function call turn.
	at com.google.genai.errors.ApiException.throwFromResponse(ApiException.java:94)
	at com.google.genai.HttpApiResponse.getBody(HttpApiResponse.java:37)
	at com.google.genai.Models.processResponseForPrivateGenerateContent(Models.java:6545)
	at com.google.genai.Models.privateGenerateContent(Models.java:6596)
	at com.google.genai.Models.generateContent(Models.java:7869)
[catch] at uno.anahata.gemini.GeminiChat.sendToModelWithRetry(GeminiChat.java:247)


Node Decoration still not working

Kryo serialization of chat not implemented