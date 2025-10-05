package uno.anahata.gemini.ui.functions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import uno.anahata.gemini.internal.PartUtils;
import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.functions.AIToolParam;
import uno.anahata.gemini.functions.MultiPartResponse;
import uno.anahata.gemini.ui.UICapture;

/**
 *
 * @author pablo
 */
public class ScreenCapture {
    
    @AIToolMethod(value = "Takes a screenshot of all host systems graphics devices as in GraphicsEnvironment.getScreenDevices() "
            + "And returns a String with the absolute path ")
    public static String takeDeviceScreenshot(
            @AIToolParam(value = "The device index in GraphicsEnvironment.getScreenDevices()")
                    int deviceIdx) {
        List<File> files = UICapture.screenshotAllScreenDevices();
        String ret = "";
        for (File file : files) {
            ret+= file.getAbsolutePath();
        }
        return ret;
    }
    
    @AIToolMethod(value = "Takes a screenshot of all visible JFrames"
            + " and sends it back to the model with the FunctionResponse")
    public static MultiPartResponse attachWindowCaptures() throws IOException {
        MultiPartResponse mpr = new MultiPartResponse();
        List<File> files = UICapture.screenshotAllJFrames();
        mpr.responseValue = "";
        for (File file : files) {            
            mpr.parts.add(PartUtils.toPart(file));
        }
        return mpr;
    }
}
