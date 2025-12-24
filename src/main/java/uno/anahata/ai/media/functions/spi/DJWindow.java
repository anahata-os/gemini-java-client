/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.functions.spi;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A dedicated window for the DJ Dashboard, separating Swing window management
 * from the core DJ Engine logic.
 */
public class DJWindow extends JFrame {
    
    private final DJDashboard dashboard;
    private final DJEngine engine;

    public DJWindow(DJEngine engine) {
        super("Anahata DJ Dashboard");
        this.engine = engine;
        this.dashboard = new DJDashboard(engine);
        
        setIconImage(new ImageIcon(getClass().getResource("/icons/anahata_16.png")).getImage());
        setContentPane(dashboard);
        setSize(700, 600);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // We don't stop the engine on hide, just hide the window.
                // The 'Stop' button in the UI or the 'stop' tool will handle shutdown.
            }
        });
    }

    public DJDashboard getDashboard() {
        return dashboard;
    }
}
