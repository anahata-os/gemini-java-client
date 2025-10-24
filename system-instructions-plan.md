# Plan: Toggable System Instructions Framework

This document outlines the plan to refactor the system instruction generation mechanism to be modular, extensible, and controllable via the UI.

## 1. Core Problem

The current system hardcodes host-specific (e.g., NetBeans) details into the `GeminiConfig` and `GeminiChat` classes. There is no way to dynamically add or remove different types of contextual information (like project overviews, IDE alerts, etc.) that are sent to the model.

## 2. The Plan

### Step 1: Create a Service Provider Interface (SPI)

-   **Action:** Create a new interface named `SystemInstructionProvider` in the `uno.anahata.gemini.spi` package within the `gemini-java-client` project.
-   **Details:** The interface will define the contract for any module wishing to contribute to the system instructions. It will include methods for state management and identification.
-   **Interface Definition:**
    ```java
    public interface SystemInstructionProvider {
        String getId(); // e.g., "core-dynamic-env"
        String getDisplayName(); // e.g., "Dynamic Environment"
        boolean isEnabled();
        void setEnabled(boolean enabled);
        List<Part> getInstructionParts();
    }
    ```

### Step 2: Refactor Core Logic to Use the SPI

-   **Action:** Modify `GeminiChat` to use `java.util.ServiceLoader` to discover all implementations of `SystemInstructionProvider` on the classpath.
-   **Details:**
    -   The `buildSystemInstructions` method will be updated to iterate through all discovered providers.
    -   For each provider, it will check if `isEnabled()` is true.
    -   If enabled, it will call `getInstructionParts()` and add the returned `Part` objects to the system instructions list.
    -   The hardcoded calls to `getHostSpecificSystemInstructionParts` and `getSystemInstructionsAppendix` will be removed.

-   **Action:** Refactor `GeminiConfig`.
-   **Details:**
    -   Remove the now-obsolete `getHostSpecificSystemInstructionParts()` and `getSystemInstructionsAppendix()` methods.

### Step 3: Create a Default Core Provider

-   **Action:** Create a default implementation of the SPI within `gemini-java-client` itself.
-   **Details:** This provider will be responsible for the "Dynamic Environment Details" (GeminiConfig, System Properties, Env Variables) that was previously handled by `getSystemInstructionsAppendix`. This ensures the core library is self-contained and that this essential information is still provided by default.

### Step 4: Implement the UI in `GeminiPanel`

-   **Action:** Add a new "System Instructions" tab to the `JTabbedPane` in `GeminiPanel`.
-   **Details:**
    -   This tab will contain a `JTextArea` (or similar component) to display the *effective* system instructions that will be sent with the next user message. A "Refresh" button will trigger a call to a new method in `GeminiChat` that builds and returns the current instructions.
    -   The tab will also feature a toolbar or panel containing a `JToggleButton` for each discovered `SystemInstructionProvider`.
    -   The text for each button will be the provider's `getDisplayName()`.
    -   The initial selected state of the button will be determined by the provider's initial `isEnabled()` state.
    -   The action listener for each toggle button will call the `setEnabled(boolean)` method on its corresponding provider instance, allowing for dynamic control.

### Step 5: Migrate Host-Specific Logic (NetBeans)

-   **Action:** In the `anahata-netbeans-ai` project, create a new class that implements `SystemInstructionProvider`.
-   **Details:**
    -   Move all the logic currently in `NetBeansGeminiConfig.getHostSpecificSystemInstructionParts()` (IDE alerts, project overview, file modification rules) into the `getInstructionParts()` method of this new class.
    -   Register this class as a service provider using the standard `META-INF/services` mechanism.
    -   Remove the overridden `getHostSpecificSystemInstructionParts` method from `NetBeansGeminiConfig`.
