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

    @AITool("Applies a unified diff patch to a file. It uses the lastModified timestamp from the FileInfo object as a precondition to prevent conflicts. Returns the updated FileInfo object of the patched file.")
    public static FileInfo applyPatch(
            @AITool("The FileInfo object of the file to be patched. This must contain the original content and the correct lastModified timestamp.")
            FileInfo fileInfo,
            @AITool("The unified diff patch content as a string.")
            String patch
    ) throws IOException, PatchFailedException {
        Path filePath = Paths.get(fileInfo.path);

        if (!Files.exists(filePath)) {
            throw new IOException("File to patch does not exist: " + fileInfo.path);
        }

        // Precondition Check: Ensure the file on disk hasn't changed since it was read.
        long currentLastModified = Files.getLastModifiedTime(filePath).toMillis();
        if (currentLastModified != fileInfo.lastModified) {
            throw new IOException("File modification conflict. The file at " + fileInfo.path +
                                  " was modified on disk after it was read. Expected timestamp: " + fileInfo.lastModified +
                                  ", but found: " + currentLastModified);
        }

        List<String> originalLines = fileInfo.content.lines().toList();
        List<String> patchLines = patch.lines().toList();

        com.github.difflib.patch.Patch<String> diff = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
        List<String> patchedLines = DiffUtils.patch(originalLines, diff);
        String newContent = String.join("\n", patchedLines);

        Files.writeString(filePath, newContent);

        // Return the new, updated FileInfo
        return LocalFiles2.readFile(fileInfo.path);
    }
}
