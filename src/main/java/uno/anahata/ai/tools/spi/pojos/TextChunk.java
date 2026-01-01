/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.spi.pojos;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A data transfer object representing a processed chunk of text.
 * <p>
 * This class holds a segment of text along with metadata about its position
 * in the original source, the total line count, and any filtering or
 * truncation that was applied.
 * </p>
 */
@Getter
@AllArgsConstructor
public class TextChunk {
    /** The total number of lines in the original source text. */
    private final int totalLineCount;
    
    /** The number of lines that matched the filter pattern, if any. */
    private final Integer matchingLineCount;
    
    /** The 1-based line number of the first line in this chunk. */
    private final int fromLine;
    
    /** The 1-based line number of the last line in this chunk. */
    private final int toLine;
    
    /** The number of lines in this chunk that were truncated due to length limits. */
    private final int truncatedLinesCount;
    
    /** The actual processed text content of the chunk. */
    private final String text;
}