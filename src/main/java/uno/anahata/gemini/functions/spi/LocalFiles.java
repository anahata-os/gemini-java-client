package uno.anahata.gemini.functions.spi;

import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.functions.AIToolParam;
import uno.anahata.gemini.functions.spi.pojos.FileInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import uno.anahata.gemini.functions.ContextBehavior;

/**
 * A comprehensive, context-aware file operations tool that uses POJOs and timestamps
 * for safe, version-aware file modifications. This is the primary tool for all file I/O.
 * @author AI
 */
public class LocalFiles {

    private static String sanitize(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\r\\n", "\\n").replaceAll("\\r", "\\n");
        return normalized.replaceAll("[^\\p{Print}\\t\\n]", "");
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

        String content = Files.readString(filePath);
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        long size = Files.size(filePath);

        return new FileInfo(path, content, lastModified, size);
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
                throw new IOException("File modification conflict. The file at " + path +
                                      " was modified on disk after it was read. Expected timestamp: " + lastModified +
                                      ", but found: " + currentLastModified);
            }
        } else if (lastModified > 0) {
             throw new IOException("File modification conflict. The file at " + path +
                                      " was expected to exist with timestamp " + lastModified + " but it has been deleted.");
        } 

        Files.writeString(filePath, sanitize(content));

        return readFile(path);
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
        Files.writeString(filePath, sanitize(content));
        return readFile(path);
    }

    @AIToolMethod(value = "Appends content to the end of a file. Returns the updated FileInfo object.", behavior = ContextBehavior.STATEFUL_REPLACE)
    public static FileInfo appendToFile(
            @AIToolParam("The absolute path of the file to append to.") String path,
            @AIToolParam("The content to append.") String content
    ) throws IOException {
        Files.writeString(Paths.get(path), sanitize(content), java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
        return readFile(path);
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
        return "Successfully deleted file: " + path;
    }

    @AIToolMethod(value = "Moves or renames a file. Returns the FileInfo of the new file.", behavior = ContextBehavior.STATEFUL_REPLACE)
    public static FileInfo moveFile(
            @AIToolParam("The absolute path of the file to move.") String sourcePath,
            @AIToolParam("The absolute path of the destination.") String targetPath
    ) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourcePath);
        }
        Path target = Paths.get(targetPath);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return readFile(targetPath);
    }

    @AIToolMethod(value = "Copies a file from a source path to a destination path. Returns the FileInfo of the new file.", behavior = ContextBehavior.STATEFUL_REPLACE)
    public static FileInfo copyFile(
            @AIToolParam("The absolute path of the source file.") String sourcePath,
            @AIToolParam("The absolute path of the destination file.") String destinationPath
    ) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourcePath);
        }
        Path destination = Paths.get(destinationPath);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        return readFile(destinationPath);
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

    @AIToolMethod(value = "Lists the contents (files and subdirectories) of a given directory.", requiresApproval = false)
    public static List<String> listDirectory(
            @AIToolParam("The absolute path of the directory to list.") String path
    ) throws IOException {
        Path dirPath = Paths.get(path);
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Path is not a directory: " + path);
        }
        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream.map(Path::toString).collect(Collectors.toList());
        }
    }
}
