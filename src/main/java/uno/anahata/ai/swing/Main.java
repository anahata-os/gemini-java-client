/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import com.formdev.flatlaf.FlatLightLaf;
import uno.anahata.ai.swing.render.editorkit.DefaultEditorKitProvider;
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
            JFrame frame = new JFrame("Anahata");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setPreferredSize(new Dimension(800, 600));

            // Use the new config and panel
            SwingChatConfig config = new SwingChatConfig();
            ChatPanel chatPanel = new ChatPanel(new DefaultEditorKitProvider());
            chatPanel.init(config);
            chatPanel.initComponents();
            frame.add(chatPanel);
            
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            
            chatPanel.checkAutobackupOrStartupContent();
            //chatPanel.init(config);
            
        });
        
        Thread.setDefaultUncaughtExceptionHandler((thread, thrwbl) -> {
            log.error("Uncaught exception in " + thread, thrwbl);
        });
    }
}
