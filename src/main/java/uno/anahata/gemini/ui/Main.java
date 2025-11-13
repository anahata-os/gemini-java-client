package uno.anahata.gemini.ui;

import com.formdev.flatlaf.FlatLightLaf;
import uno.anahata.gemini.ui.render.editorkit.DefaultEditorKitProvider;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        
        try {
            // Set FlatLaf as the Look and Feel
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            log.error("Failed to initialize FlatLaf", e);
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AI Assistant");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setPreferredSize(new Dimension(800, 600));

            // Use the new config and panel
            SwingChatConfig config = new SwingChatConfig();
            GeminiPanel geminiPanel = new GeminiPanel(new DefaultEditorKitProvider());
            geminiPanel.init(config);
            frame.add(geminiPanel);
            
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            geminiPanel.initComponents();
            geminiPanel.checkAutobackupOrStartupContent();
            //geminiPanel.init(config);
            
        });
        
        Thread.setDefaultUncaughtExceptionHandler((thread, thrwbl) -> {
            log.error("Uncaught exception in " + thread, thrwbl);
        });
    }
}
