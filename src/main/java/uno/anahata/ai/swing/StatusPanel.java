/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.ai.Chat;
import uno.anahata.ai.media.util.AudioPlayer;
import uno.anahata.ai.status.ApiExceptionRecord;
import uno.anahata.ai.status.ChatStatus;
import uno.anahata.ai.status.StatusManager;

public class StatusPanel extends JPanel {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance();

    private final ChatPanel parentPanel;
    private final Timer refreshTimer;
    private ChatStatus lastStatus = null;

    // UI Components
    private StatusIndicator statusIndicator;
    private JLabel statusLabel;
    private ContextUsageBar contextUsageBar;
    private JPanel detailsPanel;
    private JLabel tokenDetailsLabel;
    private JToggleButton soundToggle;
    private JButton killButton;

    public StatusPanel(ChatPanel parentPanel) {
        super(new BorderLayout(10, 2));
        this.parentPanel = parentPanel;
        initComponents();
        
        this.refreshTimer = new Timer(1000, e -> refresh());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refreshTimer.start();
    }

    @Override
    public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    private void initComponents() {
        setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new java.awt.Insets(0, 2, 0, 2);
        gbc.gridy = 0;
        
        statusIndicator = new StatusIndicator();
        statusLabel = new JLabel("Initializing...");
        statusLabel.setMinimumSize(new Dimension(10, 16));
        
        soundToggle = new JToggleButton(IconUtils.getIcon("bell.png"));
        soundToggle.setSelectedIcon(IconUtils.getIcon("bell_mute.png"));
        soundToggle.setToolTipText("Toggle Sound Notifications");
        soundToggle.setSelected(!parentPanel.getConfig().isAudioFeedbackEnabled());
        soundToggle.addActionListener(e -> parentPanel.getConfig().setAudioFeedbackEnabled(!soundToggle.isSelected()));

        killButton = new JButton(IconUtils.getIcon("kill.png", 16));
        killButton.setToolTipText("Interrupt current API request or tool execution");
        killButton.setVisible(false);
        killButton.addActionListener(e -> parentPanel.getChat().kill());
        killButton.setMargin(new java.awt.Insets(0, 2, 0, 2));
        killButton.setFocusable(false);

        // Left-aligned components
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;

        gbc.gridx = 0;
        topPanel.add(soundToggle, gbc);
        
        gbc.gridx = 1;
        topPanel.add(statusIndicator, gbc);
        
        gbc.gridx = 2;
        topPanel.add(killButton, gbc);
        
        // Flexible center component
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        topPanel.add(statusLabel, gbc);
        
        // Right-aligned component
        contextUsageBar = new ContextUsageBar(parentPanel);
        gbc.gridx = 4;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        topPanel.add(contextUsageBar, gbc);
        
        detailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        detailsPanel.setVisible(false);
        
        tokenDetailsLabel = new JLabel();
        detailsPanel.add(tokenDetailsLabel);

        add(topPanel, BorderLayout.NORTH);
        add(detailsPanel, BorderLayout.CENTER);
    }

