# gemini-java-client Project Overview

## 1. High-Level Summary

`gemini-java-client` is a comprehensive, pure-Java client framework for the Google Gemini API. It is designed to be more than a simple API wrapper, providing a full-featured, extensible chat application architecture with a focus on integrating powerful local tools (functions). The project includes a ready-to-use Swing UI implementation, making it suitable for both standalone desktop applications and for embedding into larger host applications.

## 2. Core Features

- **Pure Java:** Ensures high portability across different environments.
- **Embeddable Swing UI:** A complete, renderer-based UI (`uno.anahata.gemini.ui.GeminiPanel`) is provided for displaying complex chat interactions.
- **Extensible Local Tools (SPI):** The framework's power lies in its Service Provider Interface for local functions, allowing the AI to interact directly with the user's machine via tools like `LocalFiles`, `LocalShell`, and `RunningJVM`.
- **Robust Session Management:** Includes context management with automatic pruning and session persistence using the Kryo serialization library.
- **Dynamic System Instructions:** A provider-based system allows for dynamically injecting context-aware instructions into the model's prompt.

## 3. Architectural Overview

The project is logically divided into four main layers. For a detailed breakdown of the classes and responsibilities within each package, please refer to the `package-info.java` files, which serve as the definitive source for architectural documentation.

- **Core Logic (`uno.anahata.gemini`):** Contains the central orchestrators like `GeminiChat` and `ContextManager`.
- **Function System (`uno.anahata.gemini.functions`):** The framework for discovering, managing, and executing local tools.
- **UI Layer (`uno.anahata.gemini.ui`):** The complete Swing-based user interface.
- **Internal Utilities (`uno.anahata.gemini.internal`):** Helper classes for serialization and data conversion.

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
