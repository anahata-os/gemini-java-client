package uno.anahata.gemini.internal;

import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;

/**
 *
 * @author pablo
 */
@Slf4j
public class PartUtils {

    private static final Tika TIKA = new Tika();

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
}
