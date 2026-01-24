/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import lombok.extern.slf4j.Slf4j;
import org.jdesktop.swingx.prompt.PromptSupport;
import uno.anahata.ai.config.ChatConfig;

/**
 * A UI panel for managing Gemini API keys.
 * It allows users to enter multiple keys, save them to a local file,
 * and provides a link to obtain new keys from Google AI Studio.
 * 
 * @author anahata
 */
@Slf4j
public class GeminiKeysPanel extends JPanel {
    private final JTextArea keysTextArea;
    private final File keysFile;
    private final ChatConfig config;

    /**
     * Constructs a new GeminiKeysPanel.
     * 
     * @param config The chat configuration.
     */
    public GeminiKeysPanel(ChatConfig config) {
        super(new BorderLayout(10, 10));
        this.config = config;
        this.keysFile = new File(config.getWorkingFolder(), config.getApiKeyFileName());
        
        keysTextArea = new JTextArea();
        if (config instanceof SwingChatConfig) {
            keysTextArea.setFont(((SwingChatConfig) config).getTheme().getMonoFont());
        }
        
        // Set the hint using SwingX PromptSupport
        String hint = "# Gemini API Key Configuration\n" +
                "# -----------------------------\n" +
                "# Lines starting with '#' are discarded.\n" +
                "# You can put comments after each key using '//'.\n" +
                "# Pro Tip: Use multiple keys (one per Gmail) to increase your total quota!\n" +
                "\n" +
                "AIzaSyB1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R // visca.el.barsa@gmail.com\n" +
                "AIzaSyA9B8C7D6E5F4G3H2I1J0K9L8M7N6O5P4Q // roblox.pet.life.1@gmail.com\n" +
                "AIzaSyZ1Y2X3W4V5U6T7S8R9Q0P1O2N3M4L5K6J // roblox.pet.life.2@gmail.com\n" +
                "AIzaSyD5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T // geertjan.wilenga.plays.the.guitar@gmail.com\n" +
                "AIzaSyX1W2V3U4T5S6R7Q8P9O0N1M2L3K4J5I6H // kovalsky.has.a.plan@gmail.com\n" +
                "AIzaSyM1N2O3P4Q5R6S7T8U9V0W1X2Y3Z4A5B6C // the_guy_from_the_cafeteria@gmail.com\n" +
                "AIzaSyQ1R2S3T4U5V6W7X8Y9Z0A1B2C3D4E5F6G // gal.gadot.numberonefan@gmail.com\n" +
                "AIzaSyH1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X // my.mum.is.watching.me@gmail.com\n" +
                "AIzaSyG1O2S3L4I5N6G7S8G9O0D1F2A3T4H5E6R // goslings.godfather.java@gmail.com\n" +
                "AIzaSyL1A2R3R4Y5B6U7Y8U9S0A1L2L3I4S5O6N // larry.buy.us@gmail.com\n" +
                "AIzaSyJ1O2N3A4T5H6A7N8S9C0H1W2A3R4T5Z6P // jonathan.shwarz.open.source.pony.tail@gmail.com\n" +
                "AIzaSyN1E2T3B4E5A6N7S8F9O0R1E2V3E4R5G6L // netbeans.forever@gmail.com\n" +
                "AIzaSyY1A2R3D4A5T6U7L8A9C0H1T2H3E4A5R6C // yarda.tulach.the.architect@gmail.com\n" +
                "AIzaSyT1I2M3B4O5U6D7R8E9A0U1W2I3Z4A5R6D // tim.boudreau.the.swing.wizard@gmail.com\n" +
                "AIzaSyJ1E2S3S4E5G6L7I8C9K0M1A2V3E4N5M6A // jesse.glick.the.maven.master@gmail.com";
        
        PromptSupport.setPrompt(hint, keysTextArea);
        PromptSupport.setFocusBehavior(PromptSupport.FocusBehavior.SHOW_PROMPT, keysTextArea);
        
        JLabel linkLabel = new JLabel("<html><a href=''>Get Gemini API Key from Google AI Studio</a></html>");
        linkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        linkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://aistudio.google.com/app/apikey"));
                } catch (Exception ex) {
                    log.error("Failed to open API key URL", ex);
                }
            }
        });

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveKeys());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 5));
        buttonPanel.add(linkLabel);
        buttonPanel.add(saveButton);
        
        add(new JScrollPane(keysTextArea), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        loadKeys();
    }

    /**
     * Loads the API keys from the configuration file.
     */
    private void loadKeys() {
        try {
            if (keysFile.exists()) {
                String content = Files.readString(keysFile.toPath());
                keysTextArea.setText(content);
            }
        } catch (IOException e) {
            log.error("Failed to load Gemini API keys", e);
            keysTextArea.setText("Error loading keys file: " + e.getMessage());
        }
    }

    /**
     * Saves the current content of the text area to the API keys file
     * and reloads the Gemini API.
     */
    private void saveKeys() {
        try {
            Files.writeString(keysFile.toPath(), keysTextArea.getText());
            config.getApi().reload(); // Reload GeminiAPI after saving keys
            JOptionPane.showMessageDialog(this, "API keys saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            log.error("Failed to save Gemini API keys", e);
            JOptionPane.showMessageDialog(this, "Error saving keys file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
