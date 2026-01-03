# gemini-java-client - Master Task List

This document is the single source of truth for all actionable work items, technical debt, and future enhancements for the `gemini-java-client` project.



## Phase 1: Known bugs (Deferred to after 1.0.0 release)
-   [ ] **Fix Async Job Delivery:** Implement a queueing mechanism in `Chat` to ensure that asynchronous job results are reliably delivered and not dropped when a tool loop is already in progress.
-   [ ] **Cancellation Framework:** Implement a robust cancellation mechanism (`ExecutorService`/`Future`) for API calls, tool loops, and individual tool executions.
