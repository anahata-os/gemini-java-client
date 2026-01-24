/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.config;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
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
 * Manages configuration-related tasks for a {@link Chat} session.
 * <p>
 * This class is responsible for aggregating system instructions from various
 * {@link ContextProvider}s and constructing the final {@link GenerateContentConfig}
 * used for Gemini API calls.
 * </p>
 */
@Slf4j
@Getter
public class ConfigManager {

    private final Chat chat;

    /**
     * Constructs a new ConfigManager for the given Chat instance.
     *
     * @param chat The Chat instance to manage configuration for.
     */
    public ConfigManager(Chat chat) {
        this.chat = chat;
    }

    /**
     * Gets a list of context providers that match the specified position and enabled state.
     *
     * @param position The desired context position (e.g., SYSTEM_INSTRUCTIONS).
     * @param enabled  Whether to filter for enabled or disabled providers.
     * @return A list of matching context providers.
     */
    public List<ContextProvider> getContextProviders(ContextPosition position, boolean enabled) {
        return chat.getConfig().getContextProviders().stream()
                .filter(p -> p.isEnabled() == enabled && p.getPosition() == position)
                .collect(Collectors.toList());
    }

    /**
     * Constructs the {@link GenerateContentConfig} for an API call.
     * <p>
     * This method assembles the system instructions, configures thinking mode,
     * and sets up tools (local functions or Google Search) based on the current
     * chat state.
     * </p>
     *
     * @return The fully configured GenerateContentConfig object.
     */
    public GenerateContentConfig makeGenerateContentConfig() {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
                .systemInstruction(buildSystemInstructions())
                .thinkingConfig(ThinkingConfig.builder().includeThoughts(true))
                .temperature(0f);

        if (chat.isFunctionsEnabled()) {
            builder
                    .tools(chat.getToolManager().getFunctionTool())
                    .toolConfig(chat.getToolManager().getToolConfig());
        } else if (chat.isServerToolsEnabled()) {
            Tool googleTools = Tool.builder().googleSearch(GoogleSearch.builder().build()).build();
            builder.tools(googleTools);
        }

        return builder.build();
    }

    /**
     * Aggregates system instruction parts from all enabled providers.
     *
     * @return A Content object containing the combined system instructions.
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

        parts.addAll(chat.getContentFactory().produceParts(ContextPosition.SYSTEM_INSTRUCTIONS));

        return Content.builder().role("system").parts(parts).build();
    }
}