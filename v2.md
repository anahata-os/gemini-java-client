# V2 Master Plan: Anahata AI Framework

This document is the single source of truth for the V2 refactoring of the `anahata-ai` framework (formerly `gemini-java-client`). It consolidates the strategic vision, architectural plans, UI/UX goals, and technical debt from all project documentation.

## 1. Unified Vision & The Flywheel Strategy

The project follows a "flywheel" business model:
-   **The "Engine" (`anahata-ai`):** This is the core commercial product, a pure-Java, enterprise-grade AI framework. It is monetized via a dual AGPLv3/Commercial license.
-   **The "Car" (`anahata-netbeans-ai`):** This is the free, open-source flagship application that showcases the engine's power and drives adoption.

Our key competitive differentiator is **deep, interactive, and visual integration into desktop/IDE environments**.

## 2. V2 Core Architectural Pillars

### 2.1. Model Agnosticism & Project Modularity
The primary technical goal is to decouple the framework from any specific AI provider.
-   **Action:** Split the project into three modules: `anahata-ai-core`, `anahata-ai-gemini`, and `anahata-ai-swing`.
-   **Action:** Implement a model-agnostic domain in `anahata-ai-core` for all conversation elements.
-   **Action:** Create a `ContentProducer` interface to abstract away all provider-specific object creation.

### 2.2. The Active Workspace Model
This is the new paradigm for state management.
-   **Concept:** A client-side `ActiveWorkspaceManager` will hold the definitive state of all `StatefulResource`s.
-   **Action:** All tool calls will become **ephemeral** and aggressively pruned.
-   **Action:** Before each API call, the state from the `ActiveWorkspaceManager` will be **injected into the prompt**.

### 2.3. Instance-Based "Servlet-Style" Tooling
-   **Concept:** Migrate from static tool methods to an instance-based model.
-   **Action:** Create a `BaseTool` class that tools can extend to get access to the current `Chat` instance and the `ActiveWorkspaceManager`.

### 2.4. Advanced Schema Generation
-   **Goal:** Replace the custom `GeminiSchemaGenerator` with a mature, industry-standard library like **`jackson-module-jsonSchema`**.
-   **Benefit:** This will provide full, compliant JSON Schema generation and make the framework easily adaptable to other LLMs.

### 2.5. Hierarchical Chat Management
-   **Goal:** A `Chat` instance will have the ability to spawn and manage child `Chat` instances for complex, parallel workflows.

## 3. UI/UX Refactoring Plan

### 3.1. In-Chat Interactive Tool Prompter
-   **Action:** Replace the disruptive modal dialog with **interactive components rendered directly within the chat stream**.
-   **Features:** Each component will have its own controls for individual approval, denial, and setting session-level preferences (Always/Never).

### 3.2. Anahata Session Navigator
-   **Action:** Create a new `AnahataInstancesTopComponent` that displays a live list of all active chat sessions with real-time status.

## 4. `LocalFiles` Tool Evolution
-   **Goal:** Evolve the `listDirectory` tool to return a rich, polymorphic `List<FileSystemEntry>` instead of a simple `List<String>`.
-   **Benefit:** This provides structured, unambiguous metadata about files, directories, and symlinks, eliminating the need for inefficient and fragile shell calls (`ls -ld`) to determine entry types.
-   **Action:** Implement a new `findFiles(path, recursive, glob)` method to provide a structured, superior alternative to shell `find` and `grep` commands.
