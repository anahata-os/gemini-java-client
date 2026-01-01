/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import java.io.File;

/**
 * Gllobal Anahata Working directory stuff.
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
    
    public static File getWorkingFolder(String name) {
        File f = new File(getWorkingFolder(), name);
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    public static File getWorkingFolder() {
        File f = new File(System.getProperty("user.home") + File.separator + ".anahata" + File.separator + "ai-assistant");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

}