package uno.anahata.gemini.ui.functions.spi;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
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
            ret += file.getAbsolutePath();
        }
        return ret;
    }

    @AIToolMethod(value = "Takes a screenshot of all visible JFrames, saves them to temporary files, "
            + "and returns a response object containing the absolute paths to those files.")
    public static MultiPartResponse attachWindowCaptures() throws IOException {
        List<File> files = UICapture.screenshotAllJFrames();
        List<String> filePaths = files.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
        return new MultiPartResponse(filePaths);
    }
}
