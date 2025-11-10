package uno.anahata.gemini.ui.config.systeminstructions.spi;

import com.google.genai.types.Part;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.internal.PartUtils;
import uno.anahata.gemini.config.systeminstructions.SystemInstructionProvider;
import uno.anahata.gemini.ui.UICapture;

/**
 * A SystemInstructionProvider that captures the screen and provides it as image parts.
 */
@Slf4j
public class ScreenInstructionsProvider extends SystemInstructionProvider {

    @Override
    public String getId() {
        return "ui-screen-capture";
    }

    @Override
    public String getDisplayName() {
        return "Live Screen Capture";
    }

    @Override
    public List<Part> getInstructionParts(GeminiChat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        List<Part> parts = new ArrayList<>();
        try {
            List<File> screenshotFiles = UICapture.screenshotAllJFrames();
            if (screenshotFiles.isEmpty()) {
                log.warn("ScreenInstructionsProvider found no JFrames to capture.");
                return Collections.emptyList();
            }
            
            for (File file : screenshotFiles) {
                parts.add(PartUtils.toPart(file));
            }
            log.info("Successfully added {} screen capture(s) to system instructions.", parts.size());
            
        } catch (Exception e) {
            log.error("Error capturing screen for system instructions", e);
            // Return a part with the error message so the model is aware of the failure.
            parts.add(Part.fromText("Error capturing screen: " + e.getMessage()));
        }
        
        return parts;
    }
}
