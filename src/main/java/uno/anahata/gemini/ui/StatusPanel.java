package uno.anahata.gemini.ui;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.status.ApiExceptionRecord;
import uno.anahata.gemini.status.ChatStatus;
import uno.anahata.gemini.status.StatusManager;

public class StatusPanel extends JPanel {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance();

    private final GeminiPanel parentPanel;
    private final Timer refreshTimer;

    // UI Components
    private JLabel statusLabel;
    private ContextUsageBar contextUsageBar;
    private JPanel detailsPanel;
    private JLabel promptTokensLabel;
    private JLabel candidatesTokensLabel;
    private JLabel cachedTokensLabel;
    private JLabel thoughtsTokensLabel;

    public StatusPanel(GeminiPanel parentPanel) {
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

        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Initializing...");
        contextUsageBar = new ContextUsageBar(parentPanel);
        
        topPanel.add(statusLabel, BorderLayout.WEST);
        topPanel.add(contextUsageBar, BorderLayout.EAST);
        
        detailsPanel = new JPanel();
        detailsPanel.setVisible(false);
        
        promptTokensLabel = new JLabel();
        candidatesTokensLabel = new JLabel();
        cachedTokensLabel = new JLabel();
        thoughtsTokensLabel = new JLabel();

        add(topPanel, BorderLayout.NORTH);
        add(detailsPanel, BorderLayout.CENTER);
    }

    public void refresh() {
        GeminiChat chat = parentPanel.getChat();
        if (chat.isShutdown()) {
            if (refreshTimer.isRunning()) refreshTimer.stop();
            return;
        }
        
        StatusManager statusManager = chat.getStatusManager();
        ChatStatus status = statusManager.getCurrentStatus();
        long now = System.currentTimeMillis();

        // 1. Update Status Label
        statusLabel.setForeground(parentPanel.getConfig().getColor(status));
        statusLabel.setToolTipText(status.getDescription());
        
        if (status != ChatStatus.IDLE_WAITING_FOR_USER) {
            long duration = now - statusManager.getStatusChangeTime();
            statusLabel.setText(String.format("%s (%s)", status.getDisplayName(), TimeUtils.formatMillisConcise(duration)));
        } else {
            long lastDuration = statusManager.getLastOperationDuration();
            if (lastDuration > 0) {
                statusLabel.setText(String.format("%s (took %s)", status.getDisplayName(), TimeUtils.formatMillisConcise(lastDuration)));
            } else {
                statusLabel.setText(status.getDisplayName());
            }
        }

        // 2. Refresh Context Usage Bar
        contextUsageBar.refresh();

        // 3. Update Details Panel
        detailsPanel.removeAll();
        List<ApiExceptionRecord> errors = statusManager.getApiErrors();
        GenerateContentResponseUsageMetadata usage = statusManager.getLastUsage();
        boolean isRetrying = !errors.isEmpty() && (status == ChatStatus.WAITING_WITH_BACKOFF || status == ChatStatus.API_CALL_IN_PROGRESS);

        if (isRetrying) {
            detailsPanel.setLayout(new GridLayout(0, 1)); // Vertical layout for errors
            ApiExceptionRecord lastError = errors.get(errors.size() - 1);
            long totalErrorTime = now - errors.get(0).getTimestamp().getTime();
            String headerText = String.format("Retrying... Total Time: %s | Attempt: %d | Next Backoff: %dms",
                                              TimeUtils.formatMillisConcise(totalErrorTime),
                                              lastError.getRetryAttempt() + 1,
                                              lastError.getBackoffAmount());
            detailsPanel.add(new JLabel(headerText));

            for (ApiExceptionRecord error : errors) {
                String errorText = String.format("  • [%s] [..%s] %s",
                                                 TIME_FORMAT.format(error.getTimestamp()),
                                                 error.getApiKey(),
                                                 error.getException().toString());
                JLabel errorLabel = new JLabel(errorText);
                errorLabel.setForeground(Color.RED.darker());
                detailsPanel.add(errorLabel);
            }
            detailsPanel.setVisible(true);
            
        } else if (usage != null) {
            detailsPanel.setLayout(new GridLayout(0, 1)); // Vertical layout for token details
            promptTokensLabel.setText("Prompt Tokens: " + NUMBER_FORMAT.format(usage.promptTokenCount().orElse(0)));
            candidatesTokensLabel.setText("Candidates Tokens: " + NUMBER_FORMAT.format(usage.candidatesTokenCount().orElse(0)));
            cachedTokensLabel.setText("Cached Content Tokens: " + NUMBER_FORMAT.format(usage.cachedContentTokenCount().orElse(0)));
            thoughtsTokensLabel.setText("Thoughts Tokens: " + NUMBER_FORMAT.format(usage.thoughtsTokenCount().orElse(0)));
            
            detailsPanel.add(promptTokensLabel);
            detailsPanel.add(candidatesTokensLabel);
            detailsPanel.add(cachedTokensLabel);
            detailsPanel.add(thoughtsTokensLabel);
            detailsPanel.setVisible(true);
            
        } else {
            detailsPanel.setVisible(false);
        }
        
        revalidate();
        repaint();
    }
}
