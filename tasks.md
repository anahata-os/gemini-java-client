# gemini-java-client - Master Task List

This document is the single source of truth for all actionable work items, technical debt, and future enhancements for the `gemini-java-client` project.

## Phase 1: V2 Core Refactoring & Bug Fixes (Immediate Focus)
-   [ ] **Resolve all compilation errors** resulting from the initial V2 refactoring. This is the highest priority.
-   [ ] **Implement the Model-Agnostic Domain** and `ContentProducer` interface as outlined in `v2.md`.
-   [ ] **Decompose God Classes:** Break down `Chat` and `ToolManager` into smaller, focused delegates.
-   [ ] **Fix Critical Schema Bugs:**
    -   [ ] Ensure `@AiToolParam` descriptions are included in the schema.
    -   [ ] Apply rich schema generation to tool input parameters.
    -   [ ] Base schema generation on class fields, not getter methods.
-   [ ] **Fix `MavenTools.addDependency` Bug:** Stop the tool from adding an incorrect `<classifier>jar</classifier>` tag.
-   [ ] **Fix `lastModified` Mismatch Bug:** Investigate and resolve failures in file-writing tools caused by stale timestamps.
-   [ ] **Fix Session Serialization:** Repair `SessionManager` to handle the new domain model and `Date`/`Instant` mismatches.
-   [ ] **Fix Async Job Delivery:** Implement a queueing mechanism in `Chat` to ensure that asynchronous job results are reliably delivered and not dropped when a tool loop is already in progress.
-   [ ] **Cancellation Framework:** Implement a robust cancellation mechanism (`ExecutorService`/`Future`) for API calls, tool loops, and individual tool executions.

## Phase 2: Tooling & Active Workspace
-   [ ] **Migrate to Instance-Based Tools** and create the `BaseTool` class.
-   [ ] **Implement the Active Workspace Model** with the `ActiveWorkspaceManager` and prompt injection.
-   [ ] **Make all tool calls ephemeral** and update the pruning logic.

## Phase 3: UI/UX Implementation
-   [ ] **Implement the In-Chat Interactive Tool Prompter**.
-   [ ] **Implement the Anahata Session Navigator**.
-   [ ] **Redesign `FunctionsPanel`** with the two-panel layout.
-   [ ] **Refactor `StatusListener`** to use a rich `ChatStatusEvent` object.
-   [ ] **UI Polish:** Add the `explanation` text to the `proposeChange` diff dialog.
-   [ ] **Status Bar Integration:** Fully integrate the new status bar and progress bar components into `GeminiPanel`.
-   [ ] **Detailed Status Reporting:** The status bar must clearly display state, latency, retries, and token counts.

## Phase 4: Long-Term & Ecosystem
-   [ ] **Split `gemini-java-client` into separate modules.**
-   [ ] **Implement Hierarchical Chat Management.**
-   [ ] **Implement Asynchronous Input:** Allow the user to queue messages while a tool loop is running.
-   [ ] **Embeddings for Notes:** Convert the personal notes system into a searchable knowledge base.

## Investigations & Technical Debt
-   **[High Priority] Investigate `lastModified` Timestamp Discrepancies:** Determine why file-writing tools sometimes fail with a mismatch, even without manual user edits.
-   **[High Priority] Investigate Concurrent Audio Streams:** Research if `javax.sound.sampled` supports simultaneous recording and playback for `AudioTool` and `RadioTool`.
-   **[Code Health]** Investigate why `buildApiContext` needs to filter for null `Content` objects.
