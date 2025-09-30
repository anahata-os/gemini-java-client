[![Sponsor anahata-os](https://img.shields.io/badge/Sponsor-%E2%9D%A4-%23db61a2.svg?logo=GitHub)](https://github.com/sponsors/anahata-os)

# Gemini Java Client

A pure Java client for Google's Gemini API, designed for deep integration into Java applications.

This library provides a robust and extensible foundation for interacting with Google's Gemini models. It's not just a simple API wrapper; it includes a powerful, annotation-driven system for creating and managing local tools (functions) that the Gemini model can invoke. This allows you to build AI assistants that can interact directly with your application's logic, the local file system, or any other resource accessible from the JVM.

The client also features a built-in Swing UI (`GeminiPanel`) that can be easily embedded into any desktop application, providing a complete chat interface out of the the box.

## Features

- **Fluent Gemini API:** A clean, modern interface for interacting with the Gemini API.
- **Powerful Local Tool Framework:**
    - Define tools using simple Java methods annotated with `@AITool`.
    - Automatic generation of function declarations (JSON schema) for the Gemini API.
    - The model can call your local Java code, enabling deep application integration.
- **Built-in Swing UI (`GeminiPanel`):**
    - A complete, embeddable chat component.
    - Support for streaming responses, file attachments, and conversation history.
    - Automatic rendering of tool calls and their results.
    - Syntax highlighting for code snippets.
- **Automatic Context Management:** Strategies for automatically pruning the conversation history to stay within the model's token limit.
- **Multimodality:** Send text, images, and other file types to the model.
- **Extensible:** Designed to be easily extended and customized for specific host applications.

## How It Works

The core of the client is the `GeminiChat` class, which manages the conversation with the Gemini API. The real power, however, comes from the `FunctionManager` and the `@AITool` annotation.

1.  **Tool Discovery:** When you initialize the `FunctionManager`, you provide it with instances of classes containing methods annotated with `@AITool`.
2.  **Schema Generation:** The manager uses reflection to inspect these methods and automatically generates the JSON schema that the Gemini API requires to understand what the function does, what parameters it accepts, and what it returns.
3.  **Function Calling:** When the Gemini model decides to call one of your tools, the `GeminiChat` class intercepts the `FunctionCall` from the API response.
4.  **Local Execution:** It then invokes the corresponding Java method in your tool class, passing the arguments provided by the model.
5.  **Response:** The return value of your Java method is wrapped in a `FunctionResponse` and sent back to the model, completing the loop.

This architecture allows you to create sophisticated AI agents that can reason about problems and then use your application's own logic to solve them.

## Getting Started

*(Full usage examples and Maven dependency information to be added).*

### Basic Example (without UI)

```java
// 1. Configure your API key and other settings
GeminiConfig config = new StandaloneSwingGeminiConfig();

// 2. Create an instance of your tool class
MyApplicationTools myTools = new MyApplicationTools();

// 3. Initialize the chat
GeminiChat chat = new GeminiChat(config);
chat.getFunctionManager().addTool(myTools); // Register your tools

// 4. Start a conversation
String response = chat.send("Hello! Can you use your tools to tell me the current time?");
System.out.println(response);
```

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
