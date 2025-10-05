package uno.anahata.gemini.functions.spi;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.PatchFailedException;
import uno.anahata.gemini.functions.pojos.FileInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * A dedicated tool for applying diff patches to files, ensuring atomicity and safety.
 * Too difficult for an LLM to create a well formed pat
 * @author AI
 */
@Deprecated
public class Patch {
    
    public static FileInfo applyPatch(
            String path,
            long lastModified,
            String patch
    ) throws IOException, PatchFailedException {
        Path filePath = Paths.get(path);

        if (!Files.exists(filePath)) {
            throw new IOException("File to patch does not exist: " + path);
        }

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

        return LocalFiles.readFile(path);
    }
}
