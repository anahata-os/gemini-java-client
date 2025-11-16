package uno.anahata.gemini.ui;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.gemini.Chat;
import uno.anahata.gemini.functions.FunctionConfirmation;
import uno.anahata.gemini.functions.ToolManager;
import uno.anahata.gemini.internal.GsonUtils;

public class FunctionsPanel extends JPanel {
    private final Chat chat;
    private final SwingChatConfig config;
    private final JTable classTable;
    private final DefaultTableModel tableModel;
    private final JPanel detailPanel;
    private final List<ToolManager.FunctionInfo> functionInfos;

    public FunctionsPanel(Chat chat, SwingChatConfig config) {
        this.chat = chat;
        this.config = config;
        this.functionInfos = chat.getFunctionManager().getFunctionInfos();
        setLayout(new BorderLayout());

        // Left side: Table of classes
        String[] columnNames = {"Tool Class", "Prompt", "Always", "Never"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        classTable = new JTable(tableModel);
        classTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        classTable.getTableHeader().setReorderingAllowed(false);
        classTable.setAutoCreateRowSorter(true); // Enable sorting

        // Right side: Details of functions for the selected class
        detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        JScrollPane detailScrollPane = new JScrollPane(detailPanel);
        detailScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(classTable), detailScrollPane);
        splitPane.setDividerLocation(400);
        add(splitPane, BorderLayout.CENTER);

        // Listener to update detail view on table selection
        classTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = classTable.getSelectedRow();
                if (selectedRow != -1) {
                    int modelRow = classTable.convertRowIndexToModel(selectedRow);
                    String className = (String) tableModel.getValueAt(modelRow, 0);
                    updateDetailPanel(className);
                }
            }
        });

        refresh();
    }

    public final void refresh() {
        // Group functions by class name
        Map<String, List<ToolManager.FunctionInfo>> groupedByClass = functionInfos.stream()
            .collect(Collectors.groupingBy(fi -> fi.method.getDeclaringClass().getSimpleName()));

        // Create summary data for the table
        List<ClassPermissionSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<ToolManager.FunctionInfo>> entry : groupedByClass.entrySet()) {
            String className = entry.getKey();
            int promptCount = 0;
            int alwaysCount = 0;
            int neverCount = 0;
            for (ToolManager.FunctionInfo fi : entry.getValue()) {
                FunctionCall fc = FunctionCall.builder().name(fi.declaration.name().get()).build();
                FunctionConfirmation pref = config.getFunctionConfirmation(fc);
                if (pref == null) {
                    promptCount++;
                } else {
                    switch (pref) {
                        case ALWAYS: alwaysCount++; break;
                        case NEVER: neverCount++; break;
                        default: promptCount++; break;
                    }
                }
            }
            summaries.add(new ClassPermissionSummary(className, promptCount, alwaysCount, neverCount));
        }

        // Sort summaries alphabetically by class name for initial display
        summaries.sort(Comparator.comparing(s -> s.className));

        // Populate table model
        tableModel.setRowCount(0);
        for (ClassPermissionSummary summary : summaries) {
            tableModel.addRow(new Object[]{summary.className, summary.promptCount, summary.alwaysCount, summary.neverCount});
        }
    }

    private void updateDetailPanel(String className) {
        detailPanel.removeAll();
        
        functionInfos.stream()
            .filter(fi -> fi.method.getDeclaringClass().getSimpleName().equals(className))
            .sorted(Comparator.comparing(fi -> fi.method.getName()))
            .forEach(fi -> {
                detailPanel.add(createFunctionControlPanel(fi));
                detailPanel.add(Box.createVerticalStrut(8));
            });
        
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private JPanel createFunctionControlPanel(ToolManager.FunctionInfo fi) {
        FunctionDeclaration fd = fi.declaration;
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("<html><b>" + fi.method.getName() + "</b></html>"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);

        // Description (extract original part before signature)
        String fullDescription = fd.description().get();
        String originalDescription = StringUtils.substringBefore(fullDescription, "\n\njava method signature:");
        String descriptionText = "<html>" + originalDescription.replace("\n", "<br>") + "</html>";
        panel.add(new JLabel(descriptionText), gbc);
        gbc.gridy++;

        // --- Toggle Button ---
        JToggleButton detailsButton = new JToggleButton("Show Details");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(detailsButton, gbc);
        gbc.gridy++;

        // --- Collapsible Details Panel ---
        JPanel collapsiblePanel = new JPanel(new GridBagLayout());
        collapsiblePanel.setVisible(false);
        GridBagConstraints detailsGbc = new GridBagConstraints();
        detailsGbc.gridx = 0;
        detailsGbc.gridy = 0;
        detailsGbc.weightx = 1.0;
        detailsGbc.fill = GridBagConstraints.HORIZONTAL;
        detailsGbc.insets = new Insets(4, 0, 4, 0);

        String signature = StringUtils.substringBetween(fullDescription, "java method signature: ", "\ncontext behavior:");
        String behavior = StringUtils.substringAfter(fullDescription, "context behavior: ");

        collapsiblePanel.add(new JLabel("<html><b>Signature:</b> " + signature + "</html>"), detailsGbc);
        detailsGbc.gridy++;
        collapsiblePanel.add(new JLabel("<html><b>Behavior:</b> " + behavior + "</html>"), detailsGbc);
        detailsGbc.gridy++;
        collapsiblePanel.add(new JSeparator(), detailsGbc);
        detailsGbc.gridy++;
        collapsiblePanel.add(new JLabel("<html><b>Full JSON Schema:</b></html>"), detailsGbc);
        detailsGbc.gridy++;
        String jsonSchema = "<html><pre>" + GsonUtils.prettyPrint(fd.toJson()).replace("\n", "<br>").replace(" ", "&nbsp;") + "</pre></html>";
        collapsiblePanel.add(new JLabel(jsonSchema), detailsGbc);
        
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(collapsiblePanel, gbc);
        gbc.gridy++;
        
        // --- Button Group ---
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(createButtonGroup(fd), gbc);
        
        detailsButton.addActionListener(e -> {
            boolean isSelected = detailsButton.isSelected();
            collapsiblePanel.setVisible(isSelected);
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
        FunctionConfirmation currentPref = config.getFunctionConfirmation(fc);
        
        if (currentPref == null) promptButton.setSelected(true);
        else {
            switch (currentPref) {
                case ALWAYS: alwaysButton.setSelected(true); break;
                case NEVER: neverButton.setSelected(true); break;
                default: promptButton.setSelected(true); break;
            }
        }

        promptButton.addActionListener(e -> { config.clearFunctionConfirmation(fc); refresh(); });
        alwaysButton.addActionListener(e -> { config.setFunctionConfirmation(fc, FunctionConfirmation.ALWAYS); refresh(); });
        neverButton.addActionListener(e -> { config.setFunctionConfirmation(fc, FunctionConfirmation.NEVER); refresh(); });

        return buttonPanel;
    }

    @Getter
    @AllArgsConstructor
    private static class ClassPermissionSummary {
        String className;
        int promptCount;
        int alwaysCount;
        int neverCount;
    }
}