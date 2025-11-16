# Design: In-Chat Interactive Function Prompter

## 1. Problem Statement
The current `SwingFunctionPrompter` is implemented as a modal `JDialog`. This approach has significant UX drawbacks:
- It **blocks the entire application UI**, preventing the user from scrolling through the chat history, copying text from previous messages, or interacting with the IDE in any way.
- It forces the user to make a decision on **all proposed function calls at once**, which is inflexible.
- The workflow is disruptive and breaks the conversational flow of the chat.

## 2. Proposed Solution: A Non-Blocking, In-Chat Experience
We will replace the modal dialog with a new system that renders proposed function calls as interactive components directly within the chat stream in the `ConversationPanel`.

### Key User Experience Goals:
- **Seamless Interaction:** The user can scroll, select, and copy text while the function call prompts are visible.
- **Granular Control:** Each proposed tool call will have its own set of controls, allowing for individual approval, denial, or execution.
- **Persistent Choices:** The user can select "Always Run" or "Never Run" for a specific tool, and this preference will be remembered for the current chat session.
- **Batch Operation:** A "Run All Approved" button will remain available for convenience.
- **Clear State:** The UI will clearly distinguish between a *proposed* tool call and one that has been *executed*.

## 3. Architectural Refactoring Plan

This is a multi-stage refactoring that touches the core chat loop and the UI rendering pipeline.

### Step 1: Create `InteractiveFunctionCallPanel.java` (The Core UI Component)
- A new `JPanel` will be created to render a single, *pending* `FunctionCall`.
- It will display the function name and arguments (similar to the current renderer).
- It will contain its own interactive controls:
    - A primary "Run" button.
    - A "Skip" button.
    - A dropdown or toggle group for setting the session preference ("Prompt", "Always", "Never").
- This component will be the fundamental building block for the new design.

### Step 2: Deprecate `SwingFunctionPrompter.java`
- The existing modal `JDialog` will be deprecated and eventually removed.
- Its responsibility for gathering user choices will be superseded by the new interactive panels and the `ConversationPanel` itself.

### Step 3: Refactor `ContentRenderer.java` to be State-Aware
- The `ContentRenderer` will be modified to inspect the `ChatMessage`'s `MessageRole`.
- If it's rendering a `FunctionCall` from a `MODEL` message (i.e., a proposal), it will use the new `InteractiveFunctionCallPanel`.
- If it's rendering a `FunctionCall` from a `TOOL` message (i.e., an executed call), it will use the existing, non-interactive `FunctionCallPartRenderer`.

### Step 4: Refactor the Core `Chat.java` Loop
- This is the most critical architectural change.
- When the `Chat` receives a response from the model containing `FunctionCall`s, it will **no longer block and call the `FunctionPrompter`**.
- Instead, it will:
    1. Add the model's message (containing the pending calls) to the context.
    2. Enter a new `ChatStatus`, such as `WAITING_FOR_USER_FUNCTION_CONFIRMATION`.
    3. Stop and wait for UI interaction.
- A new public method will be added to `Chat`, e.g., `public void executeApprovedFunctions(List<FunctionCall> approved, List<FunctionCall> denied, String userComment)`.
- This method will be invoked by the UI. It will process the approved calls, generate the `TOOL` response message, and resume the conversation by sending the results back to the model.

### Step 5: Update `ConversationPanel.java` to Orchestrate
- The `ConversationPanel` will be responsible for managing the collection of interactive prompts.
- It will contain a "Run All Approved" button that appears at the bottom of the chat when there are pending calls.
- When a user clicks "Run" on an individual panel or the main "Run All" button, the `ConversationPanel` will:
    1. Iterate through all visible `InteractiveFunctionCallPanel` instances.
    2. Collect the approved and denied `FunctionCall`s based on their current state.
    3. Call the new `chat.executeApprovedFunctions(...)` method to continue the workflow.
