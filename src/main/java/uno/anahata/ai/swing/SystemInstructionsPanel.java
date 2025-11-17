package uno.anahata.ai.swing;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.internal.PartUtils;
import uno.anahata.ai.swing.render.ContentRenderer;

@Slf4j
public class SystemInstructionsPanel extends JPanel {

    private final AnahataPanel anahataPanel;
    private final Chat chat;
    private final SwingChatConfig config;

    private JTable providerTable;
    private ProviderTableModel tableModel;
    private JPanel rightPanel;
    private JLabel rightPanelStatusLabel;
    private SwingWorker<List<Part>, Void> contentDisplayWorker;

    public SystemInstructionsPanel(AnahataPanel anahataPanel) {
        this.anahataPanel = anahataPanel;
        this.chat = anahataPanel.getChat();
        this.config = anahataPanel.getConfig();
        initComponents();
        refresh(); // Initial data load
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Left Panel (Navigation with JTable)
        tableModel = new ProviderTableModel();
        providerTable = new JTable(tableModel);
        providerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        providerTable.setFillsViewportHeight(true);

        // Set column widths and renderers
        TableColumn enabledColumn = providerTable.getColumnModel().getColumn(0);
        enabledColumn.setMaxWidth(40);
        providerTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Provider Name
        providerTable.getColumnModel().getColumn(2).setPreferredWidth(60); // Size
        providerTable.getColumnModel().getColumn(3).setPreferredWidth(60); // Time

        providerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = providerTable.getSelectedRow();
                if (selectedRow != -1) {
                    ProviderInfo selectedProviderInfo = tableModel.getProviderInfo(selectedRow);
                    displayProviderContent(selectedProviderInfo.provider);
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(providerTable);

        JButton refreshButton = new JButton(getIcon("restart.png"));
        refreshButton.setToolTipText("Refresh All Providers (Logs Timing)");
        refreshButton.addActionListener(e -> refresh());

        JToolBar leftToolbar = new JToolBar();
        leftToolbar.setFloatable(false);
        leftToolbar.add(refreshButton);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(leftToolbar, BorderLayout.NORTH);
        leftPanel.add(tableScrollPane, BorderLayout.CENTER);

        // Right Panel (Content Viewer)
        rightPanel = new JPanel(new BorderLayout());
        rightPanelStatusLabel = new JLabel("Select a provider from the list to view its content.", SwingConstants.CENTER);
        rightPanel.add(rightPanelStatusLabel, BorderLayout.CENTER);

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(300);

        add(splitPane, BorderLayout.CENTER);
    }

    private void displayProviderContent(ContextProvider provider) {
        if (contentDisplayWorker != null && !contentDisplayWorker.isDone()) {
            contentDisplayWorker.cancel(true);
        }

        rightPanel.removeAll();
        rightPanelStatusLabel.setText("Loading content for: " + provider.getDisplayName() + "...");
        rightPanel.add(rightPanelStatusLabel, BorderLayout.CENTER);
        rightPanel.revalidate();
        rightPanel.repaint();

        if (!provider.isEnabled()) {
            rightPanelStatusLabel.setText("Provider is disabled.");
            rightPanel.revalidate();
            rightPanel.repaint();
            return;
        }

        contentDisplayWorker = new SwingWorker<List<Part>, Void>() {
            @Override
            protected List<Part> doInBackground() throws Exception {
                return provider.getParts(chat);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                rightPanel.removeAll();
                try {
                    List<Part> parts = get();
                    if (parts.isEmpty()) {
                        rightPanelStatusLabel.setText("Provider returned no content.");
                        rightPanel.add(rightPanelStatusLabel, BorderLayout.CENTER);
                    } else {
                        ContentRenderer renderer = new ContentRenderer(anahataPanel);
                        Content content = Content.builder().role(provider.getPosition() == ContextPosition.AUGMENTED_WORKSPACE ? "USER" : "SYSTEM").parts(parts).build();
                        ChatMessage fakeMessage = ChatMessage.builder().content(content).build();
                        JComponent renderedContent = renderer.render(fakeMessage);
                        JScrollPane scrollPane = new JScrollPane(renderedContent);
                        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
                        rightPanel.add(scrollPane, BorderLayout.CENTER);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Failed to get content for provider {}", provider.getId(), e);
                    rightPanelStatusLabel.setText("Error: " + e.getCause().getMessage());
                    rightPanel.add(rightPanelStatusLabel, BorderLayout.CENTER);
                } finally {
                    rightPanel.revalidate();
                    rightPanel.repaint();
                }
            }
        };
        contentDisplayWorker.execute();
    }

    public final void refresh() {
        // Store the selected provider ID to restore selection later
        int selectedRow = providerTable.getSelectedRow();
        String selectedProviderId = null;
        if (selectedRow != -1) {
            selectedProviderId = tableModel.getProviderInfo(selectedRow).provider.getId();
        }

        // Initial population of the table with provider names
        List<ProviderInfo> initialProviders = chat.getConfigManager().getContextProviders().stream()
            .map(p -> new ProviderInfo(p, -1, -1)) // Use negative values to indicate "loading"
            .collect(Collectors.toList());
        tableModel.setProviders(initialProviders);

        // Restore selection after the table model has been updated
        if (selectedProviderId != null) {
            final String finalSelectedProviderId = selectedProviderId;
            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (tableModel.getProviderInfo(i).provider.getId().equals(finalSelectedProviderId)) {
                        providerTable.setRowSelectionInterval(i, i);
                        // Optional: scroll to the selected row if it's not visible
                        // providerTable.scrollRectToVisible(providerTable.getCellRect(i, 0, true));
                        break;
                    }
                }
            });
        }

