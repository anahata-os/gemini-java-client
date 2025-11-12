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
import uno.anahata.gemini.internal.PartUtils;

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
        parts.add(Part.fromText("**Instructions Providers**: (enabled ones are included on every turn and run 'after' tool execution)**"));                
        for (SystemInstructionProvider provider : getSystemInstructionProviders()) {
            if (provider.isEnabled()) {
                try {                    
                    long ts = System.currentTimeMillis();
                    List<Part> generated = provider.getInstructionParts(chat);
                    ts = System.currentTimeMillis() - ts;
                    long totalSize = parts.stream().mapToLong(PartUtils::calculateSizeInBytes).sum();
                    parts.add(Part.fromText("**Enabled** Instruction Provider: **" + provider.getDisplayName() + "** (id: **" + provider.getId() + "**): " + generated.size() + " parts, total size: " + totalSize + " took " + ts + " ms."));
                    int idx = 0;
                    for (Part part : generated) {
                        long partSize = PartUtils.calculateSizeInBytes(part);
                        parts.add(Part.fromText("**"+ provider.getId() + ": Part " + idx + " (/" + generated.size() + ") size=" + partSize + "**)"));
                        
                    }
                    //parts.addAll(generated);
                } catch (Exception e) {
                    log.warn("SystemInstructionProvider " + provider.getId() + " threw an exception", e);
                    parts.add(Part.fromText("Error in " + provider.getDisplayName() + ": " + e.getMessage()));
                }
            } else {
                parts.add(Part.fromText("**Disabled** Instruction Provider: **" + provider.getDisplayName() + "** (id: **" + provider.getId() + "**): "));
            }
        }

        return Content.builder().parts(parts).build();
    }
}
