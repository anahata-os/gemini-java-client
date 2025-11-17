package uno.anahata.ai.swing;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.swing.JPanel;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.status.ChatStatus;
import uno.anahata.ai.status.StatusManager;

/**
 * A custom JPanel that renders a progress bar for context window usage.
 */
public class ContextUsageBar extends JPanel {

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0%");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance();
    private static final int BAR_HEIGHT = 20;
    private static final int MIN_WIDTH = 250;

    private final AnahataPanel parentPanel;
    
    // State fields, updated in refresh()
    private int totalTokens = 0;
    private int maxTokens;
    private double percentage = 0.0;
    private String usageText = "0% (0 / 0)";
    private ChatStatus status = ChatStatus.IDLE_WAITING_FOR_USER;

    public ContextUsageBar(AnahataPanel parentPanel) {
        this.parentPanel = parentPanel;
        this.maxTokens = parentPanel.getChat().getContextManager().getTokenThreshold();
        setPreferredSize(new Dimension(MIN_WIDTH, BAR_HEIGHT));
        setMinimumSize(new Dimension(MIN_WIDTH, BAR_HEIGHT));
        setFont(new Font("SansSerif", Font.BOLD, 12));
        refresh(); // Initial update
    }
    
    public void refresh() {
        StatusManager statusManager = parentPanel.getChat().getStatusManager();
        ContextManager contextManager = parentPanel.getChat().getContextManager();
        
        this.status = statusManager.getCurrentStatus();
        GenerateContentResponseUsageMetadata usage = statusManager.getLastUsage();
        
        if (usage != null) {
            this.totalTokens = usage.totalTokenCount().orElse(0);
        } else {
            this.totalTokens = contextManager.getTotalTokenCount();
        }
        
        this.maxTokens = contextManager.getTokenThreshold();
        this.percentage = (maxTokens == 0) ? 0.0 : (double) totalTokens / maxTokens;
        
        String percentStr = PERCENT_FORMAT.format(percentage);
        String totalStr = NUMBER_FORMAT.format(totalTokens);
        String maxStr = NUMBER_FORMAT.format(maxTokens);
        
        this.usageText = String.format("%s (%s / %s)", percentStr, totalStr, maxStr);
        
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        
        // Determine Colors
        Color barColor;
        Color textColor;
        
        if (status == ChatStatus.WAITING_WITH_BACKOFF) {
            barColor = SwingChatConfig.getColor(status);
            textColor = Color.WHITE;
        } else {
            barColor = SwingChatConfig.getColorForContextUsage(percentage);
            // Choose black or white text based on the brightness of the bar color for readability
            double brightness = (barColor.getRed() * 0.299) + (barColor.getGreen() * 0.587) + (barColor.getBlue() * 0.114);
            textColor = (brightness > 186) ? Color.BLACK : Color.WHITE;
        }
        
        // Draw Background
        g2d.setColor(getBackground().darker());
        g2d.fillRect(0, 0, width, height);

        // Draw Progress Bar
        int barWidth = (int) (width * Math.min(1.0, percentage));
        g2d.setColor(barColor);
        g2d.fillRect(0, 0, barWidth, height);
        
        // Draw Text Overlay
        g2d.setColor(textColor);
        g2d.setFont(getFont());
        FontMetrics fm = g2d.getFontMetrics();
        int textX = (width - fm.stringWidth(usageText)) / 2;
        int textY = ((height - fm.getHeight()) / 2) + fm.getAscent();
        
        g2d.drawString(usageText, textX, textY);

        g2d.dispose();
    }
}
