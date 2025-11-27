package uno.anahata.ai.config;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.internal.PartUtils;

/**
 * Manages configuration-related tasks for a Chat session, including the
 * aggregation of system instructions and the construction of the final
 * GenerateContentConfig for API calls.
 */
@Slf4j
@Getter
public class ConfigManager {

    private final Chat chat;
    //private final List<ContextProvider> contextProviders;

    public ConfigManager(Chat chat) {
        this.chat = chat;
        // Get the single, authoritative list of providers from the config.
        //this.contextProviders = chat.getConfig().getContextProviders();
    }

    /**
     * Gets a list of all enabled context providers that match the given
     * position.
     *
     * @param position The desired context position.
     * @return A list of matching enabled providers.
     */
    public List<ContextProvider> getContextProviders(ContextPosition position, boolean enabled) {
        return chat.getConfig().getContextProviders().stream()
                .filter(p -> p.isEnabled() && p.getPosition() == position)
                .collect(Collectors.toList());
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
        parts.add(Part.fromText("These are your system instructions in order of "
                + "importance, you must follow the instructions given by each "
                + "provider in the priority given by the order in which they are listed. "
                + "You must Alert the user of any anomalies in context "
                + "window usage or performance. All enabled instructions providers "
                + "run after tool execution and refresh their data on every turn. The information"
                + "contained in this instructions is dynamic. Do not assume that because a conversation is long the data "
                + "in the following providers is stale. "
                + "Ask the user to enable any disabled "
                + "instruction providers that could give you additional context and help you accomplish tasks."));
        parts.add(Part.fromText("**Instructions Providers**: (enabled ones are included on every turn and run 'after' tool execution)**"));

        //List<ContextProvider> systemInstructionProviders = getContextProviders(ContextPosition.SYSTEM_INSTRUCTIONS, true);

        parts.addAll(chat.getContentFactory().produceParts(ContextPosition.SYSTEM_INSTRUCTIONS));

        return Content.builder().role("system").parts(parts).build();
    }
}
