package uno.anahata.ai.swing;

import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.lang.reflect.Method;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.stateful.ResourceStatus;
import uno.anahata.ai.context.stateful.StatefulResource;
import uno.anahata.ai.tools.ContextBehavior;
import uno.anahata.ai.tools.ToolManager;
import uno.anahata.ai.internal.GsonUtils;
import uno.anahata.ai.internal.PartUtils;

@Slf4j
public class ContextHeatmapPanel extends JPanel {
    private static final Gson GSON = GsonUtils.getGson();

    private JTable partTable;
    private PartTableModel tableModel;
    private PieChartPanel pieChartPanel;
    private JLabel statusLabel;
    private SwingWorker<List<PartInfo>, Void> worker;
    private ToolManager functionManager;
    private SwingChatConfig.UITheme theme;

    private ScrollableTooltipPopup tooltipPopup;

    public ContextHeatmapPanel() {
        initComponents();
    }

    public void setFunctionManager(ToolManager functionManager) {
        this.functionManager = functionManager;
        this.theme = new SwingChatConfig.UITheme();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        statusLabel = new JLabel("No context loaded.", SwingConstants.CENTER);

        tableModel = new PartTableModel();
        partTable = new JTable(tableModel);
        partTable.setFillsViewportHeight(true);
        partTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        PartTableCellRenderer cellRenderer = new PartTableCellRenderer();
        partTable.setDefaultRenderer(Object.class, cellRenderer);
        partTable.setDefaultRenderer(Number.class, cellRenderer);

        partTable.setRowSorter(new TableRowSorter<>(tableModel));
        setupTableColumnWidths();
        JScrollPane tableScrollPane = new JScrollPane(partTable);

        setupInteractivePopup();

        pieChartPanel = new PieChartPanel(partTable, tableModel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScrollPane, pieChartPanel);
        splitPane.setDividerLocation(0.7);
        splitPane.setResizeWeight(0.7);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton pruneButton = new JButton("Prune Selected");
        pruneButton.addActionListener(e -> onPruneSelected());
        bottomPanel.add(pruneButton);

        add(splitPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupInteractivePopup() {
        tooltipPopup = new ScrollableTooltipPopup();
        tooltipPopup.attach(partTable);

        partTable.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = partTable.rowAtPoint(e.getPoint());
                if (row != -1) {
                    int modelRow = partTable.convertRowIndexToModel(row);
                    String content = tableModel.getPartInfo(modelRow).getFullContentText();
                    tooltipPopup.setContent(content);
                } else {
                    tooltipPopup.setContent(null);
                    tooltipPopup.hide();
                }
            }
        });
        
        partTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                // The ScrollableTooltipPopup handles the hide timer logic
            }
        });
    }

    private void onPruneSelected() {
        int[] selectedRows = partTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "No parts selected to prune.", "Prune Parts", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
            this,
            "Prune " + selectedRows.length + " selected parts (and their dependencies) from the context?",
            "Confirm Prune",
            JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            List<Part> partsToPrune = new ArrayList<>();
            for (int viewRow : selectedRows) {
                int modelRow = partTable.convertRowIndexToModel(viewRow);
                partsToPrune.add(tableModel.getPartInfo(modelRow).getPart());
            }

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    functionManager.getChat().getContextManager().prunePartsByReference(
                            partsToPrune,
                            "Pruned by user from Context Heatmap"
                        );
                                        return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (Exception ex) {
                        log.error("Error pruning parts", ex);
                        JOptionPane.showMessageDialog(ContextHeatmapPanel.this,
                            "Error during pruning: " + ex.getMessage(),
                            "Prune Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    private void setupTableColumnWidths() {
        TableColumnModel columnModel = partTable.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(40);
        columnModel.getColumn(1).setPreferredWidth(40);
        columnModel.getColumn(2).setPreferredWidth(60);
        columnModel.getColumn(3).setPreferredWidth(120);
        columnModel.getColumn(4).setPreferredWidth(100);
        columnModel.getColumn(5).setPreferredWidth(150);
        columnModel.getColumn(6).setPreferredWidth(150);
        columnModel.getColumn(7).setPreferredWidth(80);
        columnModel.getColumn(8).setPreferredWidth(450);
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
                return (context == null || context.isEmpty()) ? Collections.emptyList() : buildPartInfoList(context);
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
        Map<String, ResourceStatus> statusMap = functionManager != null && functionManager.getChat().getContextManager() != null
            ? functionManager.getChat().getContextManager().getResourceTracker().getStatefulResourcesOverview()
                .stream()
                .collect(Collectors.toMap(srs -> srs.resource.getResourceId(), srs -> srs.status))
            : Collections.emptyMap();

        List<PartInfo> infos = new ArrayList<>();
        for (int i = 0; i < context.size(); i++) {
            ChatMessage msg = context.get(i);
            if (msg.getContent() != null && msg.getContent().parts().isPresent()) {
                List<Part> parts = msg.getContent().parts().get();
                for (int j = 0; j < parts.size(); j++) {
                    infos.add(new PartInfo(i, j, msg, parts.get(j), functionManager, theme, statusMap));
                }
            }
        }
        return infos;
    }

    @Getter
    public static class PartInfo {
        private final int messageIndex;
        private final int partIndex;
        private final long messageSeqId;
        private final String role;
        private final String partType;
        private final long sizeInBytes;
        private final String functionName;
        private final String resourceId;
        private final String resourceIdFilename;
        private final ResourceStatus resourceStatus;
        private final String contentSummary;
        private final String fullContentText;
        private final boolean isError;
        private final Color roleColor;
        private final Part part;

        PartInfo(int msgIdx, int partIdx, ChatMessage msg, Part part, ToolManager fm, SwingChatConfig.UITheme theme, Map<String, ResourceStatus> statusMap) {
            this.messageIndex = msgIdx;
            this.partIndex = partIdx;
            this.messageSeqId = msg.getSequentialId();
            this.role = msg.getContent().role().orElse("unknown");
            this.sizeInBytes = PartUtils.calculateSizeInBytes(part);
            this.roleColor = getRoleColor(theme, this.role);
            this.part = part;

            String tempPartType = "Unknown";
            String tempFullContent = part.toString();
            String tempFuncName = "";
            String tempResourceId = "";
            ResourceStatus tempResourceStatus = null;
            boolean tempIsError = false;

            if (part.text().isPresent()) {
                tempPartType = "Text";
                tempFullContent = part.text().get();
            } else if (part.functionCall().isPresent()) {
                tempPartType = "FunctionCall";
                tempFuncName = part.functionCall().get().name().orElse("");
                tempFullContent = "Call: " + tempFuncName + "\nArgs: " + part.functionCall().get().args();
            } else if (part.functionResponse().isPresent()) {
                FunctionResponse fr = part.functionResponse().get();
                tempPartType = "FunctionResponse";
                tempFuncName = fr.name().orElse("");
                Map<String, Object> respMap = (Map<String, Object>) fr.response().get();
                tempFullContent = "Response: " + tempFuncName + "\nContent: " + respMap;
                tempIsError = respMap.containsKey("error") || respMap.containsKey("exception");

                if (fm != null && fm.getContextBehavior(tempFuncName) == ContextBehavior.STATEFUL_REPLACE) {
                    Method toolMethod = fm.getToolMethod(tempFuncName);
                    if (toolMethod != null && StatefulResource.class.isAssignableFrom(toolMethod.getReturnType())) {
                        try {
                            JsonElement jsonTree = GSON.toJsonTree(fr.response().get());
                            Class<? extends StatefulResource> statefulClass = toolMethod.getReturnType().asSubclass(StatefulResource.class);
                            StatefulResource sr = GSON.fromJson(jsonTree, statefulClass);
                            tempResourceId = sr.getResourceId();
                            tempResourceStatus = statusMap.get(tempResourceId);
                        } catch (Exception e) {
                            log.warn("Error deserializing stateful resource for function {}", tempFuncName, e);
                        }
                    }
                }
            } else if (part.inlineData().isPresent()) {
                tempPartType = "Blob";
                tempFullContent = "MIME Type: " + part.inlineData().get().mimeType().orElse("") + ", Size: " + sizeInBytes + " bytes";
            }

            this.partType = tempPartType;
            this.fullContentText = tempFullContent;
            this.functionName = tempFuncName;
            this.resourceId = tempResourceId;
            this.resourceStatus = tempResourceStatus;
            this.isError = tempIsError;
            this.contentSummary = StringUtils.abbreviate(fullContentText.replace('\n', ' '), 100);
            this.resourceIdFilename = extractFilename(tempResourceId);
        }

        private String extractFilename(String path) {
            if (StringUtils.isBlank(path)) return "";
            try {
                return Paths.get(path).getFileName().toString();
            } catch (InvalidPathException e) {
                return path;
            }
        }

        private Color getRoleColor(SwingChatConfig.UITheme theme, String role) {
            switch (role) {
                case "user": return theme.getUserHeaderBg();
                case "model": return theme.getModelHeaderBg();
                case "tool": return theme.getToolHeaderBg();
                default: return theme.getDefaultHeaderBg();
            }
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

        @Override public int getRowCount() { return partInfos.size(); }
        @Override public int getColumnCount() { return columnNames.length; }
        @Override public String getColumnName(int column) { return columnNames[column]; }
        @Override public Class<?> getColumnClass(int c) {
            return (c == 4) ? Long.class : (c == 0 || c == 1) ? Integer.class : String.class;
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
                case 6: return info.getResourceIdFilename();
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
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);

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
        private List<Slice> slices = Collections.emptyList();
        private final JTable table;
        private final PartTableModel tableModel;
        private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#.0%");
        private static final Map<String, Color> PART_TYPE_COLORS = new HashMap<>();

        static {
            PART_TYPE_COLORS.put("Text", new Color(0, 120, 215));
            PART_TYPE_COLORS.put("FunctionCall", new Color(216, 59, 1));
            PART_TYPE_COLORS.put("FunctionResponse", new Color(0, 153, 188));
            PART_TYPE_COLORS.put("Blob", new Color(104, 33, 122));
            PART_TYPE_COLORS.put("Unknown", Color.GRAY);
        }

        public PieChartPanel(JTable table, PartTableModel tableModel) {
            this.table = table;
            this.tableModel = tableModel;
            setToolTipText("");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handleMouseClick(e.getPoint());
                }
            });
        }

        private void handleMouseClick(Point p) {
            if (slices.isEmpty()) return;

            Slice clickedSlice = findSliceForPoint(p);
            if (clickedSlice != null) {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (tableModel.getPartInfo(i) == clickedSlice.info) {
                        int viewRow = table.convertRowIndexToView(i);
                        if (viewRow != -1) {
                            table.setRowSelectionInterval(viewRow, viewRow);
                            table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                            table.requestFocusInWindow();
                        }
                        break;
                    }
                }
            }
        }

        private Slice findSliceForPoint(Point p) {
            int diameter = Math.min(getWidth(), getHeight()) - 100;
            int pieX = (getWidth() - diameter) / 2;
            int pieY = (getHeight() - diameter) / 2;
            double explodeOffset = diameter * 0.03;

            for (Slice slice : slices) {
                double midAngle = slice.startAngle + slice.angle / 2.0;
                double midAngleRad = Math.toRadians(midAngle);
                double offsetX = Math.cos(midAngleRad) * explodeOffset;
                double offsetY = Math.sin(midAngleRad) * explodeOffset;
                int arcY = pieY - (int)offsetY;

                Arc2D arc = new Arc2D.Double(pieX + offsetX, arcY, diameter, diameter, slice.startAngle, slice.angle, Arc2D.PIE);
                if (arc.contains(p)) {
                    return slice;
                }
            }
            return null;
        }

        public void setData(List<PartInfo> data) {
            processData(data);
            repaint();
        }

        private void processData(List<PartInfo> data) {
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
                Color color = PART_TYPE_COLORS.getOrDefault(info.getPartType(), Color.DARK_GRAY);
                newSlices.add(new Slice(currentAngle, angle, color, info, (double) info.getSizeInBytes() / totalSize));
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

            int diameter = Math.min(getWidth(), getHeight()) - 100;
            int pieX = (getWidth() - diameter) / 2;
            int pieY = (getHeight() - diameter) / 2;
            double explodeOffset = diameter * 0.03;

            FontMetrics fm = g2d.getFontMetrics();
            List<LabelInfo> labels = new ArrayList<>();

            // First, draw the pie slices and calculate label positions
            for (Slice slice : slices) {
                double midAngle = slice.startAngle + slice.angle / 2.0;
                double midAngleRad = Math.toRadians(midAngle); // Correct angle in radians
                
                double offsetX = Math.cos(midAngleRad) * explodeOffset;
                double offsetY = Math.sin(midAngleRad) * explodeOffset;

                int arcX = pieX + (int)offsetX;
                int arcY = pieY - (int)offsetY; // Invert Y for Swing coordinates

                Arc2D arc = new Arc2D.Double(arcX, arcY, diameter, diameter, slice.startAngle, slice.angle, Arc2D.PIE);
                g2d.setColor(slice.color);
                g2d.fill(arc);
                g2d.setColor(Color.WHITE);
                g2d.draw(arc);

                if (slice.percentage > 0.01) {
                    double sliceCenterX = arcX + diameter / 2.0;
                    double sliceCenterY = arcY + diameter / 2.0;
                    labels.add(new LabelInfo(slice, midAngleRad, diameter, sliceCenterX, sliceCenterY));
                }
            }

            // Now, draw the labels with collision avoidance
            drawLabels(g2d, labels, fm);
        }

        private void drawLabels(Graphics2D g2d, List<LabelInfo> labels, FontMetrics fm) {
            // Separate labels for left and right sides of the pie
            List<LabelInfo> rightLabels = labels.stream()
                .filter(l -> Math.cos(l.midAngleRad) >= 0)
                .sorted(Comparator.comparingDouble(l -> l.leaderLineEnd.getY()))
                .collect(Collectors.toList());

            List<LabelInfo> leftLabels = labels.stream()
                .filter(l -> Math.cos(l.midAngleRad) < 0)
                .sorted(Comparator.comparingDouble(l -> l.leaderLineEnd.getY()))
                .collect(Collectors.toList());

            // Adjust positions to avoid overlap
            adjustLabelPositions(rightLabels, fm);
            adjustLabelPositions(leftLabels, fm);

            // Draw all labels and lines
            g2d.setColor(Color.BLACK);
            for (LabelInfo label : labels) {
                g2d.drawLine((int) label.edgePoint.getX(), (int) label.edgePoint.getY(), (int) label.leaderLineEnd.getX(), (int) label.leaderLineEnd.getY());
                g2d.drawLine((int) label.leaderLineEnd.getX(), (int) label.leaderLineEnd.getY(), (int) label.horizontalLineEnd.getX(), (int) label.horizontalLineEnd.getY());
                g2d.drawString(label.text, (int) label.textStart.getX(), (int) label.textStart.getY());
            }
        }

        private void adjustLabelPositions(List<LabelInfo> labels, FontMetrics fm) {
            int labelHeight = fm.getHeight();
            for (int i = 0; i < labels.size() - 1; i++) {
                LabelInfo l1 = labels.get(i);
                LabelInfo l2 = labels.get(i + 1);
                double overlap = (l1.leaderLineEnd.getY() + labelHeight) - l2.leaderLineEnd.getY();
                if (overlap > 0) {
                    // Shift l2 and all subsequent labels down
                    for (int j = i + 1; j < labels.size(); j++) {
                        labels.get(j).adjustY(overlap);
                    }
                }
            }
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            Slice slice = findSliceForPoint(e.getPoint());
            if (slice != null) {
                PartInfo info = slice.info;
                return String.format("<html><b>%s</b> (Msg %d, Part %d)<br>Size: %d bytes (%.2f%%)<br>Type: %s<br>Summary: %s</html>",
                    info.getRole(), info.getMessageIndex(), info.getPartIndex(), info.getSizeInBytes(), slice.percentage * 100, info.getPartType(), info.getContentSummary());
            }
            return null;
        }

        private static class Slice {
            final double startAngle, angle, percentage;
            final Color color;
            final PartInfo info;
            Slice(double sa, double a, Color c, PartInfo i, double p) {
                startAngle = sa; angle = a; color = c; info = i; percentage = p;
            }
        }

        private static class LabelInfo {
            final String text;
            final double midAngleRad;
            Point2D edgePoint;
            Point2D leaderLineEnd;
            Point2D horizontalLineEnd;
            Point2D textStart;

            LabelInfo(Slice slice, double midAngleRad, int diameter, double sliceCenterX, double sliceCenterY) {
                this.midAngleRad = midAngleRad;
                this.text = String.format("%s (%s)",
                    slice.info.getResourceIdFilename().isEmpty() ? slice.info.getPartType() : slice.info.getResourceIdFilename(),
                    PERCENT_FORMAT.format(slice.percentage)
                );

                double radius = diameter / 2.0;
                double labelRadius = radius + 15;
                double horizontalLineLength = 20;

                // Point on the edge of the exploded slice's arc
                this.edgePoint = new Point2D.Double(
                    sliceCenterX + Math.cos(midAngleRad) * radius,
                    sliceCenterY - Math.sin(midAngleRad) * radius
                );

                // End of the radial part of the leader line
                this.leaderLineEnd = new Point2D.Double(
                    sliceCenterX + Math.cos(midAngleRad) * labelRadius,
                    sliceCenterY - Math.sin(midAngleRad) * labelRadius
                );

                // End of the horizontal part of the leader line
                this.horizontalLineEnd = new Point2D.Double(
                    leaderLineEnd.getX() + (Math.cos(midAngleRad) >= 0 ? horizontalLineLength : -horizontalLineLength),
                    leaderLineEnd.getY()
                );

                // Position the text
                int textWidth = SwingUtilities.computeStringWidth(new JLabel().getFontMetrics(new JLabel().getFont()), text);
                if (Math.cos(midAngleRad) >= 0) { // Right side
                    this.textStart = new Point2D.Double(horizontalLineEnd.getX(), horizontalLineEnd.getY());
                } else { // Left side
                    this.textStart = new Point2D.Double(horizontalLineEnd.getX() - textWidth, horizontalLineEnd.getY());
                }
            }

            void adjustY(double amount) {
                this.leaderLineEnd.setLocation(this.leaderLineEnd.getX(), this.leaderLineEnd.getY() + amount);
                this.horizontalLineEnd.setLocation(this.horizontalLineEnd.getX(), this.horizontalLineEnd.getY() + amount);
                this.textStart.setLocation(this.textStart.getX(), this.textStart.getY() + amount);
            }
        }
    }
}
