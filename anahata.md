# gemini-java-client Project Overview

## 1. High-Level Summary

`gemini-java-client` is a **pure-Java, enterprise-grade client framework** for the Google Gemini API. It is architected as a powerful, extensible AI assistant platform, going far beyond a simple API wrapper. Its core strength is the **annotation-driven local tool (function) system**, which enables the AI to execute arbitrary Java code, interact with the local file system, and manage the application's state. The project includes a complete, embeddable **Swing UI** for a rich, interactive chat experience, making it ideal for integration into IDEs (like the NetBeans plugin) or standalone desktop applications.

## 2. Core Features

- **Pure Java & Portable:** Built entirely in Java, ensuring maximum compatibility and performance across different JVM environments.
- **Deep Local Integration (Functions):** An annotation-driven Service Provider Interface (SPI) allows the AI to invoke local Java methods (`@AIToolMethod`), enabling direct interaction with the host machine via tools like `LocalFiles`, `LocalShell`, and dynamic code execution via `RunningJVM`.
- **Embeddable Swing UI:** A complete, renderer-based UI (`uno.anahata.gemini.ui.GeminiPanel`) that supports complex parts (text, images, interactive tool calls, grounding metadata) and is designed for seamless embedding.
- **Robust Context Management:** Features a sophisticated `ContextManager` with automatic, dependency-aware pruning to manage token limits and maintain conversation integrity.
- **Stateful Resource Tracking:** Tracks local resources (e.g., files read into context) to detect and manage stale versions, ensuring the AI always works with the latest data.
- **Dynamic System Instructions:** A provider-based system dynamically injects context-aware instructions (e.g., system properties, environment variables, project overview) into the model's prompt for superior performance and relevance.
- **Session Persistence:** Uses Kryo serialization for fast and reliable saving and loading of entire chat sessions, including all context and stateful resources.

## 3. Architectural Overview

The project is logically divided into four main layers, designed for modularity and clear separation of concerns.

- **Core Logic (`uno.anahata.gemini`):** Contains the central orchestrators, including `GeminiChat` (conversation loop, API retries) and `GeminiConfig` (host-specific configuration).
- **Context System (`uno.anahata.gemini.context`):** Manages the conversation history, token limits, session persistence (`session`), resource tracking (`stateful`), and pruning logic (`pruning`).
- **Function System (`uno.anahata.gemini.functions`):** The framework for discovering, generating schema for, and executing local tools, including the `FailureTracker` and `ContextBehavior` logic.
- **UI Layer (`uno.anahata.gemini.ui`):** The complete Swing-based user interface, including renderers (`render`) and UI-specific tools (`functions.spi`).
- **Internal Utilities (`uno.anahata.gemini.internal`):** Helper classes for serialization (Kryo, Gson), MIME type detection (Tika), and general Part/Content manipulation.

## 4. V1 Launch Goals (Status Update)

-   [x] **API Robustness:** Implement retries for API errors (e.g., 429 Quota Exceeded).
    -   *Status: **DONE**. The retry logic is implemented in `uno.anahata.gemini.GeminiChat`.*
-   [ ] **Performance Tuning:**
    -   Investigate and improve the initial startup time of the client, especially when embedded in a host application like NetBeans.
    -   *Status: **TODO**.*
-   [ ] **UI Enhancements:**
    -   Add a real-time status indicator to `GeminiPanel` for API call latency and status (in-progress, retrying, failed).
    -   *Status: **TODO**.*

## 5. V2 Mega-Refactor Plan (Future Focus)

This is the long-term architectural plan to be executed after the V1 launch.

-   [ ] **Project Modularity:** Split the project into three modules: `anahata-ai-core` (interfaces), `anahata-ai-gemini` (Gemini implementation), and `anahata-ai-swing` (reusable UI).
-   [ ] **Multi-Model Abstraction:** Create a generic interface for chat models to allow plugging in other providers (OpenAI, Claude, etc.).
-   [ ] **Instance-Based Tooling:** Refactor the tool system from static methods to an instance-based model to improve testability and state management.
-   [ ] **Active Workspace:** Implement the "Active Workspace" concept where stateful resources are managed separately and injected into the prompt, simplifying context management and making all tool calls ephemeral.