package uno.anahata.gemini.ui;

import uno.anahata.gemini.ui.render.editorkit.DefaultEditorKitProvider;
import java.awt.Dimension;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    public static void main(String[] args) {
        
        /*
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AI Assistant");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setPreferredSize(new Dimension(800, 600));

            // Use the new config and panel
            SwingGeminiConfig config = new SwingGeminiConfig();
            GeminiPanel geminiPanel = new GeminiPanel(new DefaultEditorKitProvider());
            geminiPanel.init(config);
            frame.add(geminiPanel);
            
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            geminiPanel.initComponents();
            geminiPanel.initChatInSwingWorker();
            //geminiPanel.init(config);
            
        });
        
        Thread.setDefaultUncaughtExceptionHandler((thread, thrwbl) -> {
            logger.log(Level.SEVERE, "Uncaught exception in " + thread, thrwbl);
        });
    }
}
