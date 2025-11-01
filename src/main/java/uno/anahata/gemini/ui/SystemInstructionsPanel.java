package uno.anahata.gemini.ui;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Image;
import java.util.List;
import javax.swing.*;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.systeminstructions.SystemInstructionProvider;
import uno.anahata.gemini.ui.render.ContentRenderer;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

public class SystemInstructionsPanel extends JPanel {

    private final GeminiChat chat;
    private final EditorKitProvider editorKitProvider;
    private final SwingGeminiConfig config;
    
    private JToolBar systemInstructionsToolbar;
    private JPanel contentPanel;

    public SystemInstructionsPanel(GeminiChat chat, EditorKitProvider editorKitProvider, SwingGeminiConfig config) {
        this.chat = chat;
        this.editorKitProvider = editorKitProvider;
        this.config = config;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        
        systemInstructionsToolbar = new JToolBar();
        systemInstructionsToolbar.setFloatable(false);
        
        for (SystemInstructionProvider provider : chat.getSystemInstructionProviders()) {
            JToggleButton providerButton = new JToggleButton(provider.getDisplayName(), provider.isEnabled());
            providerButton.addActionListener(e -> {
                provider.setEnabled(providerButton.isSelected());
                refresh();
            });
            systemInstructionsToolbar.add(providerButton);
        }
        
        systemInstructionsToolbar.add(Box.createHorizontalGlue());
        JButton refreshButton = new JButton(getIcon("restart.png"));
        refreshButton.setToolTipText("Refresh Instructions");
        refreshButton.addActionListener(e -> refresh());
        systemInstructionsToolbar.add(refreshButton);
        
        add(systemInstructionsToolbar, BorderLayout.NORTH);
        
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24); // Increase scroll speed
        add(scrollPane, BorderLayout.CENTER);
        
        refresh();
    }
    
    public void refresh() {
        contentPanel.removeAll();
        ContentRenderer renderer = new ContentRenderer(editorKitProvider, config);
        
        for (SystemInstructionProvider provider : chat.getSystemInstructionProviders()) {
            if (provider.isEnabled()) {
                try {
                    List<Part> parts = provider.getInstructionParts(chat);
                    if (parts.isEmpty()) {
                        continue;
                    }
                    
                    JLabel header = new JLabel(provider.getId() + " " + provider.getDisplayName() + " ");
                    header.setOpaque(true);
                    header.setBackground(config.getTheme().getModelHeaderBg());
                    header.setForeground(config.getTheme().getModelHeaderFg());
                    header.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                    
                    JPanel providerPanel = new JPanel(new BorderLayout());
                    providerPanel.setBorder(BorderFactory.createLineBorder(config.getTheme().getModelBorder()));
                    providerPanel.add(header, BorderLayout.NORTH);

                    Content content = Content.builder().role("tool").parts(parts).build();
                    
                    // FIX: Use builder instead of constructor
                    ChatMessage fakeMessage = ChatMessage.builder()
                            .content(content)
                            .modelId(provider.getDisplayName() + " ") // Use display name as a placeholder model ID
                            .build();
                    
                    JComponent renderedContent = renderer.render(fakeMessage, -1, chat.getContextManager());
                    providerPanel.add(renderedContent, BorderLayout.CENTER);
                    
                    contentPanel.add(providerPanel);
                    contentPanel.add(Box.createVerticalStrut(8));

                } catch (Exception e) {
                    JLabel errorLabel = new JLabel("Error loading instructions from " + provider.getDisplayName() + ": " + e.getMessage());
                    contentPanel.add(errorLabel);
                }
            }
        }
        
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private ImageIcon getIcon(String icon) {
        ImageIcon originalIcon = new ImageIcon(getClass().getResource("/icons/" + icon));
        Image scaledImage = originalIcon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }
}
