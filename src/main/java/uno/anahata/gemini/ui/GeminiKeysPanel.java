package uno.anahata.gemini.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.config.ChatConfig;

@Slf4j
public class GeminiKeysPanel extends JPanel {
    private final JTextArea keysTextArea;
    private final File keysFile;

    public GeminiKeysPanel(ChatConfig config) {
        super(new BorderLayout(10, 10));
        this.keysFile = new File(config.getWorkingFolder(), config.getApiKeyFileName());
        
        keysTextArea = new JTextArea();
        if (config instanceof SwingGeminiConfig) {
            keysTextArea.setFont(((SwingGeminiConfig) config).getTheme().getMonoFont());
        }
        
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveKeys());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveButton);
        
        add(new JScrollPane(keysTextArea), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        loadKeys();
    }

    private void loadKeys() {
        try {
            if (keysFile.exists()) {
                String content = Files.readString(keysFile.toPath());
                keysTextArea.setText(content);
            } else {
                keysTextArea.setText("# Enter your Gemini API keys here, one per line.\n# Lines starting with // are ignored.");
            }
        } catch (IOException e) {
            log.error("Failed to load Gemini API keys", e);
            keysTextArea.setText("Error loading keys file: " + e.getMessage());
        }
    }

    private void saveKeys() {
        try {
            Files.writeString(keysFile.toPath(), keysTextArea.getText());
            JOptionPane.showMessageDialog(this, "API keys saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            log.error("Failed to save Gemini API keys", e);
            JOptionPane.showMessageDialog(this, "Error saving keys file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
