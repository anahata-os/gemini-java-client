package uno.anahata.ai.internal;

import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;

/**
 *
 * @author pablo
 */
@Slf4j
public class PartUtils {

    private static final Tika TIKA = new Tika();
    private static final Gson GSON = GsonUtils.getGson();

    public static Part toPart(File file) throws IOException {
        byte[] barr = java.nio.file.Files.readAllBytes(file.toPath());
        String mimeType = TIKA.detect(file);
        log.info("TIKA Detected {} for file {}", mimeType, file);

        Blob blob = Blob.builder()
                .data(barr)
                .mimeType(mimeType)
                .build();
        return Part.builder().inlineData(blob).build();
    }

    public static String toString(Blob blob) {
        return new StringBuilder(blob.mimeType().get())
                .append(" ")
                .append(blob.data().get().length)
                .append(" bytes")
                .toString();
    }
    
    public static long calculateSizeInBytes(Part part) {
        if (part.text().isPresent()) {
            return part.text().get().getBytes(StandardCharsets.UTF_8).length;
        }
        if (part.inlineData().isPresent()) {
            return part.inlineData().get().data().get().length;
        }
        if (part.functionCall().isPresent()) {
            return GSON.toJson(part.functionCall().get()).getBytes(StandardCharsets.UTF_8).length;
        }
        if (part.functionResponse().isPresent()) {
            return GSON.toJson(part.functionResponse().get()).getBytes(StandardCharsets.UTF_8).length;
        }
        if (part.executableCode().isPresent()) {
            return GSON.toJson(part.executableCode().get()).getBytes(StandardCharsets.UTF_8).length;
        }
        if (part.codeExecutionResult().isPresent()) {
            return GSON.toJson(part.codeExecutionResult().get()).getBytes(StandardCharsets.UTF_8).length;
        }
        return 0;
    }
    
    /**
     * Estimates the token count of a Part using a simple heuristic (bytes / 4).
     * @param part The part to analyze.
     * @return The approximate number of tokens.
     */
    public static int calculateApproxTokenSize(Part part) {
        return (int) (calculateSizeInBytes(part) / 4);
    }
}
