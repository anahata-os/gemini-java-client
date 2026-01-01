/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Utility class for file type detection and content extraction using Apache Tika.
 * <p>
 * This class provides methods to identify the MIME type of a file and to
 * extract its text content, supporting a wide range of formats (PDF, DOCX, etc.).
 * </p>
 */
public final class TikaUtils {

    private static final Tika TIKA = new Tika();

    /**
     * Detects the MIME type of a given file.
     *
     * @param file The file to inspect.
     * @return The detected MIME type (e.g., "image/png", "application/pdf").
     * @throws Exception if an error occurs during detection.
     */
    public static String detectMimeType(File file) throws Exception {
        return TIKA.detect(file);
    }

    /**
     * Detects the file type and parses the text content from a given file.
     * <p>
     * This method uses Tika's auto-detection to choose the appropriate parser
     * for the file format.
     * </p>
     *
     * @param file The file to parse.
     * @return The extracted text content.
     * @throws Exception if an error occurs during parsing.
     */
    public static String detectAndParse(File file) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 for no write limit
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = new FileInputStream(file)) {
            parser.parse(stream, handler, metadata, context);
            return handler.toString();
        }
    }
}