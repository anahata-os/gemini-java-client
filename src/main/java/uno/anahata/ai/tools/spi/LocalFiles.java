/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.spi;

import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;
import uno.anahata.ai.tools.spi.pojos.FileInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.stateful.ResourceStatus;
import uno.anahata.ai.context.stateful.ResourceTracker;
import uno.anahata.ai.context.stateful.StatefulResourceStatus;
import uno.anahata.ai.tools.ContextBehavior;
import uno.anahata.ai.tools.MultiPartResponse;

/**
 * A comprehensive, context-aware file operations tool that uses POJOs and
 * timestamps for safe, version-aware file modifications. This is the primary
 * tool for all file I/O.
 *
 * @author AI
 */
public class LocalFiles {

    @AIToolMethod(value = "Adds the contents of the specified paths as a blobs part to the user feedback message that follows the tool message.\n"
            , requiresApproval = true, 
            behavior = ContextBehavior.EPHEMERAL)
    public static MultiPartResponse addToUserPrompt(@AIToolParam("The absolute path of the file to read.") List<String> absolutePath) throws IOException {
        return new MultiPartResponse(absolutePath);
    }

    @AIToolMethod(value = "Reads a single a binary file and returns a byte[].", requiresApproval = false, behavior = ContextBehavior.EPHEMERAL)
    public static byte[] readBinaryFile(@AIToolParam("The absolute path of the file to read.") String path) throws IOException {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + path);
        }
        if (Files.isDirectory(filePath)) {
            throw new IOException("Path is a directory, not a file: " + path);
        }

        return Files.readAllBytes(filePath);
    }

    @AIToolMethod(value = "Reads a single file and returns a FileInfo object containing its path, content, size, and last modified timestamp.", requiresApproval = false, behavior = ContextBehavior.STATEFUL_REPLACE)
    public static FileInfo readFile(
            @AIToolParam("The absolute path of the file to read.") String path
    ) throws IOException {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + path);
        }
        if (Files.isDirectory(filePath)) {
            throw new IOException("Path is a directory, not a file: " + path);
        }

        // Redundant Read Check
        ResourceTracker rt = Chat.getCallingInstance().getContextManager().getResourceTracker();
        Optional<StatefulResourceStatus> status = rt.getStatefulResourcesOverview().stream()
                .filter(s -> s.getResourceId().equals(path))
                .findFirst();
        if (status.isPresent() && status.get().getStatus() == ResourceStatus.VALID) {
             throw new RuntimeException("Redundant Read: The file at " + path + " is already VALID in your context. Do not reload it.");
        }

        String content = Files.readString(filePath);
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        long size = Files.size(filePath);
        long contentLines = content.lines().count();
        return new FileInfo(path, content, contentLines, lastModified, size);
    }

    @AIToolMethod(value = "Writes content to an existing file, but only if the file exists and has not been modified since the provided timestamp. This is a safeguard against overwriting concurrent changes. Returns the updated FileInfo object. Don not use this to create new files unse LocalFiles.createFile instead", behavior = ContextBehavior.STATEFUL_REPLACE)
    public static FileInfo writeFile(
            @AIToolParam("The absolute path of the file to write to.") String path,
            @AIToolParam("The new content to write to the file.") String content,
            @AIToolParam("The expected 'last modified' timestamp of the file on disk. The write will fail if the actual timestamp is different.") long lastModified
    ) throws IOException {
        Path filePath = Paths.get(path);

        if (Files.exists(filePath)) {
            long currentLastModified = Files.getLastModifiedTime(filePath).toMillis();
            if (currentLastModified != lastModified) {
                throw new IOException("File modification conflict. The file at " + path
                        + " was modified on disk after it was read. Expected timestamp: " + lastModified
                        + ", but found: " + currentLastModified);
            }
        } else if (lastModified > 0) {
            throw new IOException("File modification conflict. The file at " + path
                    + " was expected to exist with timestamp " + lastModified + " but it has been deleted.");
        }

        Files.writeString(filePath, content);

        return new FileInfo(path);
    }

    @AIToolMethod(value = "Creates a new file with the given content, creating parent directories if necessary. Throws an IOException if a file or directory already exists at the specified path.", behavior = ContextBehavior.STATEFUL_REPLACE)
    public static FileInfo createFile(
            @AIToolParam("The absolute path of the file to create.") String path,
            @AIToolParam("The initial content to write to the file. Can be empty.") String content
    ) throws IOException {
        Path filePath = Paths.get(path);
        if (Files.exists(filePath)) {
            throw new IOException("Cannot create file. Path already exists: " + path);
        }
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.writeString(filePath, content);
        return new FileInfo(path);
    }

    @AIToolMethod(value = "Appends content to the end of a file. Returns the updated FileInfo object.", behavior = ContextBehavior.STATEFUL_REPLACE)
    public static FileInfo appendToFile(
            @AIToolParam("The absolute path of the file to append to.") String path,
            @AIToolParam("The content to append.") String content
    ) throws IOException {
        Files.writeString(Paths.get(path), content, java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
        return new FileInfo(path);
    }

    @AIToolMethod(value = "Deletes a file at the specified path.", behavior = ContextBehavior.STATEFUL_REPLACE)
    public static String deleteFile(
            @AIToolParam("The absolute path of the file to delete.") String path
    ) throws IOException {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + path);
        }
        Files.delete(filePath);
        Chat.getCallingInstance().getContextManager().getResourceTracker().pruneStatefulResources(Collections.singletonList(path), "File deleted via LocalFiles.deleteFile");
        return "Successfully deleted file: " + path;
    }

    @AIToolMethod(value = "Moves or renames a file, creating parent directories for the destination if they don't exist. The operation will fail if the target file already exists.", behavior = ContextBehavior.EPHEMERAL)
    public static String moveFile(
            @AIToolParam("The absolute path of the file to move.") String sourcePath,
            @AIToolParam("The absolute path of the destination.") String targetPath
    ) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourcePath);
        }
        Path target = Paths.get(targetPath);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.move(source, target);
        Chat.getCallingInstance().getContextManager().getResourceTracker().pruneStatefulResources(Collections.singletonList(sourcePath), "File moved via LocalFiles.moveFile");
        return String.format("Successfully moved %s to %s", sourcePath, targetPath);
    }

    @AIToolMethod(value = "Copies a file from a source path to a destination path, creating parent directories for the destination if they don't exist. The operation will fail if the target file already exists.", behavior = ContextBehavior.EPHEMERAL)
    public static String copyFile(
            @AIToolParam("The absolute path of the source file.") String sourcePath,
            @AIToolParam("The absolute path of the destination file.") String destinationPath
    ) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourcePath);
        }
        Path target = Paths.get(destinationPath);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(source, target);
        long size = Files.size(target);
        return String.format("Successfully copied %d bytes from %s to %s", size, sourcePath, destinationPath);
    }

    @AIToolMethod("Creates all non-existent parent directories for the given path.")
    public static String createDirectories(
            @AIToolParam("The absolute path of the directory structure to create.") String path
    ) throws IOException {
        Path dirPath = Paths.get(path);
        Files.createDirectories(dirPath);
        return "Successfully created directory structure for: " + path;
    }

    @AIToolMethod(value = "Checks if a file or directory exists at the specified path.", requiresApproval = false)
    public static boolean fileExists(
            @AIToolParam("The absolute path to check.") String path
    ) {
        return Files.exists(Paths.get(path));
    }

    @AIToolMethod(value = "Lists the contents of a directory with basic metadata (name, type, size).", requiresApproval = false)
    public static List<String> listDirectory(
            @AIToolParam("The absolute path of the directory to list.") String path
    ) throws IOException {
        Path dirPath = Paths.get(path);
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Path is not a directory: " + path);
        }
        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream.map(p -> {
                try {
                    String type = Files.isDirectory(p) ? "[DIR]" : "[FILE]";
                    long size = Files.isDirectory(p) ? 0 : Files.size(p);
                    return String.format("%-6s %-10d %s", type, size, p.getFileName().toString());
                } catch (IOException e) {
                    return "[ERROR] " + p.getFileName().toString();
                }
            }).collect(Collectors.toList());
        }
    }
}