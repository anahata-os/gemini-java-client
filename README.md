# Gemini Java Client

A robust and extensible Java client for interacting with the Google Gemini API, designed to empower your applications with advanced AI capabilities, including powerful tool-calling features.

## Features

### 1. Seamless Gemini API Integration
- **Flexible API Key Management**: Securely load and manage multiple Gemini API keys from a `gemini-api-keys.txt` file, with built-in round-robin rotation for efficient quota usage.
- **Model ID Configuration**: Easily switch between different Gemini models (e.g., `gemini-2.5-flash`, `gemini-2.5-pro`) to suit your application's needs.
- **Robust API Communication**: Handles API requests with retry mechanisms, exponential backoff, and jitter to ensure reliable communication even under high load or temporary network issues.

### 2. Advanced Conversation Management
- **Context Preservation**: Automatically manages the conversation history (context) to ensure the AI maintains awareness across turns.
- **Token Management**: Provides tools to monitor and manage the context window's token count, allowing for efficient resource usage and preventing context overflow.
- **Session Persistence**: Save and load entire chat sessions, enabling long-running conversations and continuity across application restarts.
- **Detailed History Logging**: All conversation entries are logged to files in a `history` directory, providing a comprehensive audit trail for debugging and analysis.

### 3. Powerful Tool Calling Framework
The `gemini-java-client` excels in its ability to integrate custom Java functions as "tools" that the Gemini model can invoke. This allows your AI to interact with the local environment and extend its capabilities dynamically.

- **`@AITool` Annotation**: Easily mark any static Java method as an AI-callable tool, complete with descriptions for the model.
- **Automatic Schema Generation**: Dynamically generates precise JSON schemas for your tools, including parameter types, descriptions, and validation rules (e.g., `@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern`), ensuring the Gemini model understands how to correctly call your functions.
- **User Approval Mechanism**: For sensitive operations, tool calls can be configured to require explicit user confirmation, providing a crucial layer of security and control.
- **Extensible Tool Providers**: The library includes a set of powerful built-in tools:
    - **`LocalFiles`**: Comprehensive file system operations (read, write, append, delete, move, copy, create directories, list contents, check existence) with safety features like `lastModified` preconditions to prevent data corruption.
    - **`LocalShell`**: Execute arbitrary shell commands (`bash -c`) and capture their output, enabling interaction with the operating system.
    - **`RunningJVM`**: Compile and execute Java code dynamically within the running JVM, allowing the AI to extend its own capabilities at runtime.
    - **`Images`**: Generate images based on text prompts.
    - **`Patch`**: Apply unified diff patches to files for precise, non-destructive code modifications.
    - **`ContextWindow`**: Tools for the AI to manage its own context window, including pruning irrelevant entries to optimize token usage.
    - **`Session`**: Tools for saving, loading, and listing chat sessions.

### 4. Extensible Architecture
- **`GeminiConfig`**: An abstract configuration class allowing easy customization for different host environments (e.g., desktop applications, web services).
- **`ContextListener`**: An interface for observing changes in the conversation context, useful for updating UI components or triggering other application logic.

## Getting Started

(Detailed instructions on how to include the library in a Maven/Gradle project, initialize `GeminiConfig`, `GeminiAPI`, and `GeminiChat`, and define custom tools would go here.)

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.
