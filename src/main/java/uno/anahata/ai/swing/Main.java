/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.swing.SwingChatConfig.UITheme;

@Slf4j
public class Main {
    public static void main(String[] args) {
        
        // Register core FlatLaf themes
        FlatLightLaf.installLafInfo();
        FlatDarkLaf.installLafInfo();
        FlatIntelliJLaf.installLafInfo();
        FlatDarculaLaf.installLafInfo();
        
        // Try to register all themes from flatlaf-intellij-themes via reflection
        try {
            Class<?> allThemesClass = Class.forName("com.formdev.flatlaf.intellijthemes.FlatAllIJThemes");
            try {
                Method installMethod = allThemesClass.getMethod("install");
                installMethod.invoke(null);
                log.info("Successfully registered FlatLaf IntelliJ themes via install().");
            } catch (NoSuchMethodException e) {
                // Fallback: manually register from INFOS field
                Field infosField = allThemesClass.getField("INFOS");
                UIManager.LookAndFeelInfo[] infos = (UIManager.LookAndFeelInfo[]) infosField.get(null);
                for (UIManager.LookAndFeelInfo info : infos) {
                    UIManager.installLookAndFeel(info);
                }
                log.info("Successfully registered {} FlatLaf IntelliJ themes via INFOS field.", infos.length);
            }
        } catch (ClassNotFoundException e) {
            log.info("FlatLaf IntelliJ themes not found on classpath.");
        } catch (Exception e) {
            log.warn("Failed to register FlatLaf IntelliJ themes", e);
        }

        // Set default LAF to Light
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            log.warn("Failed to set default FlatLightLaf", e);
        }

        SwingUtilities.invokeLater(() -> {
            String version = Main.class.getPackage().getImplementationVersion();
            if (version == null) version = "Development Build"; // Fallback for IDE runs
            
            JFrame frame = new JFrame("Anahata v1 - " + version);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setPreferredSize(new Dimension(1000, 800));
            frame.setLayout(new BorderLayout());

            ChatPanel chatPanel = new ChatPanel();
            frame.add(chatPanel, BorderLayout.CENTER);
            
            // Ensure UITheme is initialized with the current LAF
            UITheme.refresh(chatPanel.getConfig());
            
            JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            header.add(new JLabel("Global Look & Feel:"));
            
            UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
            JComboBox<UIManager.LookAndFeelInfo> lafCombo = new JComboBox<>(lafs);
            
            lafCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof UIManager.LookAndFeelInfo) {
                        setText(((UIManager.LookAndFeelInfo) value).getName());
                    }
                    return this;
                }
            });
            
            String currentLafClass = UIManager.getLookAndFeel().getClass().getName();
            for (int i = 0; i < lafs.length; i++) {
                if (lafs[i].getClassName().equals(currentLafClass)) {
                    lafCombo.setSelectedIndex(i);
                    break;
                }
            }

            lafCombo.addActionListener(e -> {
                UIManager.LookAndFeelInfo selected = (UIManager.LookAndFeelInfo) lafCombo.getSelectedItem();
                if (selected != null) {
                    try {
                        UIManager.setLookAndFeel(selected.getClassName());
                        
                        for (Window window : Window.getWindows()) {
                            SwingUtilities.updateComponentTreeUI(window);
                        }
                        
                        // Refresh the Chat internal theme to match the new LAF (if in AUTO mode)
                        UITheme.refresh(chatPanel.getConfig());
                        
                        SwingUtilities.invokeLater(() -> {
                            chatPanel.getChatPanel().redraw();
                            chatPanel.revalidate();
                            chatPanel.repaint();
                        });
                        
                    } catch (Exception ex) {
                        log.error("Failed to switch LAF", ex);
                    }
                }
            });
            
            header.add(lafCombo);
            frame.add(header, BorderLayout.NORTH);
            
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            chatPanel.checkAutobackupOrStartupContent();
        });
        
        Thread.setDefaultUncaughtExceptionHandler((thread, thrwbl) -> {
            log.error("Uncaught exception in " + thread, thrwbl);
        });
    }
}
