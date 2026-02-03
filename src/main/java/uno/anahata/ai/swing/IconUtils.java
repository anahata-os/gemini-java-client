/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import java.awt.Image;
import java.net.URL;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * A utility class for handling icons.
 */
public class IconUtils {

    /**
     * Loads an icon from the classpath resources and scales it to a 24x24 size.
     *
     * @param name The name of the icon file (e.g., "attach.png").
     * @return A scaled Icon, or null if the resource is not found.
     */
    public static Icon getIcon(String name) {
        return getIcon(name, 24);
    }

    /**
     * Loads an icon from the classpath resources and scales it to the specified size.
     *
     * @param name The name of the icon file.
     * @param size The size (width and height) to scale the icon to.
     * @return A scaled Icon, or null if the resource is not found.
     */
    public static Icon getIcon(String name, int size) {
        // Map specific names to ThemedIcon types
        if ("restart.png".equals(name)) return new ThemedIcon(ThemedIcon.Type.RESTART, size);
        if ("attach.png".equals(name)) return new ThemedIcon(ThemedIcon.Type.ATTACH, size);
        if ("desktop_screenshot.png".equals(name)) return new ThemedIcon(ThemedIcon.Type.SCREENSHOT, size);
        if ("capture_frames.png".equals(name)) return new ThemedIcon(ThemedIcon.Type.FRAMES, size);
        if ("compress.png".equals(name)) return new ThemedIcon(ThemedIcon.Type.LIVE, size);
        
        // Fallback to standard PNG loading
        try {
            URL resource = IconUtils.class.getResource("/icons/" + name);
            if (resource == null) {
                return null;
            }
            ImageIcon originalIcon = new ImageIcon(resource);
            Image scaledImage = originalIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaledImage);
        } catch (Exception e) {
            return null;
        }
    }
}
