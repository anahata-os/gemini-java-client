package uno.anahata.gemini.spi.providers;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import uno.anahata.gemini.spi.SystemInstructionProvider;

public class CoreDynamicEnvProvider implements SystemInstructionProvider {

    private boolean enabled = true;

    @Override
    public String getId() {
        return "core-dynamic-env";
    }

    @Override
    public String getDisplayName() {
        return "Dynamic Environment";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public List<Part> getInstructionParts() {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Dynamic Environment Details\n");
        sb.append("# ---------------------------\n");

        // System Properties
        sb.append("- **System Properties**: ");
        Properties props = System.getProperties();
        sb.append(props.toString());
        sb.append("\n");

        // Environment Variables
        sb.append("- **Environment variables**: ");
        Map<String, String> env = System.getenv();
        sb.append(env.toString());
        sb.append("\n");

        return Collections.singletonList(Part.fromText(sb.toString()));
    }
}
