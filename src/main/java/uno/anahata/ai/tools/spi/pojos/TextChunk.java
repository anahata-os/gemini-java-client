/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.spi.pojos;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TextChunk {
    private final int totalLineCount;
    private final Integer matchingLineCount;
    private final int fromLine;
    private final int toLine;
    private final int truncatedLinesCount;
    private final String text;
}