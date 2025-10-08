# TODO List: The Grand Refactoring Epic

This document outlines the full scope of the architectural refactoring for the `gemini-java-client`. It synthesizes our conversations, the existing `gemini.md` notes, and the current state of the codebase into a clear, phased plan.

---

## Phase 1: Complete the Core Architectural Foundation

**Objective:** Solidify the new data models and refactor the core services to be fully aligned with the new stateful, message-centric architecture.

### 1.1: Finalize Core Data Models
- **Task:** Ensure all new POJOs are complete and correctly defined.
- **`ChatMessage.java`**: The new central data model.
  - `id`: Stable, unique identifier.
  - `modelId`: The model that generated the message.
  - `content`: The core `Content` object.
  - `functionResponses`: Map to explicitly link calls and responses.
  - `usageMetadata`: For token counts and heatmap.
  - `groundingMetadata`: For citations.
  - **Status: `[x]` COMPLETE**
- **`ContextBehavior.java`**: Enum for declarative context management.
  - `EPHEMERAL`: For transient results.
  - `STATEFUL_REPLACE`: For workspace resources like files.
  - **Status: `[x]` COMPLETE**
- **`JobInfo.java`**: POJO for asynchronous operations.
  - `jobId`, `status`, `description`, `result`.
  - **Status: `[x]` COMPLETE**

### 1.2: Refactor `ContextManager.java`
- **Task:** Fully convert the `ContextManager` to operate on `List<ChatMessage>` instead of `List<Content>`.
- **Sub-tasks:**
  - `[ ]` Change the internal `context` list from `List<Content>` to `List<ChatMessage>`.
  - `[ ]` Implement the "read-and-replace" / "write-and-replace" logic in the `add()` method for any `ChatMessage` containing a `FunctionResponse` from a tool marked as `STATEFUL_REPLACE`.
  - `[ ]` Implement the "Two-Turn Rule" garbage collection to automatically prune old `EPHEMERAL` tool call results.
  - `[ ]` Add a new `pruneById(String id)` method for manual UI-driven pruning.
- **Status: `[ ]` NOT STARTED**

### 1.3: Refactor `GeminiChat.java`
- **Task:** Overhaul the main chat loop to support the new architecture. This is the most critical piece of the refactoring.
- **Sub-tasks:**
  - `[ ]` Create the `buildApiContext(List<ChatMessage>)` conversion method.
  - `[ ]` **Implement the "Active Workspace"**: Before sending to the model, this method must iterate through the context, find all `STATEFUL_REPLACE` resources (files), and create a final, consolidated `Content` object with `role="tool"` containing their full content. This `Content` object must be appended to the very end of the list sent to the API.
  - `[ ]` Rewrite the main `sendContent` loop to create and manage `ChatMessage` objects, linking `FunctionResponse`s back to the `ChatMessage` that contained the original `FunctionCall`.
  - `[ ]` Implement the async job notification logic (proactively sending a message to the model when a background job completes and no other API call is in flight).
- **Status: `[ ]` NOT STARTED**

### 1.4: Refactor `FunctionManager.java`
- **Task:** Finalize the implementation of advanced tool-handling features.
- **Sub-tasks:**
  - `[x]` The `fromMethod` logic has been updated to add the artificial `asynchronous: boolean` parameter to all tool definitions.
  - `[ ]` The main `processFunctionCalls` logic needs to be updated to *handle* the `asynchronous: true` flag by wrapping the method invocation in a background task and immediately returning a `JobInfo` object.
  - `[ ]` Implement the `FailureTracker` service to prevent the model from getting stuck in error loops by blocking repeated failed calls.
- **Status: `[ ]` IN PROGRESS**

---

## Phase 2: UI Layer and Feature Enablement

**Objective:** Update the UI to work with the new data model and implement the user-facing features that the new architecture enables.

### 2.1: Update UI Renderers
- **Task:** Modify all `PartRenderer` implementations to accept a `ChatMessage` object instead of a raw `Content` object. This is a prerequisite for all other UI work.
- **Status: `[ ]` NOT STARTED**

### 2.2: Implement Grounding/Citation View
- **Task:** Create a new `GroundingMetadataRenderer` that can format and display citation information from the `groundingMetadata` field of a `ChatMessage`.
- **Status: `[ ]` NOT STARTED**

### 2.3: Implement Context Heatmap
- **Task:** Modify the `ContentRenderer` to read the `usageMetadata` from each `ChatMessage` and apply a visual indicator (e.g., a colored border or background) to represent the token cost of that message.
- **Status: `[ ]` NOT STARTED**

### 2.4: Implement Manual Pruning in UI
- **Task:** Add a "delete" button to each rendered message component. Clicking this button should call the new `ContextManager.pruneById()` method using the message's stable ID.
- **Status: `[ ]` NOT STARTED**

---

## Phase 3: Future Work & Deferred Items

**Objective:** Capture important ideas that were discussed but are out of scope for the initial refactoring epic.

### 3.1: Implement Kryo Serialization
- **Task:** Replace the current GSON-based session serialization with the more performant and robust Kryo framework. This will improve performance and reduce maintenance overhead.
- **Status: `[ ]` DEFERRED**

### 3.2: Build a Multi-Model Abstraction Layer
- **Task:** Create a generic interface for chat models to allow plugging in other providers like OpenAI or Claude.
- **Status: `[ ]` DEFERRED**


### 2.1: Update UI Renderers
- **Task:** Modify all `PartRenderer` implementations to accept a `ChatMessage` object instead of a raw `Content` object. This is a prerequisite for all other UI work.
- **Sub-tasks:**
  - `[ ]` **Enhance Message Headers:**
    - `[ ]` Replace the static "MODEL" label with the dynamic `chatMessage.getModelId()`.
    - `[ ]` Replace the static "USER" label with the value from the `user.name` system property.
    - `[ ]` Display the `usageMetadata` (token counts) in the header.
- **Status: `[ ]` NOT STARTED**

**Implementation Strategy:**
- `[ ]` The new Kryo-based serialization will be implemented in parallel to the existing Gson-based methods.
- `[ ]` The Gson methods (`saveSession`, `loadSession`) will be marked as `@Deprecated`.
- `[ ]` New methods (`saveSessionKryo`, `loadSessionKryo`) will be created.
- `[ ]` The old Gson methods will only be removed after Kryo implementation has been validated as 100% stable and reliable.


### 3.1: Implement Kryo Serialization
- **Task:** Replace the current GSON-based session serialization with the more performant and robust Kryo framework.
- **Status: `[ ]` DEFERRED**
- **Implementation Strategy:**
  - `[ ]` The new Kryo-based serialization will be implemented in parallel to the existing Gson-based methods.
  - `[ ]` The Gson methods (`saveSession`, `loadSession`) will be marked as `@Deprecated`.
  - `[ ]` New methods (`saveSessionKryo`, `loadSessionKryo`) will be created.
  - `[ ]` The old Gson methods will only be removed after Kryo implementation has been validated as 100% stable and reliable.