        // Now, refresh each one in its own worker thread
        for (ProviderInfo info : initialProviders) {
            refreshProvider(info);
        }
    }

    private void refreshProvider(ProviderInfo info) {
        new SwingWorker<ProviderInfo, Void>() {
            @Override
            protected ProviderInfo doInBackground() throws Exception {
                return calculateProviderInfo(info.provider);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    tableModel.updateProvider(get());
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Failed to refresh provider {}", info.provider.getId(), e);
                }
            }
        }.execute();
    }

    private ProviderInfo calculateProviderInfo(ContextProvider provider) {
        long startTime = System.currentTimeMillis();
        long size = 0;
        try {
            if (provider.isEnabled()) {
                List<Part> parts = provider.getParts(chat);
                size = parts.stream().mapToLong(PartUtils::calculateSizeInBytes).sum();
            }
        } catch (Exception e) {
            log.warn("Exception during refresh of provider '{}'", provider.getId(), e);
        }
        long duration = System.currentTimeMillis() - startTime;
        log.info("Provider '{}' took {}ms to refresh.", provider.getId(), duration);
        return new ProviderInfo(provider, size, duration);
    }

    private ImageIcon getIcon(String icon) {
        java.net.URL imgURL = getClass().getResource("/icons/" + icon);
        if (imgURL == null) {
            log.error("Could not find icon resource: /icons/{}", icon);
            return new ImageIcon();
        }
        ImageIcon originalIcon = new ImageIcon(imgURL);
        Image scaledImage = originalIcon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }

    // --- Inner Classes for JTable ---
    private static class ProviderInfo {
        final ContextProvider provider;
        long sizeInBytes;
        long timeInMillis;

        ProviderInfo(ContextProvider provider, long sizeInBytes, long timeInMillis) {
            this.provider = provider;
            this.sizeInBytes = sizeInBytes;
            this.timeInMillis = timeInMillis;
        }
    }

    private class ProviderTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Enabled", "Provider", "Size", "Time (ms)"};
        private List<ProviderInfo> providers = new ArrayList<>();

        public void setProviders(List<ProviderInfo> providers) {
            this.providers = providers;
            fireTableDataChanged();
        }

        public void updateProvider(ProviderInfo newInfo) {
            for (int i = 0; i < providers.size(); i++) {
                if (providers.get(i).provider.getId().equals(newInfo.provider.getId())) {
                    providers.set(i, newInfo);
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
        }

        public ProviderInfo getProviderInfo(int rowIndex) {
            return providers.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return providers.size();
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
            switch (columnIndex) {
                case 0: return Boolean.class;
                case 1: return String.class;
                case 2:
                case 3: return Long.class;
                default: return Object.class;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // Only the "Enabled" checkbox is editable
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProviderInfo info = providers.get(rowIndex);
            if (columnIndex == 0) {
                boolean enabled = info.provider.isEnabled();
                //log.info("getValueAt RENDER: row {}, provider '{}', isEnabled: {}", rowIndex, info.provider.getId(), enabled);
                return enabled;
            }
            
            switch (columnIndex) {
                //case 0: return info.provider.isEnabled();
                case 1: return info.provider.getDisplayName();
                case 2: return info.sizeInBytes < 0 ? "..." : info.sizeInBytes;
                case 3: return info.timeInMillis < 0 ? "..." : info.timeInMillis;
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0 && aValue instanceof Boolean) {
                ProviderInfo info = providers.get(rowIndex);
                boolean enabled = (Boolean) aValue;
                log.info("setValueAt ACTION: row {}, provider '{}', setting enabled to: {}", rowIndex, info.provider.getId(), enabled);
                info.provider.setEnabled(enabled);
                fireTableCellUpdated(rowIndex, columnIndex);

                // If the currently selected row was just toggled, refresh the content view
                if (providerTable.getSelectedRow() == rowIndex) {
                    displayProviderContent(info.provider);
                }
                // Also refresh the data for the row
                refreshProvider(info);
            }
        }
    }
}
