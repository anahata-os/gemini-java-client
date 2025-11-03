package uno.anahata.gemini.ui;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.io.File;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.context.stateful.ResourceStatus;
import uno.anahata.gemini.context.stateful.StatefulResource;
import uno.anahata.gemini.functions.ContextBehavior;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.internal.PartUtils;

@Slf4j
public class ContextHeatmapPanel extends JPanel {
    private static final Gson GSON = GsonUtils.getGson();

    private JTable partTable;
    private PartTableModel tableModel;
    private PieChartPanel pieChartPanel;
    private JLabel statusLabel;
    private SwingWorker<List<PartInfo>, Void> worker;
    private FunctionManager functionManager;
    private SwingGeminiConfig.UITheme theme;

    public ContextHeatmapPanel() {
        initComponents();
    }

    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
        this.theme = new SwingGeminiConfig.UITheme(); // Assuming a default theme
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        statusLabel = new JLabel("No context loaded.", SwingConstants.CENTER);

        // Table
        tableModel = new PartTableModel();
        partTable = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                Point p = e.getPoint();
                int viewRow = rowAtPoint(p);
                if (viewRow >= 0) {
                    int modelRow = convertRowIndexToModel(viewRow);
                    return tableModel.getPartInfo(modelRow).getFullContentText();
                }
                return null;
            }
        };
        partTable.setFillsViewportHeight(true);
        partTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        PartTableCellRenderer cellRenderer = new PartTableCellRenderer();
        partTable.setDefaultRenderer(Object.class, cellRenderer);
        partTable.setDefaultRenderer(Number.class, cellRenderer); // Ensure numeric columns are also colored
        
        partTable.setRowSorter(new TableRowSorter<>(tableModel));
        
        setupTableColumnWidths();

        JScrollPane tableScrollPane = new JScrollPane(partTable);

        // Pie Chart
        pieChartPanel = new PieChartPanel();

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScrollPane, pieChartPanel);
        splitPane.setDividerLocation(0.7);
        splitPane.setResizeWeight(0.7);

        add(splitPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.NORTH);
    }
    
    private void setupTableColumnWidths() {
        TableColumnModel columnModel = partTable.getColumnModel();
        // Msg
        TableColumn msgCol = columnModel.getColumn(0);
        msgCol.setMinWidth(40);
        msgCol.setMaxWidth(60);
        msgCol.setPreferredWidth(40);
        // Part
        TableColumn partCol = columnModel.getColumn(1);
        partCol.setMinWidth(40);
        partCol.setMaxWidth(60);
        partCol.setPreferredWidth(40);
        // Role
        TableColumn roleCol = columnModel.getColumn(2);
        roleCol.setMinWidth(50);
        roleCol.setMaxWidth(80);
        roleCol.setPreferredWidth(60);
        // Type
        TableColumn typeCol = columnModel.getColumn(3);
        typeCol.setMinWidth(80);
        typeCol.setMaxWidth(150);
        typeCol.setPreferredWidth(120);
        // Size
        TableColumn sizeCol = columnModel.getColumn(4);
        sizeCol.setMinWidth(80);
        sizeCol.setMaxWidth(120);
        sizeCol.setPreferredWidth(100);
        // Function
        TableColumn funcCol = columnModel.getColumn(5);
        funcCol.setPreferredWidth(150);
        // Resource ID
        TableColumn resourceCol = columnModel.getColumn(6);
        resourceCol.setPreferredWidth(150); // Same as Function
        // Status
        TableColumn statusCol = columnModel.getColumn(7);
        statusCol.setPreferredWidth(60); // Smaller
        // Summary
        TableColumn summaryCol = columnModel.getColumn(8);
        summaryCol.setPreferredWidth(470); // Give it all the extra space
    }

    public void updateContext(List<ChatMessage> context) {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }

        statusLabel.setText("Analyzing context...");
        tableModel.clear();
        pieChartPanel.setData(Collections.emptyList());

        worker = new SwingWorker<List<PartInfo>, Void>() {
            @Override
            protected List<PartInfo> doInBackground() throws Exception {
                if (context == null || context.isEmpty()) {
                    return Collections.emptyList();
                }
                return buildPartInfoList(context);
            }

            @Override
            protected void done() {
                try {
                    List<PartInfo> partInfos = get();
                    tableModel.setPartInfos(partInfos);
                    pieChartPanel.setData(partInfos);
                    statusLabel.setText("Context analysis complete. " + partInfos.size() + " parts found.");
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Failed to update context view", e);
                    statusLabel.setText("Error analyzing context: " + e.getMessage());
                } catch (java.util.concurrent.CancellationException e) {
                    statusLabel.setText("Context analysis cancelled.");
                }
            }
        };
        worker.execute();
    }

    private List<PartInfo> buildPartInfoList(List<ChatMessage> context) {
        List<PartInfo> infos = new ArrayList<>();
        
        Map<String, ResourceStatus> statusMap = Collections.emptyMap();
        if (functionManager != null && functionManager.getChat().getContextManager() != null) {
            statusMap = functionManager.getChat().getContextManager().getResourceTracker().getStatefulResourcesOverview()
                .stream()
                .collect(Collectors.toMap(srs -> srs.resource.getResourceId(), srs -> srs.status));
        }

        for (int i = 0; i < context.size(); i++) {
            ChatMessage msg = context.get(i);
            if (msg.getContent() == null || !msg.getContent().parts().isPresent()) {
                continue;
            }
            List<Part> parts = msg.getContent().parts().get();
            for (int j = 0; j < parts.size(); j++) {
                infos.add(new PartInfo(i, j, msg, parts.get(j), functionManager, theme, statusMap));
            }
        }
        return infos;
    }

    // --- Inner Classes ---
    @Getter
    public static class PartInfo {
        private final int messageIndex;
        private final int partIndex;
        private final String messageUuid;
        private final String role;
        private final String partType;
        private final long sizeInBytes;
        private final String functionName;
        private final String resourceId;
        private final ResourceStatus resourceStatus;
        private final String contentSummary;
        private final String fullContentText;
        private final boolean isError;
        private final Color roleColor;

        PartInfo(int msgIdx, int partIdx, ChatMessage msg, Part part, FunctionManager fm, SwingGeminiConfig.UITheme theme, Map<String, ResourceStatus> statusMap) {
            this.messageIndex = msgIdx;
            this.partIndex = partIdx;
            this.messageUuid = msg.getId();
            this.role = msg.getContent().role().orElse("unknown");
            this.sizeInBytes = PartUtils.calculateSizeInBytes(part);

            switch (this.role) {
                case "user": this.roleColor = theme.getUserHeaderBg(); break;
                case "model": this.roleColor = theme.getModelHeaderBg(); break;
                case "tool": this.roleColor = theme.getToolHeaderBg(); break;
                default: this.roleColor = theme.getDefaultHeaderBg(); break;
            }

            String tempFuncName = "";
            String tempResourceId = "";
            ResourceStatus tempResourceStatus = null;
            boolean tempIsError = false;

            if (part.text().isPresent()) {
                this.partType = "Text";
                this.fullContentText = part.text().get();
            } else if (part.functionCall().isPresent()) {
                this.partType = "FunctionCall";
                tempFuncName = part.functionCall().get().name().orElse("");
                this.fullContentText = "Call: " + tempFuncName + "\nArgs: " + part.functionCall().get().args();
            } else if (part.functionResponse().isPresent()) {
                this.partType = "FunctionResponse";
                FunctionResponse fr = part.functionResponse().get();
                tempFuncName = fr.name().orElse("");
                Map<String, Object> respMap = (Map<String, Object>) fr.response().get();
                this.fullContentText = "Response: " + tempFuncName + "\nContent: " + respMap;

                if (respMap.containsKey("error") || respMap.containsKey("exception")) {
                    tempIsError = true;
                }

                if (fm != null && fm.getContextBehavior(tempFuncName) == ContextBehavior.STATEFUL_REPLACE) {
                    Method toolMethod = fm.getToolMethod(tempFuncName);
                    if (toolMethod != null && StatefulResource.class.isAssignableFrom(toolMethod.getReturnType())) {
                        try {
                            JsonElement jsonTree = GSON.toJsonTree(fr.response().get());
                            StatefulResource sr = (StatefulResource) GSON.fromJson(jsonTree, toolMethod.getReturnType());
                            tempResourceId = sr.getResourceId();
                            tempResourceStatus = statusMap.get(tempResourceId);
                        } catch (Exception e) {
                            log.warn("Error deserializing stateful resource for function {}", tempFuncName, e);
                        }
                    }
                }
            } else if (part.inlineData().isPresent()) {
                this.partType = "Blob";
                this.fullContentText = "MIME Type: " + part.inlineData().get().mimeType().orElse("") + ", Size: " + sizeInBytes + " bytes";
            } else {
                this.partType = "Unknown";
                this.fullContentText = part.toString();
            }

            this.functionName = tempFuncName;
            this.resourceId = tempResourceId;
            this.resourceStatus = tempResourceStatus;
            this.isError = tempIsError;
            this.contentSummary = StringUtils.abbreviate(fullContentText.replace('\n', ' '), 100);
        }
    }

    private static class PartTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Msg", "Part", "Role", "Type", "Size (Bytes)", "Function", "Resource ID", "Status", "Summary"};
        private List<PartInfo> partInfos = new ArrayList<>();

        public void setPartInfos(List<PartInfo> partInfos) {
            this.partInfos = new ArrayList<>(partInfos);
            fireTableDataChanged();
        }

        public void clear() {
            this.partInfos.clear();
            fireTableDataChanged();
        }

        public PartInfo getPartInfo(int rowIndex) {
            return partInfos.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return partInfos.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 4) return Long.class;
            if (columnIndex == 0 || columnIndex == 1) return Integer.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PartInfo info = partInfos.get(rowIndex);
            switch (columnIndex) {
                case 0: return info.getMessageIndex();
                case 1: return info.getPartIndex();
                case 2: return info.getRole();
                case 3: return info.getPartType();
                case 4: return info.getSizeInBytes();
                case 5: return info.getFunctionName();
                case 6: return info.getResourceId();
                case 7: return info.getResourceStatus() != null ? info.getResourceStatus().name() : "";
                case 8: return info.getContentSummary();
                default: return null;
            }
        }
    }

    private class PartTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value instanceof Number) {
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                setHorizontalAlignment(SwingConstants.LEFT);
            }
            
            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                PartInfo info = tableModel.getPartInfo(modelRow);
                if (info.isError()) {
                    c.setBackground(theme.getFunctionErrorBg());
                    c.setForeground(theme.getFunctionErrorFg());
                } else {
                    c.setBackground(info.getRoleColor());
                    c.setForeground(theme.getFontColor());
                }
            }
            return c;
        }
    }

    private static class PieChartPanel extends JPanel {
        private List<PartInfo> data;
        private List<Slice> slices;
        private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#.0%");

        public PieChartPanel() {
            this.data = Collections.emptyList();
            this.slices = Collections.emptyList();
            setToolTipText("");
        }

        public void setData(List<PartInfo> data) {
            this.data = data;
            processData();
            repaint();
        }

        private void processData() {
            if (data == null || data.isEmpty()) {
                slices = Collections.emptyList();
                return;
            }
            long totalSize = data.stream().mapToLong(PartInfo::getSizeInBytes).sum();
            if (totalSize == 0) {
                slices = Collections.emptyList();
                return;
            }
            List<Slice> newSlices = new ArrayList<>();
            double currentAngle = 0;
            for (PartInfo info : data) {
                double angle = (double) info.getSizeInBytes() / totalSize * 360.0;
                newSlices.add(new Slice(currentAngle, angle, info.getRoleColor(), info, (double) info.getSizeInBytes() / totalSize));
                currentAngle += angle;
            }
            this.slices = newSlices;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (slices.isEmpty()) {
                g.drawString("No data to display.", 10, 20);
                return;
            }
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = Math.min(getWidth(), getHeight()) - 40;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;

            for (Slice slice : slices) {
                g2d.setColor(slice.color);
                g2d.fill(new Arc2D.Double(x, y, diameter, diameter, slice.startAngle, slice.angle, Arc2D.PIE));
            }
            
            // Draw labels on top
            for (Slice slice : slices) {
                if (slice.percentage > 0.02 && StringUtils.isNotBlank(slice.info.getResourceId())) {
                    drawLabel(g2d, slice, x, y, diameter);
                }
            }
        }
        
        private void drawLabel(Graphics2D g2d, Slice slice, int x, int y, int diameter) {
            FontMetrics fm = g2d.getFontMetrics();
            double midAngle = Math.toRadians(slice.startAngle + slice.angle / 2);
            int labelX = (int) (x + diameter / 2 + Math.cos(midAngle) * diameter / 3);
            int labelY = (int) (y + diameter / 2 + Math.sin(midAngle) * diameter / 3);

            String filename = new File(slice.info.getResourceId()).getName();
            String label = String.format("%s (%s)", filename, PERCENT_FORMAT.format(slice.percentage));
            
            int textWidth = fm.stringWidth(label);
            labelX -= textWidth / 2;

            // Choose text color for contrast
            double luminance = (0.299 * slice.color.getRed() + 0.587 * slice.color.getGreen() + 0.114 * slice.color.getBlue()) / 255;
            g2d.setColor(luminance > 0.5 ? Color.BLACK : Color.WHITE);
            g2d.drawString(label, labelX, labelY);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            if (slices.isEmpty()) return null;
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int dx = e.getX() - centerX;
            int dy = e.getY() - centerY;
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 360;

            for (Slice slice : slices) {
                double endAngle = slice.startAngle + slice.angle;
                if (angle >= slice.startAngle && angle < endAngle) {
                    PartInfo info = slice.info;
                    return String.format("<html><b>%s</b> (Msg %d, Part %d)<br>Size: %d bytes (%.2f%%)<br>Type: %s<br>Summary: %s</html>",
                        info.getRole(), info.getMessageIndex(), info.getPartIndex(), info.getSizeInBytes(), slice.percentage * 100, info.getPartType(), info.getContentSummary());
                }
            }
            return null;
        }

        private static class Slice {
            final double startAngle, angle, percentage;
            final Color color;
            final PartInfo info;

            Slice(double startAngle, double angle, Color color, PartInfo info, double percentage) {
                this.startAngle = startAngle;
                this.angle = angle;
                this.color = color;
                this.info = info;
                this.percentage = percentage;
            }
        }
    }
}
