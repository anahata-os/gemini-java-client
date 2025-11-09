package uno.anahata.gemini.ui;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import javax.swing.JPanel;
import uno.anahata.gemini.functions.spi.ContextWindow;

/**
 * A custom JPanel that renders a progress bar for context window usage,
 * changing color based on the percentage (Green -> Yellow -> Red).
 */
public class ContextUsageBar extends JPanel {

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#.00%");
    private static final int BAR_HEIGHT = 20;
    private static final int MIN_WIDTH = 300;

    private int totalTokens = 0;
    private int maxTokens = ContextWindow.TOKEN_THRESHOLD;
    private double percentage = 0.0;
    private String usageText = "Usage: 0 / " + ContextWindow.TOKEN_THRESHOLD + " Tokens";

    public ContextUsageBar() {
        setPreferredSize(new Dimension(MIN_WIDTH, BAR_HEIGHT));
        setMinimumSize(new Dimension(MIN_WIDTH, BAR_HEIGHT));
        setFont(new Font("SansSerif", Font.BOLD, 12));
    }

    public void updateUsage(GenerateContentResponseUsageMetadata usage) {
        if (usage != null) {
            this.totalTokens = usage.totalTokenCount().orElse(0);
            this.maxTokens = ContextWindow.getTokenThreshold();
            this.percentage = (double) totalTokens / maxTokens;

            String prompt = "Prompt:" + usage.promptTokenCount().orElse(0);
            String candidates = "Candidates:" + usage.candidatesTokenCount().orElse(0);
            String cached = "Cached:" + usage.cachedContentTokenCount().orElse(0);
            String thoughts = "Thoughts:" + usage.thoughtsTokenCount().orElse(0);
            
            String percentStr = PERCENT_FORMAT.format(percentage);
            
            this.usageText = String.format("Usage: %d / %d Tokens (%s) %s %s %s %s",
                    totalTokens, maxTokens, percentStr, prompt, candidates, cached, thoughts);
            
            if (usage.trafficType().isPresent()) {
                this.usageText += " Traffic:" + usage.trafficType().get().toString();
            }
        } else {
            this.totalTokens = 0;
            this.maxTokens = ContextWindow.TOKEN_THRESHOLD;
            this.percentage = 0.0;
            this.usageText = "Usage: 0 / " + ContextWindow.TOKEN_THRESHOLD + " Tokens";
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        
        // 1. Determine Colors
        Color barColor;
        Color textColor;
        
        if (percentage > 1.0) {
            // Over 100%
            barColor = new Color(150, 0, 0); // Dark Red
            textColor = Color.WHITE;
        } else if (percentage > 0.9) {
            // 90% to 100%
            barColor = new Color(255, 50, 50); // Red
            textColor = Color.WHITE;
        } else if (percentage > 0.7) {
            // 70% to 90%
            barColor = new Color(255, 193, 7); // Yellow/Amber
            textColor = Color.BLACK;
        } else {
            // 0% to 70%
            barColor = new Color(40, 167, 69); // Green
            textColor = Color.WHITE;
        }
        
        // 2. Draw Background (for over 100% or just a subtle background)
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, width, height);

        // 3. Draw Progress Bar
        int barWidth = (int) (width * Math.min(1.0, percentage));
        g2d.setColor(barColor);
        g2d.fillRect(0, 0, barWidth, height);
        
        // If over 100%, draw a warning indicator
        if (percentage > 1.0) {
            g2d.setColor(Color.RED);
            g2d.fillRect(0, 0, width, height);
        }

        // 4. Draw Text Overlay
        g2d.setColor(textColor);
        g2d.setFont(getFont());
        FontMetrics fm = g2d.getFontMetrics();
        int textX = (width - fm.stringWidth(usageText)) / 2;
        int textY = ((height - fm.getHeight()) / 2) + fm.getAscent();
        
        g2d.drawString(usageText, textX, textY);

        g2d.dispose();
    }
}
