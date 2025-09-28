package uno.anahata.gemini.blob;

import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import org.apache.tika.Tika;
import uno.anahata.gemini.ui.GeminiPanel;

/**
 *
 * @author pablo
 */
public class PartUtils {

    private static final Logger logger = Logger.getLogger(PartUtils.class.getName());
        
    private static final Tika TIKA = new Tika();

    public static Blob toBlob(File file) throws IOException {
        byte[] barr = java.nio.file.Files.readAllBytes(file.toPath());
        String mimeType = TIKA.detect(file);
        logger.info("TIKA Detected " + mimeType + " for file " + file);
        
        Blob blob = Blob.builder()
                .data(barr)
                //.displayName(file.getAbsolutePath())
                .mimeType(mimeType)
                .build();
        return blob;
    }

    public static Part toPart(File file) throws IOException {
        return Part.builder().inlineData(toBlob(file)).build();
    }
    
    public static String toString(Blob blob)  {
        return new StringBuilder(blob.mimeType().get())
                .append(" ")
                .append(blob.data().get().length)
                .append(" bytes")
                .toString();
        
    }
}
