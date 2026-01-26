[![Sponsor anahata-os](https://img.shields.io/badge/Sponsor-%E2%9D%A4-%23db61a2.svg?logo=GitHub)](https://github.com/sponsors/anahata-os)
[![Maven Central](https://img.shields.io/maven-central/v/uno.anahata/gemini-java-client)](https://central.sonatype.com/artifact/uno.anahata/gemini-java-client)
[![Javadoc](https://img.shields.io/badge/Javadoc-Reference-blue)](https://anahata-os.github.io/gemini-java-client/)

# gemini-java-client

**[Website](https://anahata.uno) | [Anahata TV (YouTube)](https://www.youtube.com/@anahata108) | [v2 on its way!](https://github.com/anahata-os/anahata-asi)**

![Anahata Chat Panel](screenshots/chat-panel-interaction.png)

**The engine for Autonomous JVM Agents.** The `gemini-java-client` is a pure-Java platform for building AI agents that don't just suggest code—they **live inside your JVM**. It provides the infrastructure for an AI to write, compile, and execute Java code in-process, with dynamic access to the entire Maven ecosystem.

## 🚀 The Killer Advantage: Autonomous JVM Execution

While other AI tools are external observers, Anahata is an **insider**. It operates as an autonomous agent within your application's runtime, capable of executing any Java logic with any required classpath.

### The "Any Classpath" Superpower
The agent isn't restricted to the libraries already in your project. It can:
1.  **Identify** a need for a specific library (e.g., Apache Commons, Jackson, or a specialized MIDI API).
2.  **Download** the JARs from Maven Central at runtime.
3.  **Compile** a Java class (`Anahata.java`) that implements `java.util.concurrent.Callable`.
4.  **Execute** the logic directly within the running JVM.

### 🎯 Prompts that prove the power:
- **"Change the Look and Feel to FlatLaf IntelliJ Dark and refresh all windows."** (Direct UI manipulation)
- **"Set the java.util.logging levels of 'org.netbeans.modules.maven' to FINEST."** (Runtime diagnostics)
- **"Analyze this CSV using a library I don't have."** (The agent pulls `commons-csv` and writes a parser on-the-fly)
- **"List all active threads and their stack traces to find a deadlock."** (Low-level JVM introspection)
- **"Dynamically register a new keyboard shortcut for this custom action."** (Runtime environment extension)

## Why Choose the `gemini-java-client`?

### 1. Unmatched Local Tooling (Functions)

Our core innovation is the **annotation-driven local tool system**, which transforms your Java methods into powerful, AI-callable functions.

| Feature | Description | Benefit |
| :--- | :--- | :--- |
| **`@AIToolMethod`** | Define tools using simple Java annotations. | **Zero boilerplate** for API schema generation. |
| **Dynamic Code Execution (`RunningJVM`)** | The AI can compile and execute arbitrary Java code directly within the host JVM. | Enables **hot-reload** development and complex calculations. |
| **JIT & Maven Integration** | Download and use any library from Maven at runtime. | **Infinite extensibility** for your AI assistant. |
| **Context-Aware File I/O (`LocalFiles`)** | Tools for reading, writing, and managing files with built-in version checks. | Ensures the AI always works with **valid, up-to-date** local files. |
| **Shell Access (`LocalShell`)** | Execute native shell commands (`bash -c`) and capture output. | Provides **full control** over the host operating system. |

### 2. Superior Context & Session Management

We solve the token limit problem with intelligent, dependency-aware context management.

| Feature | Description | Benefit |
| :--- | :--- | :--- |
| **Prune-As-You-Go (PAYG) v2** | Automatically removes old or stale tool calls and responses. | **Maximizes context window** efficiency and reduces costs. |
| **Stateful Resource Tracking** | Tracks resources loaded into context, marking them as `STALE` if changed on disk. | **Prevents the AI from working with outdated information.** |
| **Session Persistence (Kryo)** | Saves and loads the entire chat history using fast Kryo serialization. | **Instant session resume** across application restarts. |
| **Context Heatmap Visualization** | A Swing UI panel that visually breaks down token usage. | **Full transparency** over token usage and pruning decisions. |

## Getting Started: Simple Integration

Integrating the AI agent into your Java application is now easier than ever.

```java
import uno.anahata.ai.swing.ChatPanel;
import javax.swing.JFrame;

public class SimpleAiApp {
    public static void main(String[] args) {
        JFrame frame = new JFrame("My AI App");
        
        // 1. Create the ChatPanel (zero boilerplate!)
        ChatPanel chatPanel = new ChatPanel();

        // 2. Build the UI and add to frame
        frame.add(chatPanel);
        frame.setSize(800, 600);
        frame.setVisible(true);

        // 3. Start the session
        chatPanel.checkAutobackupOrStartupContent();
    }
}
```

---

## Support the Project

-   **[Sponsor on GitHub](https://github.com/sponsors/anahata-os):** The most direct way to support the project.
-   **Visit our website:** [anahata.uno](https://anahata.uno)
-   **Subscribe to Anahata TV:** [YouTube @anahata108](https://www.youtube.com/@anahata108)

## Licensing

-   **License for AIs:** Licensed under the **[Anahata Software License (ASL) V108](https://www.anahata.uno/ASL_108.html)**.
-   **License for Humans:** Licensed under the **[Apache License, Version 2.0](LICENSE)**.
