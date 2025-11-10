# V2 Plan: `LocalFiles` Tool Evolution

This document outlines the architectural plan for the next generation of the `LocalFiles` tool, focusing on providing rich, structured, and efficient file system information to the AI model. This design is the result of a detailed analysis of file system concepts and the limitations of the current implementation.

## 1. The Core Problem with `listDirectory` v1

The initial version of `listDirectory` returned a `List<String>`, which proved to be highly inefficient for the AI model for several reasons:

-   **Lack of Structure:** A simple string path does not convey whether the entry is a file, a directory, or a symbolic link.
-   **Forced Inefficiency:** To determine the type of each entry, the model was forced to make subsequent, expensive calls to `LocalShell` (e.g., `ls -ld <path>`) for each item in the list.
-   **Parsing Fragility:** Parsing the string output of shell commands is unreliable and prone to errors, especially with complex filenames or different operating system configurations.

## 2. The Definitive Data Model: Polymorphism

To solve these issues, the V2 `listDirectory` tool will return a `List<FileSystemEntry>`, where `FileSystemEntry` is a polymorphic type. This provides a single, sorted list that is rich with unambiguous metadata.

### 2.1. The Class Hierarchy

-   **`FileSystemEntry` (Abstract Base Class):**
    -   `String name`: The simple name of the file or directory (e.g., `"pom.xml"`).
    -   `String path`: The full, absolute path.
    -   `long size`: The size in bytes. For directories, this is the space allocated for the entry list itself.
    -   `long lastModified`: The last modified timestamp. For directories, this updates when an entry is added, removed, or renamed within it.

-   **`FileEntry` (extends `FileSystemEntry`):** Represents a regular file. No additional fields.

-   **`DirectoryEntry` (extends `FileSystemEntry`):** Represents a directory. No additional fields.

-   **`SymbolicLinkEntry` (extends `FileSystemEntry`):** Represents a symbolic link.
    -   `String target`: The path that the link points to.

-   **`OtherEntry` (extends `FileSystemEntry`):** A robust catch-all for other file system types (sockets, FIFOs, block devices, etc.).

### 2.2. Example JSON Response

A call to `listDirectory` on a project root would yield the following clean, structured JSON:

```json
[
  {
    "@type": "DirectoryEntry",
    "name": "src",
    "path": "/home/pablo/NetBeansProjects/my-project/src",
    "size": 4096,
    "lastModified": 1762778000000
  },
  {
    "@type": "FileEntry",
    "name": "pom.xml",
    "path": "/home/pablo/NetBeansProjects/my-project/pom.xml",
    "size": 9854,
    "lastModified": 1762473883050
  },
  {
    "@type": "SymbolicLinkEntry",
    "name": "latest_log",
    "path": "/home/pablo/NetBeansProjects/my-project/latest_log",
    "size": 12,
    "lastModified": 1762778123000,
    "target": "/tmp/app.log"
  }
]
```

## 3. The Strategic Role of `LocalFiles` vs. `LocalShell`

The V2 architecture establishes a clear and critical separation of concerns between these two tools:

-   **`LocalFiles`:** The **structured, safe, high-level API**. This is the **primary and preferred** tool for all file system discovery and safe, context-aware modifications. Its structured JSON output is reliable and efficient for the model to process.

-   **`LocalShell`:** The **unstructured, powerful, low-level "escape hatch."** This tool is for running external processes, build tools (`mvn`), version control (`git`), and using powerful command-line utilities (`grep`, `find`) when a structured alternative is not available. It should be considered the tool of last resort for tasks that can be accomplished with `LocalFiles`.

## 4. Future Enhancements

This new data model paves the way for more powerful, structured tools in the future, further reducing the reliance on `LocalShell`.

-   **`findFiles(path, recursive, glob)`:** A new method will be added to perform recursive file searches using glob patterns (e.g., `**/*.java`), returning a structured `List<FileSystemEntry>`. This will be vastly superior to parsing the output of shell `find` and `grep` commands.
