/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.gemini.ui;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import uno.anahata.gemini.GeminiConfig;

/**
 *
 * @author pablo
 */
public class UICapture {

    private static final Logger logger = Logger.getLogger(UICapture.class.getName());
    public static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
    
    public static final File SCREENSHOTS_DIR = GeminiConfig.getWorkingFolder("screenshots");
    
    public static List<File> screenshotAllScreenDevices() {
        List<File> ret = new ArrayList<>();
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();

            for (int i = 0; i < screens.length; i++) {
                Rectangle screenBounds = screens[i].getDefaultConfiguration().getBounds();
                BufferedImage screenshot = new Robot().createScreenCapture(screenBounds);

                String timestamp = TIMESTAMP_FORMAT.format(new Date());
                String filename = "screen-" + i + "-" + timestamp;
                File tempFile = new File (SCREENSHOTS_DIR, filename);
                tempFile.deleteOnExit();
                ImageIO.write(screenshot, "png", tempFile);
                ret.add(tempFile);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Screenshot capture failed", ex);
            JOptionPane.showMessageDialog(null, "Could not take screenshot: " + ex.getMessage(), "Screenshot Error", JOptionPane.ERROR_MESSAGE);
        }
        return ret;
    }

    public static List<File> screenshotAllJFrames() {
        List<File> ret = new ArrayList<>();
        try {
            java.awt.Frame[] frames = java.awt.Frame.getFrames();
            int capturedCount = 0;
            for (java.awt.Frame frame : frames) {
                if (frame instanceof JFrame && frame.isShowing()) {
                    JFrame jframe = (JFrame) frame;
                    BufferedImage image = new BufferedImage(jframe.getWidth(), jframe.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    jframe.paint(image.getGraphics());

                    String title = jframe.getTitle();
                    if (title == null || title.trim().isEmpty()) {
                        title = "Untitled";
                    }
                    // Sanitize title for use in filename
                    String sanitizedTitle = title.replaceAll("[^a-zA-Z0-9.-]", "_");
                    String timestamp = TIMESTAMP_FORMAT.format(new Date());
                    String fileName = sanitizedTitle + "-" + timestamp;
                    File tempFile = new File (SCREENSHOTS_DIR, fileName);
                    tempFile.deleteOnExit();
                    ImageIO.write(image, "png", tempFile);
                    ret.add(tempFile);
                    capturedCount++;
                }
            }
            if (capturedCount == 0) {
                JOptionPane.showMessageDialog(null, "No visible application frames were found to capture.", "Capture Info", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "JFrame capture failed", ex);
            JOptionPane.showMessageDialog(null, "Could not capture frame: " + ex.getMessage(), "Capture Error", JOptionPane.ERROR_MESSAGE);
        }

        return ret;

    }
}
