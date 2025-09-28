package uno.anahata.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponseUsageMetadata;

public interface ContextListener {

    void contentAdded(GenerateContentResponseUsageMetadata usage, Content c);

    void contextCleared();

    void contextModified();
}
