package uno.anahata.gemini.ui;

import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uno.anahata.gemini.GeminiAPI;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.ui.functions.ScreenCapture;

/**
 * A simple, concrete GeminiConfig for standalone Swing applications.
 */
public class StandaloneSwingGeminiConfig extends GeminiConfig {
    
    private final GeminiAPI api = new GeminiAPI(getWorkingFolder());

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
        List ret = new ArrayList();
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
}
