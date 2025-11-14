package uno.anahata.gemini.ui;

import com.google.genai.types.Part;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.config.ChatConfig;
import uno.anahata.gemini.content.ContextProvider;
import uno.anahata.gemini.internal.PartUtils;
import uno.anahata.gemini.media.functions.spi.AudioTool;
import uno.anahata.gemini.status.ChatStatus;
import uno.anahata.gemini.ui.context.provider.ApplicationFramesContextProvider;
import uno.anahata.gemini.ui.functions.spi.ScreenCapture;

/**
 * A simple, concrete ChatConfig for standalone Swing applications.
 */
@Slf4j
@Getter
@Setter
public class SwingChatConfig extends ChatConfig {
    
    private boolean audioFeedbackEnabled = true; // Default to ON, no persistence.

    @Override
    public String getSessionId() {
        return "standalone";
    }

    @Override
    public List<Class<?>> getToolClasses() {
        List<Class<?>> ret = super.getToolClasses();
        ret.add(ScreenCapture.class);
        ret.add(AudioTool.class);
        return ret;
    }

    @Override
    public List<ContextProvider> getContextProviders() {
        List<ContextProvider> ret = super.getContextProviders(); 
        ret.add(new ApplicationFramesContextProvider());
        return ret;
    }

    public Color getColor(ChatStatus status) {
        switch (status) {
            case API_CALL_IN_PROGRESS:
                return new Color(0, 123, 255); // BLUE
            case TOOL_EXECUTION_IN_PROGRESS:
                return new Color(128, 0, 128); // PURPLE
            case WAITING_WITH_BACKOFF:
                return new Color(255, 0, 0); // RED
            case IDLE_WAITING_FOR_USER:
                return new Color(0, 128, 0); // GREEN
            default:
                return Color.BLACK;
        }
    }

    public UITheme getTheme() {
        return new UITheme();
    }

    @Getter
    public static class UITheme {
        // General
        private final Color fontColor = Color.BLACK;
        private final Font monoFont = new Font("SF Mono", Font.PLAIN, 14);

        // Role-specific colors
        private final Color userHeaderBg = new Color(212, 237, 218);
        private final Color userContentBg = new Color(233, 247, 239);
        private final Color userHeaderFg = new Color(21, 87, 36);
        private final Color userBorder = new Color(144, 198, 149);

        private final Color modelHeaderBg = new Color(221, 234, 248);
        private final Color modelContentBg = new Color(240, 248, 255);
        private final Color modelHeaderFg = new Color(0, 123, 255);
        private final Color modelBorder = new Color(160, 195, 232); // Restored Blue Border

        private final Color toolHeaderBg = new Color(223, 213, 235); // More saturated purple
        private final Color toolContentBg = new Color(250, 248, 252);
        private final Color toolHeaderFg = new Color(80, 60, 100);
        private final Color toolBorder = new Color(200, 180, 220); // Restored Purple/Violet Border

        private final Color defaultHeaderBg = Color.WHITE;
        private final Color defaultContentBg = new Color(248, 249, 250);
        private final Color defaultBorder = Color.LIGHT_GRAY;

        // Function Call/Response
        private final Color functionCallBg = new Color(28, 37, 51);
        private final Color functionCallFg = new Color(0, 229, 255);
        private final Color functionResponseBg = Color.BLACK;
        private final Color functionResponseFg = new Color(0, 255, 0);
        private final Color functionErrorBg = new Color(51, 28, 28);
        private final Color functionErrorFg = new Color(255, 80, 80); // Brighter Red for better visibility

        // Grounding Metadata (Outer Block)
        private final Color groundingHeaderBg = new Color(200, 230, 201); // #D4EDDA - Aligned with userHeaderBg
        private final Color groundingContentBg = new Color(233, 247, 239); // #E9F7EF - Aligned with userContentBg

        // Grounding Details (Inner Sections: Supporting Text, Sources, Search Suggestions)
        private final Color groundingDetailsHeaderBg = new Color(212, 237, 218); // #C8E6C9 - Aligned with chipBackground
        private final Color groundingDetailsContentBg = new Color(233, 247, 239); // #E9F7EF - Aligned with userContentBg
        private final Color groundingDetailsHeaderColor = Color.BLACK;

        // Grounding Chips (Keeping existing for now as requested)
        private final Color chipBackground = new Color(200, 230, 201); // #C8E6C9 - Anahata Green
        private final Color chipBorder = new Color(210, 210, 210);
        private final Color chipText = new Color(0, 77, 64); // #004D40 - Dark Anahata Green
    }
}
