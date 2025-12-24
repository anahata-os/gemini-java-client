/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.functions.spi;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A refined, non-modal UI for controlling the RadioTool with a split-pane layout
 * and a fancy LED display.
 */
public class RadioToolPanel extends JFrame {
    
    private final JTable stationTable;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;
    private final JLabel stationLabel;
    private final JButton playBtn;
    private final AtomicReference<Boolean> isPlaying = new AtomicReference<>(false);

    public RadioToolPanel() {
        super("Anahata Radio");
        
        setIconImage(new ImageIcon(getClass().getResource("/icons/anahata_16.png")).getImage());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(18, 18, 18));
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                RadioTool.stop();
            }
        });

        // --- Station Table (Left) ---
        String[] columnNames = {"OK", "Station Name", "URL"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        Map<String, String> stations = RadioTool.listStations();
        stations.forEach((name, url) -> tableModel.addRow(new Object[]{true, name, url}));

        stationTable = new JTable(tableModel);
        stationTable.setRowHeight(40); // Slightly taller for better visibility
        stationTable.setBackground(new Color(25, 25, 25));
        stationTable.setForeground(new Color(210, 210, 210));
        stationTable.setShowGrid(false);
        stationTable.setIntercellSpacing(new Dimension(0, 0));
        stationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Fancy "Neon" Renderer
        DefaultTableCellRenderer fancyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (isSelected) {
                    label.setBackground(new Color(0, 255, 150, 50)); // Vibrant semi-transparent green
                    label.setForeground(Color.WHITE);
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                    
                    // Add a "Neon Indicator" on the left of the first visible column
                    if (column == 0) {
                         label.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 5, 0, 0, new Color(0, 255, 150)),
                            BorderFactory.createEmptyBorder(0, 5, 0, 10)
                        ));
                    } else {
                        label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                    }
                    
                    if (column == 1) {
                        label.setText("▶  " + value);
                    }
                } else {
                    label.setBackground(table.getBackground());
                    label.setForeground(table.getForeground());
                    label.setFont(label.getFont().deriveFont(Font.PLAIN));
                    label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                }
                return label;
            }
        };

        stationTable.setDefaultRenderer(Object.class, fancyRenderer);
        stationTable.setDefaultRenderer(String.class, fancyRenderer);

        stationTable.getColumnModel().getColumn(0).setMaxWidth(40);
        stationTable.removeColumn(stationTable.getColumnModel().getColumn(2)); // Hide URL

        JScrollPane tableScroll = new JScrollPane(stationTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getViewport().setBackground(new Color(25, 25, 25));

        // --- Right Panel ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(18, 18, 18));

        // LED Display
        JPanel displayPanel = new JPanel(new BorderLayout());
        displayPanel.setBackground(Color.BLACK);
        displayPanel.setPreferredSize(new Dimension(320, 130));
        displayPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 255, 150)),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        statusLabel = new JLabel("● SYSTEM READY");
        statusLabel.setForeground(new Color(0, 180, 255));
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        
        stationLabel = new JLabel("ANAHATA RADIO");
        stationLabel.setForeground(new Color(50, 255, 50));
        stationLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        
        displayPanel.add(statusLabel, BorderLayout.NORTH);
        displayPanel.add(stationLabel, BorderLayout.CENTER);

        // Controls
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBackground(new Color(18, 18, 18));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 12, 10, 12);
        
        JButton prevBtn = createBtn("⏮", Color.GRAY);
        playBtn = createBtn("▶", new Color(0, 255, 150));
        JButton nextBtn = createBtn("⏭", Color.GRAY);
        
        controlPanel.add(prevBtn, gbc);
        controlPanel.add(playBtn, gbc);
        controlPanel.add(nextBtn, gbc);

        rightPanel.add(displayPanel, BorderLayout.NORTH);
        rightPanel.add(controlPanel, BorderLayout.CENTER);

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, rightPanel);
        splitPane.setDividerLocation(300);
        splitPane.setDividerSize(2);
        splitPane.setBorder(null);
        add(splitPane, BorderLayout.CENTER);

        // --- Logic ---
        playBtn.addActionListener(e -> {
            if (isPlaying.get()) {
                RadioTool.stop();
            } else {
                playSelected();
            }
        });

        stationTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = stationTable.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        stationTable.setRowSelectionInterval(row, row);
                        playSelected();
                    }
                }
            }
        });

        nextBtn.addActionListener(e -> skip(1));
        prevBtn.addActionListener(e -> skip(-1));

        setSize(800, 500);
        setLocationRelativeTo(null);
    }

    private void playSelected() {
        int row = stationTable.getSelectedRow();
        if (row != -1) {
            String url = (String) tableModel.getValueAt(row, 2);
            try {
                RadioTool.start(url);
            } catch (Exception ex) {
                updatePlaybackState(null, false);
                statusLabel.setText("● FAILED");
            }
        }
    }

    private void skip(int direction) {
        int row = stationTable.getSelectedRow();
        int count = stationTable.getRowCount();
        if (count == 0) return;
        
        for (int i = 1; i <= count; i++) {
            int nextRow = (row + (i * direction) + count) % count;
            if ((Boolean) tableModel.getValueAt(nextRow, 0)) {
                stationTable.setRowSelectionInterval(nextRow, nextRow);
                playSelected();
                break;
            }
        }
    }

    public void updatePlaybackState(String name, boolean playing) {
        isPlaying.set(playing);
        SwingUtilities.invokeLater(() -> {
            if (playing) {
                stationLabel.setText(name.toUpperCase());
                statusLabel.setText("● STREAMING LIVE");
                statusLabel.setForeground(new Color(255, 50, 50));
                playBtn.setText("⏹");
                
                // Sync table selection and scroll to it
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (name.equals(tableModel.getValueAt(i, 1))) {
                        stationTable.setRowSelectionInterval(i, i);
                        stationTable.scrollRectToVisible(stationTable.getCellRect(i, 0, true));
                        break;
                    }
                }
            } else {
                stationLabel.setText("IDLE");
                statusLabel.setText("● SYSTEM READY");
                statusLabel.setForeground(new Color(0, 180, 255));
                playBtn.setText("▶");
            }
        });
    }

    private JButton createBtn(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 48));
        b.setForeground(color);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                b.setForeground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                b.setForeground(color);
            }
        });
        
        return b;
    }
}
