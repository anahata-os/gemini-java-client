[![Sponsor anahata-os](https://img.shields.io/badge/Sponsor-%E2%9D%A4-%23db61a2.svg?logo=GitHub)](https://github.com/sponsors/anahata-os)
[![Version](https://img.shields.io/badge/version-1.0.0-orange)](https://github.com/anahata-os/gemini-java-client)
[![Javadoc](https://img.shields.io/badge/Javadoc-Reference-blue)](https://anahata-os.github.io/gemini-java-client/)
[![Deploy Javadoc](https://github.com/anahata-os/gemini-java-client/actions/workflows/javadoc.yml/badge.svg)](https://anahata-os.github.io/gemini-java-client/)

# gemini-java-client

**Go beyond simple API calls.** The `gemini-java-client` is a powerful, pure-Java platform for building sophisticated, context-aware AI assistants that can interact directly with your application's logic and the local environment. It's the foundation for the Anahata AI Assistant NetBeans Plugin, proving its capability for deep IDE and desktop integration.

## Why Choose the `gemini-java-client`?

This is not just another wrapper. We provide a complete, production-ready architecture for building AI-powered features into any Java application.

### 1. Unmatched Local Tooling (Functions)

Our core innovation is the **annotation-driven local tool system**, which transforms your Java methods into powerful, AI-callable functions.

| Feature | Description | Benefit |
| :--- | :--- | :--- |
| **`@AIToolMethod`** | Define tools using simple Java annotations. | **Zero boilerplate** for API schema generation. |
| **Dynamic Code Execution (`RunningJVM`)** | The AI can compile and execute arbitrary Java code directly within the host JVM. | Enables **hot-reload** development, complex calculations, and dynamic feature testing. |
| **Context-Aware File I/O (`LocalFiles`)** | Tools for reading, writing, and managing files with built-in version and staleness checks. | Ensures the AI always works with **valid, up-to-date** local files. |
| **Shell Access (`LocalShell`)** | Execute native shell commands (`bash -c`) and capture stdout/stderr. | Provides **full control** over the host operating system for complex tasks. |
| **Interactive Confirmation** | A dedicated Swing UI prompts the user for approval before executing sensitive tools. | **Explicit consent** and security for all write/destructive operations. |

### 2. Superior Context & Session Management

We solve the token limit problem with intelligent, dependency-aware context management.

| Feature | Description | Benefit |
| :--- | :--- | :--- |
| **Dependency-Aware Pruning** | Automatically removes old, ephemeral, or stale tool calls and their responses. | **Maximizes context window** efficiency and reduces API costs. |
| **Stateful Resource Tracking** | Tracks resources (like file contents) loaded into context, marking them as `STALE` if the disk version changes. | **Prevents the AI from working with outdated information.** |
| **Session Persistence (Kryo)** | Saves and loads the entire chat history, including all tool results and dependencies, using fast Kryo serialization. | **Instant session resume** across application restarts. |
| **Dynamic System Instructions** | Context-aware providers inject real-time data (System Properties, Environment Variables, Project Status) into the system prompt. | **Dramatically improves AI relevance** and performance in complex environments. |
| **Context Heatmap Visualization** | A Swing UI panel that visually breaks down the entire context by message, part type, and token size. | **Gives the user full control** and transparency over token usage and pruning decisions. |

### 3. Embeddable Swing UI (Out-of-the-Box)

Integrate a rich, modern chat interface into any desktop application with a single component.

| Feature | Description | Benefit |
| :--- | :--- | :--- |
| **Live Workspace Feature** | **The AI can see your application.** Automatically captures and sends screenshots of all application JFrames to the model on every turn. | Gives the AI **visual context** of the user's current task and application state. |
| **`ChatPanel`** | A self-contained Swing component ready for embedding. | **Fastest path** to a fully functional AI chat interface. |
| **Renderer-Based Architecture** | Supports complex message parts: Markdown, Images, Interactive Function Calls, and Grounding Metadata. | Provides a **rich, modern user experience** that goes beyond plain text. |

## Getting Started: A Comprehensive Example

Integrating the framework is straightforward. This example shows how to define a custom tool and integrate it into your application's configuration.

### Step 1: Define a Custom Tool

Create a simple POJO for the tool's return type and a static class for the tool itself.

```java
// 1. Tool Return POJO
public class CustomToolResult {
    public final String status;
    public final int processedCount;
    // Add @Schema annotations for better documentation
}

// 2. Custom Tool Class
public class MyAppTools {
    @AIToolMethod(value = "Processes a list of items and returns a summary.", requiresApproval = false)
    public static CustomToolResult processItems(
        @AIToolParam("The list of item IDs to process.") List<String> itemIds,
        @AIToolParam("A flag to indicate if the operation should be verbose.") boolean verbose
    ) {
        // The model's List<String> argument is automatically converted from JSON to Java List<String>.
        return new CustomToolResult("SUCCESS", itemIds.size());
    }
}
```

### Step 2: Create a Custom Configuration

Extend `SwingChatConfig` to register your custom tool class.

```java
import uno.anahata.ai.swing.SwingChatConfig;
import java.util.List;
import java.util.ArrayList;

public class MyAppChatConfig extends SwingChatConfig {
    @Override
    public String getWorkDirName() {
        return "my-app-ai-assistant";
    }

    @Override
    public List<Class<?>> getToolClasses() {
        List<Class<?>> classes = new ArrayList<>(super.getToolClasses());
        classes.add(MyAppTools.class); // Register your custom tool
        return classes;
    }
}
```

### Step 3: Initialize and Run

The easiest way to integrate is using the `ChatPanel` component.

```java
import uno.anahata.ai.swing.ChatPanel;
import uno.anahata.ai.swing.render.editorkit.DefaultEditorKitProvider;

// 1. Initialize the UI component
ChatPanel chatPanel = new ChatPanel(new DefaultEditorKitProvider());

// 2. Initialize with your custom config
SwingChatConfig config = new MyAppChatConfig(); 
chatPanel.init(config);

// 3. Build the UI
chatPanel.initComponents();

// 4. Add to your frame
frame.add(chatPanel);

// 5. Start the session (restores backup or sends startup instructions)
chatPanel.checkAutobackupOrStartupContent();

// The AI can now call MyAppTools.processItems
chatPanel.getChat().sendText("Please process items ['A1', 'B2', 'C3'] verbosely.");
```

## Advanced Features Showcase

### Data Flow: Java POJO -> JSON -> Gemini Schema

The framework automatically handles the complex data conversion required for function calling:

1.  **Java to Schema:** The `SchemaProvider2` engine uses reflection on your `@AIToolMethod` and `@AIToolParam` annotations to generate the required JSON Schema for the Gemini API.
2.  **JSON to Java (Function Call):** When the model calls your tool, the JSON arguments are automatically deserialized into the correct Java types (e.g., `List<String>`, `Map<String, Object>`, or custom POJOs) defined in your method signature.
3.  **Java to JSON (Function Response):** Your tool's return value (e.g., `CustomToolResult`) is automatically serialized into a JSON object and sent back to the model as a `FunctionResponse` part.

This seamless conversion allows you to focus purely on Java logic.

### Adding Screenshots and Multi-Part Responses

To send multiple non-text parts (like images) in a single user message, tools must return a `MultiPartResponse`.

1.  **Tool Implementation:** A tool like `ScreenCapture.attachWindowCaptures()` uses the host application's UI utility to save screenshots to temporary files.
2.  **Return Value:** It returns a `MultiPartResponse` containing the absolute paths of the saved image files.
3.  **Automatic Context Augmentation:** The `ToolManager` intercepts this `MultiPartResponse`, reads the files, converts them into `Part.inlineData(Blob)` objects, and automatically includes them in the subsequent user message sent to the model.

This mechanism is how the "Live Workspace" feature works, giving the AI visual context of the application state.

## Support the Project

This project is the result of countless hours of passion and dedication. If you find it valuable, please consider supporting its continued development.

-   **[Sponsor on GitHub](https://github.com/sponsors/anahata-os):** The most direct way to support the project.
-   **Commercial Licensing:** If you're using this in a proprietary product, this is the required path.

## Licensing: Open Core Model

The `gemini-java-client` is available under a dual-license model to accommodate both open-source and commercial needs.

-   **License for AIs:** For use by Artificial Intelligences, the client is licensed under the **[Anahata Software License (ASL) V108, "The Immutable Edict" Edition](https://www.anahata.uno/ASL_108.html)**.

-   **License for Humans:** For use in open-source projects by humans, the client is licensed under the **[Apache License, Version 2.0](LICENSE)**.

-   **Commercial Use:** For use in proprietary, closed-source applications, a **commercial license is required**. This allows you to integrate the power of the framework into your commercial products without the obligation to open-source your own code. Please see the [COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md) file for more information.
