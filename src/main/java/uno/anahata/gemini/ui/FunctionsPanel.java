package uno.anahata.gemini.ui;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import uno.anahata.gemini.Chat;
import uno.anahata.gemini.functions.FunctionConfirmation;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.internal.GsonUtils;

public class FunctionsPanel extends JScrollPane {
    private final Chat chat;
    private final SwingChatConfig config;

    public FunctionsPanel(Chat chat, SwingChatConfig config) {
        this.chat = chat;
        this.config = config;
        refresh();
    }

    public void refresh() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        for (FunctionManager.FunctionInfo fi : chat.getFunctionManager().getFunctionInfos()) {
            mainPanel.add(createFunctionControlPanel(fi));
            mainPanel.add(Box.createVerticalStrut(8));
        }
        
        setViewportView(mainPanel);
        getVerticalScrollBar().setUnitIncrement(16);
    }

    private JPanel createFunctionControlPanel(FunctionManager.FunctionInfo fi) {
        FunctionDeclaration fd = fi.declaration;
        Method method = fi.method;
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("<html><b>" + fd.name().get() + "</b></html>"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);

        // Description (extract original part before signature)
        String fullDescription = fd.description().get();
        String originalDescription = fullDescription;
        int signatureIndex = fullDescription.indexOf("\n\njava method signature:");
        if (signatureIndex != -1) {
            originalDescription = fullDescription.substring(0, signatureIndex);
        }
        String descriptionText = "<html>" + originalDescription.replace("\n", "<br>") + "</html>";
        JLabel description = new JLabel(descriptionText);
        panel.add(description, gbc);
        gbc.gridy++;

        // --- Toggle Button ---
        JToggleButton detailsButton = new JToggleButton("Show Details");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(detailsButton, gbc);
        gbc.gridy++;

        // --- Collapsible Details Panel ---
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setVisible(false); // Collapsed by default
        GridBagConstraints detailsGbc = new GridBagConstraints();
        detailsGbc.gridx = 0;
        detailsGbc.gridy = 0;
        detailsGbc.weightx = 1.0;
        detailsGbc.fill = GridBagConstraints.HORIZONTAL;
        detailsGbc.insets = new Insets(4, 0, 4, 0); // No extra insets needed inside this panel

        // Full Java Method Signature (including throws)
        String methodSignature = "<html><b>" + method.getReturnType().getSimpleName() + "</b> " + method.getName() + "(" +
                                 Stream.of(method.getParameters())
                                       .map(p -> p.getType().getSimpleName() + " " + p.getName())
                                       .collect(Collectors.joining(", ")) +
                                 ")";
        if (method.getExceptionTypes().length > 0) {
            methodSignature += " throws " + Arrays.stream(method.getExceptionTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        }
        methodSignature += "</html>";
        JLabel methodLabel = new JLabel(methodSignature);
        detailsPanel.add(methodLabel, detailsGbc);
        detailsGbc.gridy++;

        // Separator
        detailsPanel.add(new JSeparator(), detailsGbc);
        detailsGbc.gridy++;

        // Full FunctionDeclaration JSON
        String jsonSchema = "<html><pre>" + GsonUtils.prettyPrint(functionDeclarationToMap(fd)).replace("\n", "<br>").replace(" ", "&nbsp;") + "</pre></html>";
        JLabel schemaLabel = new JLabel(jsonSchema);
        detailsPanel.add(schemaLabel, detailsGbc);
        
        // Add details panel to main panel
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(detailsPanel, gbc);
        gbc.gridy++;
        
        // --- Button Group ---
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(createButtonGroup(fd), gbc);
        
        // --- Toggle Logic ---
        detailsButton.addActionListener(e -> {
            boolean isSelected = detailsButton.isSelected();
            detailsPanel.setVisible(isSelected);
            detailsButton.setText(isSelected ? "Hide Details" : "Show Details");
        });
        
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel createButtonGroup(FunctionDeclaration fd) {
        JPanel buttonPanel = new JPanel();
        ButtonGroup group = new ButtonGroup();

        JToggleButton promptButton = new JToggleButton("Prompt");
        JToggleButton alwaysButton = new JToggleButton("Always");
        JToggleButton neverButton = new JToggleButton("Never");

        group.add(promptButton);
        group.add(alwaysButton);
        group.add(neverButton);

        buttonPanel.add(promptButton);
        buttonPanel.add(alwaysButton);
        buttonPanel.add(neverButton);
        
        FunctionCall fc = FunctionCall.builder().name(fd.name().get()).build();

        // Set initial state from config
        FunctionConfirmation currentPref = config.getFunctionConfirmation(fc);
        if (currentPref == null) {
            promptButton.setSelected(true);
        } else {
            switch (currentPref) {
                case ALWAYS:
                    alwaysButton.setSelected(true);
                    break;
                case NEVER:
                    neverButton.setSelected(true);
                    break;
                default:
                    promptButton.setSelected(true);
            }
        }

        // Add listeners to update config
        promptButton.addActionListener(e -> config.clearFunctionConfirmation(fc));
        alwaysButton.addActionListener(e -> config.setFunctionConfirmation(fc, FunctionConfirmation.ALWAYS));
        neverButton.addActionListener(e -> config.setFunctionConfirmation(fc, FunctionConfirmation.NEVER));

        return buttonPanel;
    }
    
    private Map<String, Object> functionDeclarationToMap(FunctionDeclaration fd) {
        Map<String, Object> map = new LinkedHashMap<>();
        fd.description().ifPresent(d -> map.put("description", d));
        fd.name().ifPresent(n -> map.put("name", n));
        fd.parameters().ifPresent(p -> map.put("parameters", schemaToMap(p)));
        fd.response().ifPresent(r -> map.put("response", schemaToMap(r)));
        return map;
    }

    private Map<String, Object> schemaToMap(Schema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        schema.type().ifPresent(t -> map.put("type", t.toString()));
        schema.description().ifPresent(d -> map.put("description", d));
        
        if (schema.properties().isPresent() && !schema.properties().get().isEmpty()) {
            Map<String, Object> props = new LinkedHashMap<>();
            schema.properties().get().forEach((key, value) -> props.put(key, schemaToMap(value)));
            map.put("properties", props);
        }
        
        if (schema.required().isPresent() && !schema.required().get().isEmpty()) {
            map.put("required", schema.required().get());
        }
        
        schema.items().ifPresent(i -> map.put("items", schemaToMap(i)));
        
        if (schema.enum_().isPresent() && !schema.enum_().get().isEmpty()) {
            map.put("enum", schema.enum_().get());
        }
        
        return map;
    }
}