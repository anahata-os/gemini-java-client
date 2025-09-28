package uno.anahata.gemini.functions.spi;

import uno.anahata.gemini.functions.AITool;
import uno.anahata.gemini.functions.pojos.FileInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A comprehensive, context-aware file operations tool that uses POJOs and timestamps
 * for safe, version-aware file modifications. This is the primary tool for all file I/O.
 * @author AI
 */
public class LocalFiles {

    @AITool(value = "Reads a single file and returns a FileInfo object containing its path, content, size, and last modified timestamp.", requiresApproval = false)
    public static FileInfo readFile(
            @AITool("The absolute path of the file to read.") String path
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

    @AITool("Writes content to a file, but only if the file has not been modified since it was last read. It uses the lastModified timestamp from the FileInfo object as a precondition. Returns the updated FileInfo object.")
    public static FileInfo writeFile(
            @AITool("A FileInfo object containing the path, new content, and the expected last modified timestamp. OPTIMISTIC USAGE: If you have just performed a successful write/patch, you can use the 'lastModified' from the returned FileInfo object for the next immediate write on the same file, saving a 'readFile' call.")
            FileInfo fileInfo
    ) throws IOException {
        Path filePath = Paths.get(fileInfo.path);

        if (Files.exists(filePath)) {
            long currentLastModified = Files.getLastModifiedTime(filePath).toMillis();
            if (currentLastModified != fileInfo.lastModified) {
                throw new IOException("File modification conflict. The file at " + fileInfo.path +
                                      " was modified on disk after it was read. Expected timestamp: " + fileInfo.lastModified +
                                      ", but found: " + currentLastModified);
            }
        } else if (fileInfo.lastModified > 0) {
             throw new IOException("File modification conflict. The file at " + fileInfo.path +
                                      " was expected to exist with timestamp " + fileInfo.lastModified + " but it has been deleted.");
        }

        Files.writeString(filePath, fileInfo.content);
        
        return readFile(fileInfo.path);
    }
    
    @AITool("Appends content to the end of a file. Returns the updated FileInfo object.")
    public static FileInfo appendToFile(
            @AITool("The absolute path of the file to append to.") String path,
            @AITool("The content to append.") String content
    ) throws IOException {
        Files.writeString(Paths.get(path), content, java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
        return readFile(path);
    }

    @AITool("Deletes a file at the specified path.")
    public static String deleteFile(
            @AITool("The absolute path of the file to delete.") String path
    ) throws IOException {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + path);
        }
        Files.delete(filePath);
        return "Successfully deleted file: " + path;
    }

    @AITool("Moves or renames a file. Returns the FileInfo of the new file.")
    public static FileInfo moveFile(
            @AITool("The absolute path of the file to move.") String sourcePath,
            @AITool("The absolute path of the destination.") String targetPath
    ) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourcePath);
        }
        Path target = Paths.get(targetPath);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return readFile(targetPath);
    }
    
    @AITool("Copies a file from a source path to a destination path. Returns the FileInfo of the new file.")
    public static FileInfo copyFile(
            @AITool("The absolute path of the source file.") String sourcePath,
            @AITool("The absolute path of the destination file.") String destinationPath
    ) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourcePath);
        }
        Path destination = Paths.get(destinationPath);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        return readFile(destinationPath);
    }

    @AITool("Creates all non-existent parent directories for the given path.")
    public static String createDirectories(
            @AITool("The absolute path of the directory structure to create.") String path
    ) throws IOException {
        Path dirPath = Paths.get(path);
        Files.createDirectories(dirPath);
        return "Successfully created directory structure for: " + path;
    }

    @AITool(value = "Checks if a file or directory exists at the specified path.", requiresApproval = false)
    public static boolean fileExists(
            @AITool("The absolute path to check.") String path
    ) {
        return Files.exists(Paths.get(path));
    }
    
    @AITool(value = "Lists the contents (files and subdirectories) of a given directory.", requiresApproval = false)
    public static List<String> listDirectory(
            @AITool("The absolute path of the directory to list.") String path
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
