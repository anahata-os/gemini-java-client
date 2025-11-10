package uno.anahata.gemini.config;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.config.systeminstructions.SystemInstructionProvider;

/**
 * Manages configuration-related tasks for a GeminiChat session, including the
 * aggregation of system instructions and the construction of the final
 * GenerateContentConfig for API calls.
 */
@Slf4j
@Getter
public class ConfigManager {

    private final GeminiChat chat;
    private final List<SystemInstructionProvider> systemInstructionProviders;

    public ConfigManager(GeminiChat chat) {
        this.chat = chat;
        // Take a defensive copy of the providers from the main config
        this.systemInstructionProviders = new ArrayList<>(chat.getConfig().getSystemInstructionProviders());
    }

    /**
     * Constructs the GenerateContentConfig for an API call, assembling system
     * instructions and configuring tools based on the current chat state.
     *
     * @return The fully configured GenerateContentConfig object.
     */
    public GenerateContentConfig makeGenerateContentConfig() {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
                .systemInstruction(buildSystemInstructions())
                .temperature(0f);

        if (chat.isFunctionsEnabled()) {
            builder
                    .tools(chat.getFunctionManager().getFunctionTool())
                    .toolConfig(chat.getFunctionManager().getToolConfig());
        } else {
            Tool googleTools = Tool.builder().googleSearch(GoogleSearch.builder().build()).build();
            builder.tools(googleTools);
        }

        return builder.build();
    }

    /**
     * Aggregates system instruction parts from all enabled providers.
     *
     * @return A Content object containing all system instructions.
     */
    private Content buildSystemInstructions() {
        List<Part> parts = new ArrayList<>();

        for (SystemInstructionProvider provider : getSystemInstructionProviders()) {
            if (provider.isEnabled()) {
                try {
                    List<Part> generated = provider.getInstructionParts(chat);
                    parts.add(Part.fromText("Instruction Provider: " + provider.getDisplayName() + " (id: " + provider.getId() + "): (" + generated.size() + " parts)"));
                    parts.addAll(generated);
                } catch (Exception e) {
                    log.warn("SystemInstructionProvider " + provider.getId() + " threw an exception", e);
                    parts.add(Part.fromText("Error in " + provider.getDisplayName() + ": " + e.getMessage()));
                }
            }
        }

        return Content.builder().parts(parts).build();
    }
}
