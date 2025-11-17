package uno.anahata.ai.swing.render;

import com.google.genai.types.*;

public enum PartType {
    TEXT,
    FUNCTION_CALL,
    FUNCTION_RESPONSE,
    BLOB,
    CODE_EXECUTION_RESULT,
    EXECUTABLE_CODE,
    UNKNOWN;

    public static PartType from(Part part) {
        if (part.text().isPresent()) {
            return TEXT;
        }
        if (part.functionCall().isPresent()) {
            return FUNCTION_CALL;
        }
        if (part.functionResponse().isPresent()) {
            return FUNCTION_RESPONSE;
        }
        if (part.inlineData().isPresent()) {
            return BLOB;
        }
        if (part.codeExecutionResult().isPresent()) {
            return CODE_EXECUTION_RESULT;
        }
        if (part.executableCode().isPresent()) {
            return EXECUTABLE_CODE;
        }
        return UNKNOWN;
    }
}
