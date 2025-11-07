[![Sponsor anahata-os](https://img.shields.io/badge/Sponsor-%E2%9D%A4-%23db61a2.svg?logo=GitHub)](https://github.com/sponsors/anahata-os)

# Gemini Java Client

A pure Java client for Google's Gemini API, designed for deep integration into Java applications.

This library provides a robust and extensible foundation for interacting with Google's Gemini models. It's not just a simple API wrapper; it includes a powerful, annotation-driven system for creating and managing local tools (functions) that the Gemini model can invoke. This allows you to build AI assistants that can interact directly with your application's logic, the local file system, or any other resource accessible from the JVM.

The client also features a built-in Swing UI (`GeminiPanel`) that can be easily embedded into any desktop application, providing a complete chat interface out of the box.

## Key Features

-   **Pure Java:** Ensures high portability across different environments.
-   **Embeddable Swing UI:** A complete, renderer-based UI (`uno.anahata.gemini.ui.GeminiPanel`) is provided for displaying complex chat interactions, including text, images, and interactive tool calls.
-   **Powerful Local Tool Framework:**
    -   Define tools using simple Java methods annotated with `@AIToolMethod`.
    -   Automatic generation of function declarations (JSON schema) for the Gemini API.
    -   The model can call your local Java code, enabling deep application integration with tools like `LocalFiles`, `LocalShell`, and `RunningJVM`.
-   **Robust Session Management:** Includes context management with automatic pruning and session persistence using the Kryo serialization library.
-   **Dynamic System Instructions:** A provider-based system allows for dynamically injecting context-aware instructions into the model's prompt.
-   **Multimodality:** Send text, images, and other file types to the model.

## Architectural Documentation

For a detailed breakdown of the project's architecture, please refer to the `package-info.java` files located within each package. They serve as the definitive source for architectural documentation.

## Getting Started

The standalone client can be launched by running the `main` method in `uno.anahata.gemini.ui.Main.java`.

For programmatic use, here is a basic setup:

```java
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.ui.SwingGeminiConfig;
import uno.anahata.gemini.ui.SwingFunctionPrompter;
import uno.anahata.gemini.ui.render.editorkit.DefaultEditorKitProvider;
import uno.anahata.gemini.functions.FunctionPrompter;

// 1. Configure your API key and other settings
//    (Ensure gemini-api-keys.txt is in your working directory)
SwingGeminiConfig config = new SwingGeminiConfig();

// 2. Initialize the chat with a FunctionPrompter (e.g., a Swing one)
//    Note: Tools are automatically discovered via the SPI and config.getAutomaticFunctionClasses()
FunctionPrompter prompter = new SwingFunctionPrompter(null, new DefaultEditorKitProvider());
GeminiChat chat = new GeminiChat(config, prompter);

// 3. Start a conversation
chat.sendText("Hello! Can you use your tools to tell me the current time?");
// The response will be processed asynchronously and added to the chat context.
```

## Support the Project

This project is the result of countless hours of passion and dedication. If you find it valuable, please consider supporting its continued development.

-   **[Sponsor on GitHub](https://github.com/sponsors/anahata-os):** The most direct way to support the project.
-   **Commercial Licensing:** If you're using this in a commercial product, this is the required path.

## Licensing: Open Core Model

`gemini-java-client` is available under a dual-license model to accommodate both open-source and commercial needs.

-   **Open Source:** For use in open-source projects, the client is licensed under the **GNU Affero General Public License v3 (AGPLv3)**. See the [LICENSE](LICENSE) file for the full license text.

-   **Commercial Use:** For use in proprietary, closed-source applications, a **commercial license is required**. This allows you to integrate the power of the `gemini-java-client` into your commercial products without the obligation to open-source your own code. Please see the [COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md) file for more information.
