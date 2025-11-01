package uno.anahata.gemini.context.pruning;

import uno.anahata.gemini.ChatMessage;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.functions.ContextBehavior;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.internal.ContentUtils;

@Slf4j
public class ContextPruner {

    private final ContextManager contextManager;

    public ContextPruner(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void pruneMessages(List<String> uids, String reason) {
        List<ChatMessage> context = contextManager.getContext();
        boolean removed = context.removeIf(message -> uids.contains(message.getId()));
        if (removed) {
            log.info("Pruned {} message(s). Reason: {}", uids.size(), reason);
            contextManager.setContext(context); // Notify change
        }
    }

    public void pruneParts(String messageUID, List<Number> partIndices, String reason) {
        List<ChatMessage> context = contextManager.getContext();
        Optional<ChatMessage> targetMessageOpt = context.stream()
                .filter(m -> m.getId().equals(messageUID))
                .findFirst();

        if (targetMessageOpt.isEmpty()) {
            log.warn("Could not find message with ID {} to prune parts.", messageUID);
            return;
        }

        ChatMessage targetMessage = targetMessageOpt.get();
        Content originalContent = targetMessage.getContent();
        if (originalContent == null || !originalContent.parts().isPresent()) {
            log.warn("Message {} has no parts to prune.", messageUID);
            return;
        }

        List<Part> originalParts = originalContent.parts().get();
        Set<Part> partsToPrune = new HashSet<>();
        Set<Long> indicesToPrune = partIndices.stream()
                .map(Number::longValue)
                .collect(Collectors.toSet());

        for (int i = 0; i < originalParts.size(); i++) {
            if (indicesToPrune.contains((long) i)) {
                partsToPrune.add(originalParts.get(i));
            }
        }

        if (partsToPrune.isEmpty()) {
            log.warn("None of the specified part indices {} were valid for message {}", partIndices, messageUID);
            return;
        }

        // --- Dependency Resolution ---
        Map<Part, Part> callToResponse = new HashMap<>();
        Map<Part, Part> responseToCall = new HashMap<>();
        for (ChatMessage msg : context) {
            if (msg.getPartLinks() != null) {
                for (Map.Entry<Part, Part> entry : msg.getPartLinks().entrySet()) {
                    responseToCall.put(entry.getKey(), entry.getValue());
                    callToResponse.put(entry.getValue(), entry.getKey());
                }
            }
        }

        Set<Part> fullPruneSet = new HashSet<>(partsToPrune);
        List<Part> partsToCheck = new ArrayList<>(partsToPrune);

        while (!partsToCheck.isEmpty()) {
            Part part = partsToCheck.remove(0);

            if (part.functionCall().isPresent()) {
                Part response = callToResponse.get(part);
                if (response != null && fullPruneSet.add(response)) {
                    partsToCheck.add(response);
                }
            }

            if (part.functionResponse().isPresent()) {
                Part call = responseToCall.get(part);
                if (call != null && fullPruneSet.add(call)) {
                    partsToCheck.add(call);
                }
            }
        }

        log.info("Pruning {} part(s) including dependencies. Reason: {}", fullPruneSet.size(), reason);
        contextManager.prunePartsByReference(new ArrayList<>(fullPruneSet), reason);
    }

    public void prunePartsByReference(List<Part> partsToPrune, String reason) {
        if (partsToPrune == null || partsToPrune.isEmpty()) {
            return;
        }

        Set<Part> partsToPruneSet = new HashSet<>(partsToPrune);
        boolean contextWasModified = false;
        List<ChatMessage> context = contextManager.getContext();

        ListIterator<ChatMessage> iterator = context.listIterator();
        while (iterator.hasNext()) {
            ChatMessage currentMessage = iterator.next();
            Content originalContent = currentMessage.getContent();
            if (originalContent == null || !originalContent.parts().isPresent()) {
                continue;
            }

            List<Part> originalParts = originalContent.parts().get();
            if (Collections.disjoint(originalParts, partsToPruneSet)) {
                continue;
            }

            List<Part> partsToKeep = originalParts.stream()
                    .filter(p -> !partsToPruneSet.contains(p))
                    .collect(Collectors.toList());

            contextWasModified = true;
            if (partsToKeep.isEmpty()) {
                iterator.remove();
                log.info("Message {} became empty and was removed after pruning parts.", currentMessage.getId());
            } else {
                Content newContent = ContentUtils.cloneAndRemoveParts(originalContent, partsToPrune);
                ChatMessage replacement = currentMessage.toBuilder()
                        .content(newContent)
                        .build();
                iterator.set(replacement);
            }
        }

        if (contextWasModified) {
            log.info("Pruned {} part(s) by reference. Reason: {}", partsToPrune.size(), reason);
            contextManager.setContext(context); // Notify change
        }
    }

    public void pruneOldEphemeralResults(FunctionManager functionManager) {
        if (functionManager == null) {
            return;
        }
        List<ChatMessage> context = contextManager.getContext();
        final int turnsToKeep = 2;
        List<Integer> userMessageIndices = new ArrayList<>();
        for (int i = context.size() - 1; i >= 0; i--) {
            if (isUserMessage(context.get(i))) {
                userMessageIndices.add(i);
            }
        }

        if (userMessageIndices.size() <= turnsToKeep) {
            return;
        }

        int pruneCutoffIndex = userMessageIndices.get(turnsToKeep);
        Set<Part> partsToPrune = new HashSet<>();

        Map<Part, Part> callToResponseLink = new HashMap<>();
        for (ChatMessage msg : context) {
            if (msg.getPartLinks() != null) {
                for (Map.Entry<Part, Part> entry : msg.getPartLinks().entrySet()) {
                    callToResponseLink.put(entry.getValue(), entry.getKey());
                }
            }
        }

        for (int i = 0; i < pruneCutoffIndex; i++) {
            ChatMessage message = context.get(i);
            if (message.getContent() == null || !message.getContent().parts().isPresent()) {
                continue;
            }

            for (Part part : message.getContent().parts().get()) {
                if (isPartEphemeral(part, functionManager)) {
                    partsToPrune.add(part);
                    if (part.functionCall().isPresent()) {
                        Part responsePart = callToResponseLink.get(part);
                        if (responsePart != null) {
                            partsToPrune.add(responsePart);
                        }
                    } else if (part.functionResponse().isPresent()) {
                        Part callPart = message.getFunctionCallForResponse(part);
                        if (callPart != null) {
                            partsToPrune.add(callPart);
                        }
                    }
                }
            }
        }

        if (!partsToPrune.isEmpty()) {
            log.info("Two-Turn Rule: Pruning {} old ephemeral parts.", partsToPrune.size());
            prunePartsByReference(new ArrayList<>(partsToPrune), "Automatic pruning of old ephemeral tool calls and responses.");
        }
    }

    private boolean isPartEphemeral(Part part, FunctionManager functionManager) {
        String toolName = "";
        if (part.functionCall().isPresent()) {
            toolName = part.functionCall().get().name().orElse("");
        } else if (part.functionResponse().isPresent()) {
            toolName = part.functionResponse().get().name().orElse("");
        }
        return !toolName.isEmpty() && functionManager.getContextBehavior(toolName) == ContextBehavior.EPHEMERAL;
    }

    private boolean isUserMessage(ChatMessage message) {
        return message.getContent() != null && "user".equals(message.getContent().role().orElse(null));
    }
}