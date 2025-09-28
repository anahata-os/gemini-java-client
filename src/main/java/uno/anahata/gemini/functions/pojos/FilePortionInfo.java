package uno.anahata.gemini.functions.pojos;

/**
 * A POJO that represents a specific portion of a file, including metadata.
 * It extends FileInfo to inherit common file properties.
 * @author AI
 */
public class FilePortionInfo extends FileInfo {
    /** The starting line number of the content (inclusive, 1-based). */
    public int startLine;

    /** The ending line number of the content (inclusive, 1-based). */
    public int endLine;

    /** The total number of lines in the file. */
    public int totalLines;

    public FilePortionInfo() {
        super();
    }

    public FilePortionInfo(String path, String content, long lastModified, long size, int startLine, int endLine, int totalLines) {
        super(path, content, lastModified, size);
        this.startLine = startLine;
        this.endLine = endLine;
        this.totalLines = totalLines;
    }

    @Override
    public String toString() {
        return "FilePortionInfo{" +
               "path='" + path + '\'' +
               ", lastModified=" + lastModified +
               ", size=" + size +
               ", content.length=" + (content != null ? content.length() : 0) +
               ", startLine=" + startLine +
               ", endLine=" + endLine +
               ", totalLines=" + totalLines +
               '}';
    }
}
