package uno.anahata.ai.swing;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import uno.anahata.ai.Chat;
import uno.anahata.ai.tools.FunctionConfirmation;
import uno.anahata.ai.tools.FunctionInfo;
import uno.anahata.ai.internal.GsonUtils;

public class ToolsPanel extends JPanel {
    private final Chat chat;
    private final SwingChatConfig config;
    private final JTable classTable;
    private final DefaultTableModel tableModel;
    private final JPanel detailPanel;
    private final List<FunctionInfo> functionInfos;

    public ToolsPanel(Chat chat, SwingChatConfig config) {
        this.chat = chat;
        this.config = config;
        this.functionInfos = chat.getToolManager().getFunctionInfos();
        setLayout(new BorderLayout());

        // Left side: Table of classes
        String[] columnNames = {"Tool Class", "Size", "Prompt", "Always", "Never"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) {
                    return Long.class; // Size column should be treated as a number for sorting
                }
                return super.getColumnClass(columnIndex);
            }
        };
        classTable = new JTable(tableModel);
        classTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        classTable.getTableHeader().setReorderingAllowed(false);
        classTable.setAutoCreateRowSorter(true);

        // Custom renderer for the size column to display bytes in human-readable format
        classTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                if (value instanceof Long) {
                    value = FileUtils.byteCountToDisplaySize((Long) value);
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

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
        Map<String, List<FunctionInfo>> groupedByClass = functionInfos.stream()
            .collect(Collectors.groupingBy(fi -> fi.method.getDeclaringClass().getSimpleName()));

        // Create summary data for the table
        List<ClassPermissionSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<FunctionInfo>> entry : groupedByClass.entrySet()) {
            String className = entry.getKey();
            int promptCount = 0;
            int alwaysCount = 0;
            int neverCount = 0;
            long totalSize = 0;
            for (FunctionInfo fi : entry.getValue()) {
                totalSize += fi.getSize();
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
            summaries.add(new ClassPermissionSummary(className, totalSize, promptCount, alwaysCount, neverCount));
        }

        // Sort summaries alphabetically by class name for initial display
        summaries.sort(Comparator.comparing(s -> s.className));

        // Populate table model
        tableModel.setRowCount(0);
        for (ClassPermissionSummary summary : summaries) {
            tableModel.addRow(new Object[]{summary.className, summary.totalSize, summary.promptCount, summary.alwaysCount, summary.neverCount});
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

    private JPanel createFunctionControlPanel(FunctionInfo fi) {
        FunctionDeclaration fd = fi.declaration;
        
        String title = String.format("<html><b>%s</b> (%s)</html>", fi.method.getName(), FileUtils.byteCountToDisplaySize(fi.getSize()));
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);

        // Use the full, rich description directly.
        String descriptionText = "<html>" + fd.description().get().replace("\n", "<br>") + "</html>";
        panel.add(new JLabel(descriptionText), gbc);
        gbc.gridy++;

        // --- Toggle Button ---
        JToggleButton detailsButton = new JToggleButton("Show Details");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(detailsButton, gbc);
        gbc.gridy++;

        // --- Collapsible Details Panel ---
        JPanel collapsiblePanel = new JPanel(new BorderLayout());
        collapsiblePanel.setVisible(false);
        
        // Display the full JSON of the FunctionDeclaration using its own toJson() method.
        String rawJson = fd.toJson();
        String prettyJson = GsonUtils.prettyPrint(rawJson);
        String jsonSchema = "<html><pre>" + prettyJson.replace("\n", "<br>").replace(" ", "&nbsp;") + "</pre></html>";
        collapsiblePanel.add(new JLabel(jsonSchema), BorderLayout.CENTER);
        
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
        long totalSize;
        int promptCount;
        int alwaysCount;
        int neverCount;
    }
}