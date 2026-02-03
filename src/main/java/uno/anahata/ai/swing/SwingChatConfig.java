/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.UIManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.config.ChatConfig;
import uno.anahata.ai.media.functions.spi.AudioTool;
import uno.anahata.ai.media.functions.spi.DJTool;
import uno.anahata.ai.media.functions.spi.PianoTool;
import uno.anahata.ai.media.functions.spi.RadioTool;
import uno.anahata.ai.status.ChatStatus;
import uno.anahata.ai.swing.context.provider.ApplicationFramesContextProvider;
import uno.anahata.ai.swing.render.PartType;
import uno.anahata.ai.swing.tools.spi.ScreenCapture;

/**
 * A simple, concrete ChatConfig for standalone Swing applications.
 */
@Slf4j
@Getter
@Setter
public class SwingChatConfig extends ChatConfig {
    
    public enum ThemeMode {
        AUTO("Auto-detect"),
        LIGHT("Classic Light"),
        DARK("Vibrant Dark"),
        MINIMALIST("Minimalist (B&W)");

        private final String displayName;
        ThemeMode(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    private boolean audioFeedbackEnabled = true; 
    private ThemeMode themeMode = ThemeMode.AUTO;

    public SwingChatConfig() {
        providers.add(new ApplicationFramesContextProvider());
    }
    
    @Override
    public String getSessionId() {
        return "standalone";
    }

    @Override
    public List<Class<?>> getToolClasses() {
        List<Class<?>> ret = super.getToolClasses();
        ret.add(ScreenCapture.class);
        ret.add(AudioTool.class);
        ret.add(RadioTool.class);
        ret.add(PianoTool.class);
        ret.add(DJTool.class);
        return ret;
    }

    public static Color getColor(ChatStatus status) {
        switch (status) {
            case API_CALL_IN_PROGRESS:
                return new Color(0, 123, 255); // BLUE
            case TOOL_EXECUTION_IN_PROGRESS:
                return new Color(128, 0, 128); // PURPLE
            case AUGMENTING_CONTEXT:
                return new Color(255, 140, 0); // DARK ORANGE
            case WAITING_WITH_BACKOFF:
                return new Color(255, 0, 0); // RED
            case IDLE_WAITING_FOR_USER:
                return new Color(0, 128, 0); // GREEN
            default:
                return Color.BLACK;
        }
    }
    
    public static Color getColorForContextUsage(double percentage) {
        if (percentage > 1.0) {
            return new Color(150, 0, 0); // Dark Red
        } else if (percentage > 0.9) {
            return new Color(255, 50, 50); // Red
        } else if (percentage > 0.7) {
            return new Color(255, 193, 7); // Yellow/Amber
        } else {
            return new Color(40, 167, 69); // Green
        }
    }

    @Getter
    public static class UITheme {
        private static UITheme instance;
        
        public static UITheme get() {
            if (instance == null) {
                throw new IllegalStateException("UITheme.get() called before initialization. You must call UITheme.refresh(config) first.");
            }
            return instance;
        }
        
        public static void refresh(ChatConfig config) {
            instance = new UITheme(config);
        }

        // General
        private final Color fontColor;
        private final Color secondaryFontColor;
        private final Font monoFont = new Font("SF Mono", Font.PLAIN, 13);

        // Standard LAF Colors
        private final Color panelBg;
        private final Color border;

        // Role-based Colors (The "Anahata" Identity)
        private final Color userHeaderBg;
        private final Color userContentBg;
        private final Color userHeaderFg;
        private final Color userBorder;

        private final Color modelHeaderBg;
        private final Color modelContentBg;
        private final Color modelHeaderFg;
        private final Color modelBorder;

        private final Color toolHeaderBg;
        private final Color toolContentBg;
        private final Color toolHeaderFg;
        private final Color toolBorder;
        
        private final ThemeMode mode;
        private final boolean dark;

        // Function Call/Response
        private final Color functionCallBg;
        private final Color functionCallFg;
        private final Color functionResponseBg;
        private final Color functionResponseFg;
        private final Color functionErrorBg;
        private final Color functionErrorFg;
        
        // UI Errors (for tables, labels, etc.)
        private final Color errorBg;
        private final Color errorFg;

        private UITheme(ChatConfig config) {
            if (config instanceof SwingChatConfig) {
                this.mode = ((SwingChatConfig) config).getThemeMode();
            } else {
                this.mode = ThemeMode.AUTO;
            }
            
            Color editorBg = UIManager.getColor("EditorPane.background");
            if (editorBg == null) editorBg = UIManager.getColor("TextArea.background");
            if (editorBg == null) editorBg = UIManager.getColor("Panel.background");
            panelBg = editorBg != null ? editorBg : Color.WHITE;
            
            Color sep = UIManager.getColor("Separator.foreground");
            if (sep == null) sep = UIManager.getColor("Component.borderColor");
            border = sep != null ? sep : (isDarkColor(panelBg) ? new Color(80, 80, 80) : Color.LIGHT_GRAY);
            
            boolean isDarkLaf = isDarkColor(panelBg);
            this.dark = (mode == ThemeMode.DARK) || (mode == ThemeMode.AUTO && isDarkLaf);
            boolean useMinimalist = (mode == ThemeMode.MINIMALIST);
            
            if (useMinimalist) {
                // Minimalist B&W Mode
                fontColor = UIManager.getColor("Label.foreground") != null ? UIManager.getColor("Label.foreground") : Color.BLACK;
                secondaryFontColor = blend(fontColor, panelBg, 0.6f);
                
                userHeaderBg = panelBg; userContentBg = panelBg; userHeaderFg = fontColor; userBorder = border;
                modelHeaderBg = panelBg; modelContentBg = panelBg; modelHeaderFg = fontColor; modelBorder = border;
                toolHeaderBg = panelBg; toolContentBg = panelBg; toolHeaderFg = fontColor; toolBorder = border;
                functionCallBg = panelBg; functionCallFg = fontColor;
                functionResponseBg = panelBg; functionResponseFg = fontColor;
                functionErrorBg = panelBg; functionErrorFg = fontColor;
                errorBg = panelBg; errorFg = new Color(200, 0, 0);
            } else if (this.dark) {
                // Vibrant Dark Mode
                fontColor = new Color(230, 230, 230);
                secondaryFontColor = new Color(160, 160, 160);
                
                userHeaderBg = new Color(35, 70, 35); userContentBg = new Color(25, 45, 25); userHeaderFg = new Color(180, 255, 180); userBorder = new Color(60, 120, 60);
                modelHeaderBg = new Color(30, 50, 90); modelContentBg = new Color(20, 30, 55); modelHeaderFg = new Color(180, 210, 255); modelBorder = new Color(50, 90, 160);
                toolHeaderBg = new Color(70, 30, 70); toolContentBg = new Color(45, 20, 45); toolHeaderFg = new Color(255, 180, 255); toolBorder = new Color(120, 60, 120);
                
                functionCallBg = new Color(40, 45, 55); functionCallFg = new Color(0, 229, 255);
                functionResponseBg = Color.BLACK; functionResponseFg = new Color(0, 255, 0);
                functionErrorBg = new Color(60, 20, 20); functionErrorFg = new Color(255, 150, 150);
                errorBg = new Color(100, 30, 30); errorFg = new Color(255, 180, 180);
            } else {
                // Classic Anahata Light Mode (Restored)
                fontColor = new Color(30, 30, 30);
                secondaryFontColor = new Color(100, 100, 100);
                
                userHeaderBg = new Color(212, 237, 218); userContentBg = new Color(233, 247, 239); userHeaderFg = new Color(21, 87, 36); userBorder = new Color(144, 198, 149);
                modelHeaderBg = new Color(221, 234, 248); modelContentBg = new Color(240, 248, 255); modelHeaderFg = new Color(0, 123, 255); modelBorder = new Color(160, 195, 232);
                toolHeaderBg = new Color(223, 213, 235); toolContentBg = new Color(250, 248, 252); toolHeaderFg = new Color(80, 60, 100); toolBorder = new Color(200, 180, 220);
                
                functionCallBg = new Color(28, 37, 51); functionCallFg = new Color(0, 229, 255);
                functionResponseBg = Color.BLACK; functionResponseFg = new Color(0, 255, 0);
                functionErrorBg = new Color(51, 28, 28); functionErrorFg = new Color(255, 80, 80);
                errorBg = new Color(255, 210, 210); errorFg = new Color(180, 0, 0);
            }
            
            log.info("UITheme initialized: mode={}, isDarkLaf={}, panelBg={}", mode, isDarkLaf, toHex(panelBg));
        }
        
        public Color getHeatmapRowBg(String role) {
            if (isMinimalist()) return panelBg;
            if (role == null) return panelBg;
            switch (role.toLowerCase()) {
                case "user": return userHeaderBg;
                case "model": return modelHeaderBg;
                case "tool": return toolHeaderBg;
                default: return panelBg;
            }
        }

        public Color getHeatmapRowFg(String role) {
            if (isMinimalist()) return fontColor;
            if (role == null) return fontColor;
            switch (role.toLowerCase()) {
                case "user": return userHeaderFg;
                case "model": return modelHeaderFg;
                case "tool": return toolHeaderFg;
                default: return fontColor;
            }
        }
        
        public Color getPieColor(String role, boolean isError) {
            if (isError) return new Color(216, 59, 1); // Vibrant Red for errors
            
            if (role == null) return secondaryFontColor;
            switch (role.toLowerCase()) {
                case "user": return userHeaderFg;
                case "model": return modelHeaderFg;
                case "tool": return toolHeaderFg;
                default: return secondaryFontColor;
            }
        }

        public boolean isMinimalist() {
            return mode == ThemeMode.MINIMALIST;
        }
        
        public boolean isDark() {
            return dark;
        }

        private boolean isDarkColor(Color c) {
            return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) < 128;
        }
        
        private Color blend(Color c1, Color c2, float ratio) {
            int r = (int) (c1.getRed() * ratio + c2.getRed() * (1 - ratio));
            int g = (int) (c1.getGreen() * ratio + c2.getGreen() * (1 - ratio));
            int b = (int) (c1.getBlue() * ratio + c2.getBlue() * (1 - ratio));
            return new Color(r, g, b);
        }
        
        public Color getDefaultBorder() { return border; }
        
        public Color getGroundingContentBg() { return userContentBg; }
        public Color getGroundingHeaderBg() { return userHeaderBg; }
        public Color getGroundingDetailsHeaderBg() { return userHeaderBg; }
        public Color getGroundingDetailsHeaderColor() { return userHeaderFg; }
        public Color getGroundingDetailsContentBg() { return userContentBg; }
        public Color getChipText() { return userHeaderFg; }
        public Color getChipBackground() { return userHeaderBg; }
        public Color getChipBorder() { return userBorder; }

        public String getFontColorHex() {
            return toHex(fontColor);
        }

        public String getSecondaryFontColorHex() {
            return toHex(secondaryFontColor);
        }
        
        private String toHex(Color c) {
            return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }
    }
}
