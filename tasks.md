# gemini-java-client - Master Task List

This document is the single source of truth for all actionable work items, technical debt, and future enhancements for the `gemini-java-client` project.

## Phase 1: prepare for the nb plugin go-live in nb plugin portal
    -   [ ] **Radio Tool more stations and UI:** 
    -   [ ] **DJ Tool not stopping / working:** 
    -   [ ] Live workspace button not working

Both radio tools and dj tools should just non modal dialogs as UIs and be visible when they are running and stopped when the dialogs are closed. Bu the UIs should be static components in case the user has several chat tabs open

## Phase 2: Known bugs
-   [ ] **Fix Async Job Delivery:** Implement a queueing mechanism in `Chat` to ensure that asynchronous job results are reliably delivered and not dropped when a tool loop is already in progress.
-   [ ] **Cancellation Framework:** Implement a robust cancellation mechanism (`ExecutorService`/`Future`) for API calls, tool loops, and individual tool executions.



