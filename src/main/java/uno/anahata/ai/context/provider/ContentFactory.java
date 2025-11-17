/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.ai.context.provider;

import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.internal.PartUtils;

/**
 *
 * @author pablo
 */
@AllArgsConstructor
@Slf4j
public class ContentFactory {
    
    private final Chat chat;
    
    public List<Part> produceParts(ContextPosition position) {
        List<ContextProvider> providers = chat.getConfigManager().getContextProviders(position, true);
        List<Part> parts = new ArrayList<>();
        for (ContextProvider provider : providers) {
            try {
                long ts = System.currentTimeMillis();
                List<Part> generated = provider.getParts(chat);
                ts = System.currentTimeMillis() - ts;
                long totalSize = generated.stream().mapToLong(PartUtils::calculateSizeInBytes).sum();
                parts.add(Part.fromText(
                        "Provider: **" + provider.getDisplayName() + "** (id: **" + provider.getId() + "**):"
                        + "\nDescription: " + provider.getDescription()
                        + "\nClass: " + provider.getClass() + "\n"
                        + "\nTotal Parts: " + generated.size() + " parts, "
                        + "\nSize: " + totalSize + " (bytes)"
                        + "\nTook: " + ts + " ms."
                ));
                int idx = 0;
                for (Part part : generated) {
                    long partSize = PartUtils.calculateSizeInBytes(part);
                    parts.add(Part.fromText("**" + provider.getId() + ": Part " + idx + " (/" + generated.size() + ") size=" + partSize + "**)"));
                    parts.addAll(generated);
                }
            } catch (Exception e) {
                log.warn("ContextProvider " + provider.getId() + " threw an exception", e);
                parts.add(Part.fromText("Error in " + provider.getDisplayName() + ": " + e.getMessage()));
            }
        }

        // Also list disabled providers for transparency
        for (ContextProvider provider : chat.getConfigManager().getContextProviders(position, false)) {
            parts.add(Part.fromText("**Disabled** Provider: **" + provider.getDisplayName() + "** (id: **" + provider.getId() + "**): " + provider.getDescription()));
        }
        
        return parts;
    }
    
}
