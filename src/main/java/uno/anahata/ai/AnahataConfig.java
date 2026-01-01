/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import java.io.File;

/**
 * Manages the global configuration and working directory for the Anahata AI framework.
 * <p>
 * This class is responsible for ensuring the existence of the application's
 * root working directory (typically {@code ~/.anahata/ai-assistant}) and
 * providing access to its subfolders.
 * </p>
 * 
 * @author anahata
 */
public class AnahataConfig {
    static {
        File workDir = getWorkingFolder();
        if (!workDir.exists()) {
            workDir.mkdirs();
        } else if (!workDir.isDirectory()) {
            throw new RuntimeException("work.dir is not a directory: " + workDir);
        }
    }
    
    /**
     * Gets a subfolder within the application's root working directory.
     * If the folder does not exist, it is created.
     *
     * @param name The name of the subfolder.
     * @return The subfolder File.
     */
    public static File getWorkingFolder(String name) {
        File f = new File(getWorkingFolder(), name);
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    /**
     * Gets the application's root working directory.
     * <p>
     * The default location is {@code .anahata/ai-assistant} within the user's home directory.
     * </p>
     *
     * @return The root working directory File.
     */
    public static File getWorkingFolder() {
        File f = new File(System.getProperty("user.home") + File.separator + ".anahata" + File.separator + "ai-assistant");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

}