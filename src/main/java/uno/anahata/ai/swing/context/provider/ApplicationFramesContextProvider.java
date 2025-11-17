package uno.anahata.ai.swing.context.provider;

import com.google.genai.types.Part;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.internal.PartUtils;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.swing.UICapture;

/**
 * A ContextProvider that captures the screen and provides it as image parts.
 * Currently not supported by gemini or the genai-sdk.
 */
@Slf4j
public class ApplicationFramesContextProvider extends ContextProvider {

    public ApplicationFramesContextProvider() {
        super(ContextPosition.AUGMENTED_WORKSPACE, false);
    }
    
    

    @Override
    public String getId() {
        return "ui-screen-capture";
    }

    @Override
    public String getDisplayName() {
        return "Live Screen Capture";
    }

    @Override
    public List<Part> getParts(Chat chat) {
        

        List<Part> parts = new ArrayList<>();
        try {
            List<File> screenshotFiles = UICapture.screenshotAllJFrames();
            if (screenshotFiles.isEmpty()) {
                log.warn("Found no JFrames to capture.");
                Part.fromText("Found no JFrames to capture.");
                return Collections.emptyList();
            }
            
            
            for (File file : screenshotFiles) {
                parts.add(Part.fromText("Live Workspace Screenshot (just-in-time): " + file));
                parts.add(PartUtils.toPart(file));
            }
            log.info("Successfully added {} screen capture(s) to system instructions.", screenshotFiles.size());
            
        } catch (Exception e) {
            log.error("Error capturing screen for system instructions", e);
            // Return a part with the error message so the model is aware of the failure.
            parts.add(Part.fromText("Error capturing screen: " + e.getMessage()));
        }
        
        return parts;
    }
}
