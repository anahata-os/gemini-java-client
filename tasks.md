# gemini-java-client - Master Task List

This document is the single source of truth for all actionable work items, technical debt, and future enhancements for the `gemini-java-client` project.

## Phase 1: Release 1.0.17 (UI/UX Stability & Theme Overhaul)
- [x] **Theme-Independent UI Engine**: Implemented `UITheme` with Auto, Light, Dark, and Minimalist modes.
- [x] **Vector Icon System**: Replaced PNG icons with programmatically drawn, theme-aware `ThemedIcon`s for perfect scaling and contrast.
- [x] **Enhanced Rendering**: Overhauled all `PartRenderer` implementations to respect theme colors and improve readability in "Metal" and other classic LAFs.
- [x] **Context Heatmap Refinement**: Role-based color coding for both the table and the pie chart, with descriptive "gist" labels.
- [x] **Refactor ChatPanel**: Simplified constructor and integration points.
- [x] **UI/UX Stability Fixes**:
    - [x] **Modal Hang Fix**: Corrected `SwingFunctionPrompter` parenting to prevent IDE lockups.
    - [x] **Input Area Overhaul**: Added `UndoManager` (Ctrl+Z/Y) and improved resizability within the split pane.
    - [x] **Split Pane Visibility**: Forced "grip" styling and added "Border Seams" for high-visibility dividers across all Look & Feels.
- [x] **Documentation**: Updated README with deep IDE integration examples and correct screenshot placement.

## Phase 2: Known bugs (Deferred to after 1.0.17 release)
-   [ ] **Fix Async Job Delivery:** Implement a queueing mechanism in `Chat` to ensure that asynchronous job results are reliably delivered and not dropped when a tool loop is already in progress.
-   [X] **Cancellation Framework:** Implement a robust cancellation mechanism (`ExecutorService`/`Future`) for API calls, tool loops, and individual tool executions.
