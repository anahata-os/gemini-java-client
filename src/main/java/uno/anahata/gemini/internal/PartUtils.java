package uno.anahata.gemini.internal;

import com.google.genai.types.Blob;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;

/**
 *
 * @author pablo
 */
@Slf4j
public class PartUtils {

    private static final Tika TIKA = new Tika();

    public static Blob toBlob(File file) throws IOException {
        byte[] barr = java.nio.file.Files.readAllBytes(file.toPath());
        String mimeType = TIKA.detect(file);
        log.info("TIKA Detected " + mimeType + " for file " + file);

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

    public static String toString(Blob blob) {
        return new StringBuilder(blob.mimeType().get())
                .append(" ")
                .append(blob.data().get().length)
                .append(" bytes")
                .toString();

    }

    public static String summarize(Part p) {
        final int MAX_LENGTH = 108;
        StringBuilder sb = new StringBuilder();

        if (p.text().isPresent()) {
            sb.append("[Text]:").append(StringUtils.truncate(p.text().get(), MAX_LENGTH));
        } else if (p.functionCall().isPresent()) {
            FunctionCall fc = p.functionCall().get();
            sb.append("[FunctionCall]:").append(fc.name().get());

            if (fc.args().isPresent()) {
                Map<String, Object> args = fc.args().get();
                List<Map.Entry<String, Object>> sortedArgs = new ArrayList<>(args.entrySet());
                // Sort by the length of the value's string representation
                sortedArgs.sort(Comparator.comparingInt(entry -> String.valueOf(entry.getValue()).length()));

                String argsString = sortedArgs.stream()
                        .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                        .collect(Collectors.joining(", ", "(", ")"));

                sb.append(":").append(argsString);
            }

            return StringUtils.truncate(sb.toString(), MAX_LENGTH);

        } else if (p.functionResponse().isPresent()) {
            FunctionResponse fr = p.functionResponse().get();
            String responseTruncated = StringUtils.truncate(fr.response().get().toString(), MAX_LENGTH);
            sb.append("[FunctionResponse]:").append(fr.name().get()).append(":").append(responseTruncated);
        } else if (p.inlineData().isPresent()) {
            Blob b = p.inlineData().get();
            sb.append("[Blob]:").append(toString(b));
        } else if (p.codeExecutionResult().isPresent()) {
            CodeExecutionResult cer = p.codeExecutionResult().get();
            String responseTruncated = StringUtils.truncate(cer.outcome().get() + ":" + cer.output().get(), MAX_LENGTH);
            sb.append("[CodeExecutionResult]:").append(responseTruncated);
        } else if (p.executableCode().isPresent()) {
            ExecutableCode ec = p.executableCode().get();
            String codeTruncated = StringUtils.truncate(ec.code().get(), MAX_LENGTH);
            sb.append("[ExecutableCode]:").append(codeTruncated);
        } else {
            sb.append("[Unknown part type]:").append(StringUtils.truncate(p.toString(), MAX_LENGTH));
        } 

        return sb.toString();
    }
}
