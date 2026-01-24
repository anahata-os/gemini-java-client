/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import java.awt.Image;
import java.net.URL;
import javax.swing.ImageIcon;

/**
 * A utility class for handling icons.
 */
public class IconUtils {

    /**
     * Loads an icon from the classpath resources and scales it to a 24x24 size.
     *
     * @param name The name of the icon file (e.g., "attach.png").
     * @return A scaled ImageIcon, or null if the resource is not found.
     */
    public static ImageIcon getIcon(String name) {
        return getIcon(name, 24);
    }

    /**
     * Loads an icon from the classpath resources and scales it to the specified size.
     *
     * @param name The name of the icon file.
     * @param size The size (width and height) to scale the icon to.
     * @return A scaled ImageIcon, or null if the resource is not found.
     */
    public static ImageIcon getIcon(String name, int size) {
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