    public void refresh() {
        Chat chat = parentPanel.getChat();
        if (chat.isShutdown()) {
            if (refreshTimer.isRunning()) refreshTimer.stop();
            return;
        }
        
        StatusManager statusManager = chat.getStatusManager();
        ChatStatus currentStatus = statusManager.getCurrentStatus();
        long now = System.currentTimeMillis();
        Color statusColor = parentPanel.getConfig().getColor(currentStatus);

        // Play sound on status change
        if (lastStatus != currentStatus && parentPanel.getConfig().isAudioFeedbackEnabled()) {
            handleStatusSound(currentStatus);
        }
        this.lastStatus = currentStatus;

        // 1. Update Status Indicator and Label
        statusIndicator.setColor(statusColor);
        statusLabel.setForeground(statusColor);
        statusLabel.setToolTipText(currentStatus.getDescription());
        
        String statusText = currentStatus.getDisplayName();
        if (currentStatus == ChatStatus.TOOL_EXECUTION_IN_PROGRESS && StringUtils.isNotBlank(statusManager.getExecutingToolName())) {
            statusText = String.format("%s (%s)", currentStatus.getDisplayName(), statusManager.getExecutingToolName());
        }
        
        if (currentStatus != ChatStatus.IDLE_WAITING_FOR_USER) {
            long duration = now - statusManager.getStatusChangeTime();
            statusLabel.setText(String.format("%s... (%s)", statusText, TimeUtils.formatMillisConcise(duration)));
        } else {
            long lastDuration = statusManager.getLastOperationDuration();
            if (lastDuration > 0) {
                statusLabel.setText(String.format("%s (took %s)", currentStatus.getDisplayName(), TimeUtils.formatMillisConcise(lastDuration)));
            } else {
                statusLabel.setText(currentStatus.getDisplayName());
            }
        }

        // Update Kill Button visibility
        killButton.setVisible(currentStatus.isInterruptible());

        // 2. Refresh Context Usage Bar
        contextUsageBar.refresh();

        // 3. Update Details Panel
        List<ApiExceptionRecord> errors = statusManager.getApiErrors();
        GenerateContentResponseUsageMetadata usage = statusManager.getLastUsage();
        boolean isRetrying = !errors.isEmpty() && (currentStatus == ChatStatus.WAITING_WITH_BACKOFF || currentStatus == ChatStatus.API_CALL_IN_PROGRESS);

        if (isRetrying) {
            detailsPanel.removeAll();
            detailsPanel.setLayout(new GridLayout(0, 1)); // Vertical for errors
            ApiExceptionRecord lastError = errors.get(errors.size() - 1);
            long totalErrorTime = now - errors.get(0).getTimestamp().getTime();
            String headerText = String.format("Retrying... Total Time: %s | Attempt: %d | Next Backoff: %dms",
                                              TimeUtils.formatMillisConcise(totalErrorTime),
                                              lastError.getRetryAttempt() + 1,
                                              lastError.getBackoffAmount());
            detailsPanel.add(new JLabel(headerText));

            for (ApiExceptionRecord error : errors) {
                
                String displayString = StringUtils.abbreviateMiddle(error.getException().toString(), " ... ", 108) ;
                
                String errorText = String.format("  • [%s] [..%s] %s",
                                                 TIME_FORMAT.format(error.getTimestamp()),
                                                 error.getApiKey(),
                                                 displayString);
                JLabel errorLabel = new JLabel(errorText);
                errorLabel.setForeground(Color.RED.darker());
                detailsPanel.add(errorLabel);
            }
            detailsPanel.setVisible(true);
            
        } else if (usage != null) {
            detailsPanel.removeAll();
            detailsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
            
            String prompt = "Prompt: " + NUMBER_FORMAT.format(usage.promptTokenCount().orElse(0));
            String candidates = "Candidates: " + NUMBER_FORMAT.format(usage.candidatesTokenCount().orElse(0));
            String cached = "Cached: " + NUMBER_FORMAT.format(usage.cachedContentTokenCount().orElse(0));
            String thoughts = "Thoughts: " + NUMBER_FORMAT.format(usage.thoughtsTokenCount().orElse(0));
            
            tokenDetailsLabel.setText(String.join(" | ", prompt, candidates, cached, thoughts));
            detailsPanel.add(tokenDetailsLabel);
            detailsPanel.setVisible(true);
            
        } else {
            detailsPanel.setVisible(false);
        }
        
        revalidate();
        repaint();
    }
    
    private void handleStatusSound(ChatStatus newStatus) {
        String soundFileName = newStatus.name().toLowerCase() + ".wav";
        AudioPlayer.playSound(soundFileName);
    }
    
    /**
     * A simple component that paints a colored circle.
     */
    private static class StatusIndicator extends JComponent {
        private Color color = Color.GRAY;

        public StatusIndicator() {
            Dimension size = new Dimension(16, 16);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        public void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            // Draw a 12x12 circle centered in the 16x16 component
            g2d.fillOval(2, 2, 12, 12);
            g2d.dispose();
        }
    }
}