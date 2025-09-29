/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.gemini.ui.functions;

import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import uno.anahata.gemini.internal.PartUtils;
import uno.anahata.gemini.functions.AITool;
import uno.anahata.gemini.functions.MultiPartResponse;
import uno.anahata.gemini.ui.UICapture;

/**
 *
 * @author pablo
 */
public class ScreenCapture {
    
    @AITool(value = "Takes a screenshot of all host systems graphics devices as in GraphicsEnvironment.getScreenDevices() "
            + "And returns a String with the absolute path ")
    public static String takeDeviceScreenshot(
            @AITool(value = "The device index in GraphicsEnvironment.getScreenDevices()")
                    int deviceIdx) {
        List<File> files = UICapture.screenshotAllScreenDevices();
        String ret = "";
        for (File file : files) {
            ret+= file.getAbsolutePath();
        }
        return ret;
    }
    
    //@AITool(value = "Takes a screenshot of all visible JFrames "
            //+ "And returns a String with the absolute path ")
    public static String captureWindows() {
        List<File> files = UICapture.screenshotAllJFrames();
        String ret = "";
        for (File file : files) {
            ret+= file.getAbsolutePath();
        }
        return ret;
    }
    
    @AITool(value = "Takes a screenshot of all visible JFrames"
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
