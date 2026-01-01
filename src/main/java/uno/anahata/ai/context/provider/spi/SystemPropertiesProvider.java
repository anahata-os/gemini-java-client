/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.internal.TextUtils;

/**
 * A context provider that injects the current JVM system properties into the
 * model's prompt.
 * <p>
 * Properties are grouped by prefix (e.g., java., netbeans., user.) for better
 * readability and context organization.
 * </p>
 */
public class SystemPropertiesProvider extends ContextProvider {

    @Override
    public String getId() {
        return "core-system-properties";
    }

    @Override
    public String getDisplayName() {
        return "System Properties";
    }

    @Override
    public List<Part> getParts(Chat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- **System Properties**:\n");

        Properties props = System.getProperties();
        Map<String, String> sortedProps = new TreeMap<>();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            sortedProps.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }

        Map<String, List<Map.Entry<String, String>>> grouped = new LinkedHashMap<>();
        // Define the prefixes to group by, in the desired order.
        List<String> prefixes = Arrays.asList("java.", "netbeans.", "sun.", "os.", "user.", "flatlaf.", "org.");
        
        // Group properties by prefix
        for (Map.Entry<String, String> entry : sortedProps.entrySet()) {
            String key = entry.getKey();
            String foundPrefix = prefixes.stream()
                                         .filter(key::startsWith)
                                         .findFirst()
                                         .orElse("other");
            grouped.computeIfAbsent(foundPrefix, k -> new ArrayList<>()).add(entry);
        }

        // Append grouped properties to the StringBuilder
        for (String prefix : prefixes) {
            if (grouped.containsKey(prefix)) {
                sb.append("  **").append(prefix).append("**:\n");
                for (Map.Entry<String, String> entry : grouped.get(prefix)) {
                    sb.append("    ").append(entry.getKey()).append("=").append(TextUtils.formatValue(entry.getValue())).append("\n");
                }
            }
        }
        
        // Append any remaining properties under "Other"
        if (grouped.containsKey("other")) {
            sb.append("  *Other*:\n");
            for (Map.Entry<String, String> entry : grouped.get("other")) {
                sb.append("    ").append(entry.getKey()).append("=").append(TextUtils.formatValue(entry.getValue())).append("\n");
            }
        }

        return Collections.singletonList(Part.fromText(sb.toString().trim()));
    }
}