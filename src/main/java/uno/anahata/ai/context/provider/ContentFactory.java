/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.provider;

import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.internal.PartUtils;

/**
 * A factory for generating content parts from registered {@link ContextProvider}s.
 * <p>
 * This class orchestrates the execution of context providers based on their
 * assigned {@link ContextPosition}. it supports both sequential and parallel
 * execution of providers.
 * </p>
 *
 * @author anahata
 */
@AllArgsConstructor
@Slf4j
public class ContentFactory {
    
    private final Chat chat;
    
    /**
     * Produces a list of content parts from all enabled context providers for the given position.
     *
     * @param position The position in the context where the parts should be inserted.
     * @return A list of generated parts.
     */
    public List<Part> produceParts(ContextPosition position) {
        return produceParts(position, false);
    }
    
    /**
     * Produces a list of content parts from all enabled context providers for the given position.
     *
     * @param position The position in the context where the parts should be inserted.
     * @param parallel If true, providers are executed concurrently using the common ForkJoinPool.
     * @return A list of generated parts, including metadata and content.
     */
    public List<Part> produceParts(ContextPosition position, boolean parallel) {
        List<ContextProvider> providers = chat.getConfigManager().getContextProviders(position, true);
        
        List<List<Part>> results = new ArrayList<>();
        if (parallel) {
            List<CompletableFuture<List<Part>>> futures = providers.stream()
                    .map(provider -> CompletableFuture.supplyAsync(() -> processProvider(provider))
                            .exceptionally(e -> {
                                Throwable cause = e.getCause() != null ? e.getCause() : e;
                                log.error("Critical error during parallel provider execution of " + provider, e);
                                return List.of(Part.fromText("Critical Error: " + cause.getMessage() + " on provider " + provider));
                            }))
                    .collect(Collectors.toList());
            
            for (CompletableFuture<List<Part>> future : futures) {
                try {
                    if (Thread.interrupted()) {
                        log.info("Interruption detected while waiting for context providers. Cancelling remaining tasks.");
                        futures.forEach(f -> f.cancel(true));
                        break;
                    }
                    results.add(future.get());
                } catch (InterruptedException e) {
                    log.info("Thread interrupted while waiting for context providers.");
                    futures.forEach(f -> f.cancel(true));
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    log.error("Error executing context provider future", e);
                }
            }
        } else {
            for (ContextProvider provider : providers) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
                results.add(processProvider(provider));
            }
        }

        // Flatten the results
        List<Part> parts = results.stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(ArrayList::new));

        // List disabled providers (only once)
        for (ContextProvider provider : chat.getConfigManager().getContextProviders(position, false)) {
            parts.add(Part.fromText("**Disabled** Context Provider: id:**" + provider.getId() + "** (display name: " + provider.getDisplayName()+ "): description: " + provider.getDescription()));
        }
        
        return parts;
    }
    
    /**
     * Executes a single provider, calculates timing and size, and formats the output parts.
     * This method is designed to be safe for both sequential and parallel execution.
     *
     * @param provider The context provider to execute.
     * @return A list of parts containing the provider's metadata and generated content.
     */
    public List<Part> processProvider(ContextProvider provider) {
        List<Part> parts = new ArrayList<>();
        try {
            long ts = System.currentTimeMillis();
            List<Part> generated = provider.getParts(chat);
            long executionTime = System.currentTimeMillis() - ts;

            long totalSize = generated.stream().mapToLong(PartUtils::calculateSizeInBytes).sum();

            parts.add(Part.fromText(
                    "Provider: **" + provider.getDisplayName() + "**"
                    + "\nId: **" + provider.getId() + "**:"
                    + "\nDescription: " + provider.getDescription()
                    + "\nClass: " + provider.getClass() + "\n"
                    + "\nTotal Parts: " + generated.size() + " parts"
                    + "\nSize: " + totalSize + " (bytes)"
                    + "\nTook: " + executionTime + " ms."
            ));

            int idx = 0;
            for (Part part : generated) {
                long partSize = PartUtils.calculateSizeInBytes(part);
                parts.add(Part.fromText("Part:" + (idx + 1) + "/" + generated.size() + ", size=" + partSize + " bytes " + " (Context Provider Id: **" + provider.getId() + "**)"));
                parts.add(part);
                idx++;
            }

            //parts.addAll(generated);
        } catch (Exception e) {
            // Log with placeholder for cleaner output
            log.warn("ContextProvider {} threw an exception", provider.getId(), e);
            parts.add(Part.fromText("Error in " + provider.getDisplayName() + ": " + e.getMessage()));
        }
        return parts;
    }
}