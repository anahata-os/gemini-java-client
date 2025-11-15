package uno.anahata.gemini.functions.spi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A helper class to print classpaths in a readable, grouped format.
 */
public class ClasspathPrinter {

    /**
     * Prints the given classpath string, grouping JARs by their parent directory.
     *
     * @param classpath The classpath string to format.
     * @return A formatted string representation of the classpath.
     */
    public static String prettyPrint(String classpath) {
        if (classpath == null || classpath.isEmpty()) {
            return "Classpath is empty.";
        }

        Map<String, List<String>> directoryJarMap = new TreeMap<>();
        String[] entries = classpath.split(File.pathSeparator);

        for (String entry : entries) {
            File file = new File(entry);
            if (file.exists()) {
                String parent = file.getParent();
                if (parent == null) {
                    parent = file.isFile() ? "Root" : file.getAbsolutePath();
                }
                directoryJarMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(file.getName());
            }
        }

        StringBuilder sb = new StringBuilder("Classpath Details:\n");
        for (Map.Entry<String, List<String>> entry : directoryJarMap.entrySet()) {
            sb.append("\n[DIR] ").append(entry.getKey()).append("\n");
            for (String jarName : entry.getValue()) {
                sb.append("  - ").append(jarName).append("\n");
            }
        }
        return sb.toString();
    }
}
