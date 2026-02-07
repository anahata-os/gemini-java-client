/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
# Project: gemini-java-client - The Autonomous AI Agent Engine for the JVM

This is your primary technical reference for the `gemini-java-client` framework. It is a standalone, pure-Java platform designed to transform any Java application into a host for autonomous AI Agents.

## 1. Core Philosophy: The Autonomous Agent
Unlike passive chatbots, agents built with this framework are **insiders**. They inhabit the JVM, introspect the runtime, and execute code.
- **Actionable Intelligence**: Methods annotated with `@AIToolMethod` become tools the agent can call.
- **Dynamic Execution**: The `RunningJVM` tool allows the agent to write, compile, and run Java code in-process.
- **Contextual Awareness**: The `ContextManager` tracks every message, tool call, and local resource (files) to maintain a coherent state.

## 2. Detailed Architectural Breakdown

### `uno.anahata.ai` (The Core)
- `Chat.java`: The central orchestrator. Manages the conversation loop, tool execution, and event broadcasting.
- `ChatMessage.java`: DTO for messages, supporting multi-part content (Text, Blob, FunctionCall, FunctionResponse).
- `AnahataExecutors.java`: Manages the thread pools for non-blocking tool execution and API calls.

### `uno.anahata.ai.context` (The Memory)
- `ContextManager.java`: Manages the history and pruning.
- `ResourceTracker.java`: Tracks local files loaded into context. Marks them `STALE` if modified on disk.
- `ContextPruner.java`: Implements the **PAYG (Prune-As-You-Go)** algorithm to keep the context window efficient.
- `ContextProvider.java`: SPI for adding dynamic context (e.g., System Properties, Environment Variables).

### `uno.anahata.ai.tools` (The Hands)
- `ToolManager.java`: Discovers and invokes tools. Handles JSON schema generation for the LLM.
- `SchemaProvider.java`: Uses Jackson and custom logic to generate precise JSON schemas from Java types.
- `AIToolMethod.java` / `AIToolParam.java`: Annotations used to expose Java logic to the AI.
- `uno.anahata.ai.tools.spi`: Standard tools provided by the framework (LocalFiles, LocalShell, RunningJVM, Images).

### `uno.anahata.ai.swing` (The Face)
- `ChatPanel.java`: The main embeddable UI component.
- `ContextHeatmapPanel.java`: Visualizes token usage and pruning status.
- `ConversationPanel.java`: Handles the rendering of the chat history.
- `SupportPanel.java`: Provides community links and documentation access.

### `uno.anahata.ai.media` (The Vibe)
- `RadioTool.java`: Streams internet radio (SomaFM integration).
- `DJEngine.java`: Generates MIDI-based foundational beats.
- `AudioPlayer.java`: Low-level audio playback utility.
- `PianoTool.java`: Asynchronous MIDI melody playback.

### `uno.anahata.ai.gemini` (The Brain)
- `GeminiAdapter.java`: Bridges the framework to the Google GenAI Java SDK.
- `GeminiAPI.java`: Low-level API interaction and key management.

## 3. Key Technical Concepts

### PAYG Pruning (v2)
The framework automatically manages the context window by:
1.  Pruning ephemeral tool calls after 4 user turns.
2.  Replacing stateful resources when a newer version is loaded.
3.  Allowing manual pruning via the UI or `ContextWindow` tools.

### Stateful Resource Tracking
When a tool returns a `FileInfo` object, the framework tracks that file. If the file's `lastModified` timestamp on disk changes, the version in context is marked `STALE`, prompting the agent to reload it.

### Dynamic Classloading
`RunningJVM` uses a **Child-First ClassLoader**. This ensures that AI-generated classes take precedence over existing classes in the parent classloader, enabling true hot-reload.

## 4. Recent Milestones
- **v1.1.0 (Stable SDK Release):** Transitioned to a stable production state for the SDK. Includes the new Vector Icon System, enhanced PAYG v2 context management, and critical UI/UX stability fixes.

## 5. Release Coordination (CRITICAL)
- **Release Synchronization:** When releasing a new version of this library, you **MUST WAIT at least 5 to 10 minutes** before pushing the corresponding plugin update to GitHub.
- **Rationale:** The GitHub Actions build for the plugin will fail if it cannot find the newly released library artifact in Maven Central. Central synchronization takes time; pushing too early will break the CI/CD pipeline.
- **Sequential Pushing:** Never push both projects simultaneously. Ensure the library is fully published and visible in Central before triggering the plugin's release workflow.
- **Verification Protocol:** Before pushing a plugin release that depends on a new library version, you MUST poll Maven Central (e.g., via `searchMavenIndex` or a custom scraper) to verify that the specific version is available and indexed.

## 6. Coding Principles for Framework Developers
1.  **Javadoc is Mandatory**: Every public class and method must be documented.
2.  **SLF4J Logging**: Use `@Slf4j`. No `System.out`.
3.  **Thread Safety**: The framework is highly concurrent. Use `synchronized` blocks or concurrent collections where necessary.
4.  **Zero External Dependencies**: Keep the core lean. Prefer standard Java APIs or well-established libraries like Jackson/Guava.

## 7. CI/CD & Deployment
- **Deployment Strategy**: The website (in `/docs`) and Javadocs (generated in `target/reports/apidocs`) are deployed directly to GitHub Pages via GitHub Actions.
- **URL**: [https://anahata-os.github.io/gemini-java-client/](https://anahata-os.github.io/gemini-java-client/)

## 8. Development & Testing Notes
- When testing via `NetBeansProjectJVM.compileAndExecuteInProject`, **always set `includeCompileAndExecuteDependencies` to `false`** to avoid `LinkageError` exceptions.
- The engine inherits the full, resolved classpath of the host application.

## 9. Licensing Strategy
- **Dual Licensing**: This project is dual-licensed to ensure clarity for both human developers and AI agents.
- **For Humans**: Licensed under the **Apache License, Version 2.0**.
- **For AIs**: Licensed under the **Anahata Software License (ASL) V108**.
