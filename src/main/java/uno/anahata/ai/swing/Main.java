/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import com.formdev.flatlaf.FlatLightLaf;
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

            // The simplest way to integrate Anahata: just create the panel!
            ChatPanel chatPanel = new ChatPanel();
            frame.add(chatPanel);
            
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            // Start the session (restores backup or sends startup instructions)
            chatPanel.checkAutobackupOrStartupContent();
        });
        
        Thread.setDefaultUncaughtExceptionHandler((thread, thrwbl) -> {
            log.error("Uncaught exception in " + thread, thrwbl);
        });
    }
}