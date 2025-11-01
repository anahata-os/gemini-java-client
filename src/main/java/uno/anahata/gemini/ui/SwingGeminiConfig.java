package uno.anahata.gemini.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.systeminstructions.SystemInstructionProvider;
import uno.anahata.gemini.ui.functions.spi.ScreenCapture;
import uno.anahata.gemini.ui.instructions.ScreenInstructionsProvider;

/**
 * A simple, concrete GeminiConfig for standalone Swing applications.
 */
public class SwingGeminiConfig extends GeminiConfig {
    
    @Override
    public String getApplicationInstanceId() {
        return "standalone";
    }

    @Override
    public List<Class<?>> getAutomaticFunctionClasses() {
        List<Class<?>> ret = new ArrayList<>();
        ret.add(ScreenCapture.class);
        return ret;
    }
    
    @Override
    public List<SystemInstructionProvider> getApplicationSpecificInstructionProviders() {
        List<SystemInstructionProvider> providers = new ArrayList<>();
        //providers.add(new ScreenInstructionsProvider());
        return providers;
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
    }
}
