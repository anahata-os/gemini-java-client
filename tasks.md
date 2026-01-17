# gemini-java-client - Master Task List

This document is the single source of truth for all actionable work items, technical debt, and future enhancements for the `gemini-java-client` project.

## Phase 1: Release 1.0.0
- [x] Refactor ChatPanel to use a single constructor and simplify integration.
- [x] Update Main.java to use the new ChatPanel constructor.
- [x] Update AnahataTopComponent to use the new ChatPanel constructor.
- [x] Update README.md with the new integration example.
- [x] Add more screenshots to the README.
- [x] Refine README with deep IDE integration examples and correct screenshot placement.
- [x] Finalize README with website/YouTube links and optimistic locking note.

## Phase 2: Known bugs (Deferred to after 1.0.0 release)
-   [ ] **Fix Async Job Delivery:** Implement a queueing mechanism in `Chat` to ensure that asynchronous job results are reliably delivered and not dropped when a tool loop is already in progress.
-   [ ] **Cancellation Framework:** Implement a robust cancellation mechanism (`ExecutorService`/`Future`) for API calls, tool loops, and individual tool executions.
