# Project Overview: gemini-java-client (v1)

This document provides the essential, high-level overview of the `gemini-java-client` project. It is the stable, "always-in-context" guide to the project's purpose, architecture, and core principles.

## 1. High-Level Summary

`gemini-java-client ` is a **pure-Java, enterprise-grade Gemini client framework** for building sophisticated, context-aware AI assistants. Its core strength is the **annotation-driven local tool system**, which enables the AI to execute arbitrary Java code, interact with the local file system, and manage the application's state. The project includes a complete, embeddable **Swing UI** for a rich, interactive chat experience, making it ideal for integration into IDEs or standalone desktop applications.

## 2. Architectural Overview

The project is architected as a modular, extensible AI assistant platform, logically divided into several core packages.

- **`uno.anahata.ai` (Core):** The root package containing the central orchestrator, `Chat.java`.
- **`uno.anahata.ai.config` (Configuration):** Provides the configuration framework (`ChatConfig`, `ConfigManager`).
- **`uno.anahata.ai.context` (Context Management):** A sophisticated system for managing the conversation's context, including dynamic providers and stateful resource tracking.
- **`uno.anahata.ai.gemini` (API Adapter):** An abstraction layer over the official Google Gemini client library.
- **`uno.anahata.ai.status` (Status Management):** An event-driven framework for monitoring and broadcasting the application's real-time operational status.
- **`uno.anahata.ai.swing` (UI Layer):** A complete, embeddable Swing-based user interface.
- **`uno.anahata.ai.tools` (Local Tools & Functions):** The heart of the framework's extensibility, including the `ToolManager` and the `SchemaProvider2` for rich schema generation.
- **`uno.anahata.ai.media` (Media Handling):** A module for media-related tasks (`AudioTool`, `RadioTool`).
- **`uno.anahata.ai.internal` (Internal Utilities):** Essential helper classes for serialization, MIME type detection, and text processing.

## 3. Coding Principles

1.  **Javadoc Integrity:** As an open-source Java library, comprehensive documentation is paramount. Existing Javadoc, comments, and blank lines **must never be removed**. New public classes and methods **must have Javadoc**.
2.  **Logging Standard:** All logging **must** be done through the SLF4J API (`@Slf4j`). **Never** use `System.out.println()`.
3.  **Dependency Management Workflow:** Adhere to the strict workflow: `searchMavenIndex` -> `getResolvedDependencies` -> `addDependency` -> `downloadProjectDependencies`.

## 4. CI/CD & Website Deployment

- **GitHub Actions:** The `.github/workflows/javadoc.yml` workflow automatically generates and deploys Javadocs on every push to the `main` branch.
- **Hosting:** Hosted on GitHub Pages at [https://anahata-os.github.io/gemini-java-client/](https://anahata-os.github.io/gemini-java-client/).
- **Structure:** Javadocs are deployed to the **root** of the `gh-pages` branch (no `/apidocs` suffix).

## 5. Development & Testing Notes

- When testing code in this project via `NetBeansProjectJVM.compileAndExecuteInProject`, **always set `includeCompileAndExecuteDependencies` to `false`** to avoid `LinkageError` exceptions.
- The AI Assistant's execution environment (the Anahata NetBeans plugin) **inherits the full, resolved classpath of this project**. Any dependency in the `pom.xml` is automatically available to dynamically compiled code.
