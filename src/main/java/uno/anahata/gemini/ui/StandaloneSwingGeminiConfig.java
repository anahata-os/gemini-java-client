package uno.anahata.gemini.ui;

import com.google.genai.types.Part;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import uno.anahata.gemini.GeminiAPI;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.ui.functions.ScreenCapture;

/**
 * A simple, concrete GeminiConfig for standalone Swing applications.
 */
public class StandaloneSwingGeminiConfig extends GeminiConfig {
    
    private final GeminiAPI api = new GeminiAPI(this);

    @Override
    public GeminiAPI getApi() {
        return api;
    }

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
    public List<Part> getHostSpecificSystemInstructionParts() {
        String context = "You are running in a standalone Java Swing application with a single JFrame. "
                       + "Your user interface is a `GeminiPanel` hosted within a `JFrame`. "
                       + "Your capabilities are focused on general-purpose interactions with the local operating system (like file manipulation and command execution) "
                       + "and the ability to compile and execute Java code within the application's own JVM.";
        return Collections.singletonList(Part.fromText(context));
    }

    public UITheme getTheme() {
        return new UITheme();
    }

    @Getter
    public static class UITheme {
        // General
        public Color fontColor = Color.BLACK;
        public Font monoFont = new Font("SF Mono", Font.PLAIN, 14);

        // Role-specific colors
        public Color userHeaderBg = new Color(212, 237, 218);
        public Color userContentBg = new Color(233, 247, 239);
        public Color userHeaderFg = new Color(21, 87, 36);
        public Color userBorder = new Color(144, 198, 149);

        public Color modelHeaderBg = new Color(221, 234, 248);
        public Color modelContentBg = new Color(240, 248, 255);
        public Color modelHeaderFg = new Color(0, 123, 255);
        public Color modelBorder = new Color(160, 195, 232); // Restored Blue Border

        public Color toolHeaderBg = new Color(242, 238, 247);
        public Color toolContentBg = new Color(250, 248, 252);
        public Color toolHeaderFg = new Color(80, 60, 100);
        public Color toolBorder = new Color(200, 180, 220); // Restored Purple/Violet Border
        
        public Color defaultHeaderBg = Color.WHITE;
        public Color defaultContentBg = new Color(248, 249, 250);
        public Color defaultBorder = Color.LIGHT_GRAY;

        // Function Call/Response
        public Color functionCallBg = new Color(28, 37, 51);
        public Color functionCallFg = new Color(0, 229, 255);
        public Color functionResponseBg = Color.BLACK;
        public Color functionResponseFg = new Color(0, 255, 0);
        public Color functionErrorBg = new Color(51, 28, 28);
        public Color functionErrorFg = new Color(255, 80, 80); // Brighter Red for better visibility
    }
}
