/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.tools.spi;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;
import uno.anahata.ai.tools.MultiPartResponse;
import uno.anahata.ai.swing.UICapture;

/**
 * Tool provider for capturing screenshots of the host system and application windows.
 */
public class ScreenCapture {

    /**
     * Takes a screenshot of a specific graphics device.
     * @param deviceIdx The index of the device to capture.
     * @return The absolute path to the captured screenshot file.
     * @throws Exception if the capture fails.
     */
    @AIToolMethod(value = "Takes a screenshot of a specific host system graphics device as in GraphicsEnvironment.getScreenDevices() "
            + "And returns a String with the absolute path ")
    public static String takeDeviceScreenshot(
            @AIToolParam(value = "The device index in GraphicsEnvironment.getScreenDevices()")
            int deviceIdx) throws Exception {
        File file = UICapture.screenshotScreenDevice(deviceIdx);
        return file.getAbsolutePath();
    }

    /**
     * Takes a screenshot of all visible application windows.
     * @return A MultiPartResponse containing the paths to the captured screenshots.
     * @throws IOException if the capture fails.
     */
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
