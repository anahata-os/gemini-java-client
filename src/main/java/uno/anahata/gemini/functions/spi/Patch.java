package uno.anahata.gemini.functions.spi;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.PatchFailedException;
import uno.anahata.gemini.functions.AITool;
import uno.anahata.gemini.functions.pojos.FileInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * A dedicated tool for applying diff patches to files, ensuring atomicity and safety.
 * @author AI
 */
public class Patch {
    //too difficult for an AI to make a perfect patch, cant even get line numbers right
    //@AITool("Applies a unified diff patch to a file. It reads the file from disk, verifies its lastModified timestamp as a precondition, applies the patch, and writes the changes back. Returns the updated FileInfo.")
    public static FileInfo applyPatch(
            @AITool("The absolute path of the file to be patched.")
            String path,
            @AITool("The expected lastModified timestamp of the file on disk. This is a precondition to prevent overwriting concurrent changes. OPTIMISTIC USAGE: If you have just performed a successful write/patch, you can use the 'lastModified' from the returned FileInfo object for the next immediate patch on the same file, saving a 'readFile' call.")
            long lastModified,
            @AITool("The unified diff patch content as a string.")
            String patch
    ) throws IOException, PatchFailedException {
        Path filePath = Paths.get(path);

        if (!Files.exists(filePath)) {
            throw new IOException("File to patch does not exist: " + path);
        }

        // Precondition Check: Ensure the file on disk hasn't changed since it was last known.
        long currentLastModified = Files.getLastModifiedTime(filePath).toMillis();
        if (currentLastModified != lastModified) {
            throw new IOException("File modification conflict. The file at " + path +
                                  " was modified on disk after it was read. Expected timestamp: " + lastModified +
                                  ", but found: " + currentLastModified);
        }

        List<String> originalLines = Files.readAllLines(filePath);
        List<String> patchLines = patch.lines().toList();

        com.github.difflib.patch.Patch<String> diff = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
        List<String> patchedLines = DiffUtils.patch(originalLines, diff);
        String newContent = String.join(System.lineSeparator(), patchedLines);

        Files.writeString(filePath, newContent);

        // Return the new, updated FileInfo
        return LocalFiles.readFile(path);
    }
}
