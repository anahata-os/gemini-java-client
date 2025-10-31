package uno.anahata.gemini.ui;

import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;
import uno.anahata.gemini.ui.render.editorkit.DefaultEditorKitProvider;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.context.ContextListener;
import uno.anahata.gemini.functions.FunctionPrompter;
import uno.anahata.gemini.functions.spi.ContextWindow;

@Slf4j
public class GeminiPanel extends JPanel implements ContextListener {

    private GeminiChat chat;
    private SwingGeminiConfig config;

    private JLabel usageLabel;
    private JToolBar toolbar;
    private JButton clearButton;
    private JButton attachButton;
    private JButton screenshotButton;
    private JButton captureFramesButton;
    private JButton saveSessionButton;
    private JButton loadSessionButton;
    private JToggleButton functionsButton;
    private JComboBox<String> modelIdComboBox;

    private final EditorKitProvider editorKitProvider;
    private ChatPanel chatPanel;
    private ContextHeatmapPanel heatmapPanel;
    private JTabbedPane tabbedPane;
    
    private SystemInstructionsPanel systemInstructionsPanel;
    private GeminiKeysPanel geminiKeysPanel;
    private FunctionsPanel functionsPanel;

    public GeminiPanel() {
        this(new DefaultEditorKitProvider());
    }

    public GeminiPanel(EditorKitProvider editorKitProvider) {
        super();
        this.editorKitProvider = editorKitProvider;
    }

    public void init(SwingGeminiConfig config) {
        this.config = config;
        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        FunctionPrompter prompter = new SwingFunctionPrompter(topFrame, editorKitProvider);
        this.chat = new GeminiChat(config, prompter);
        this.chat.addContextListener(this);
    }

    public GeminiChat getChat() {
        return chat;
    }

    private ImageIcon getIcon(String icon) {
        ImageIcon originalIcon = new ImageIcon(getClass().getResource("/icons/" + icon));
        Image scaledImage = originalIcon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }

    public void initComponents() {
        setLayout(new BorderLayout(5, 5));

        toolbar = new JToolBar(JToolBar.VERTICAL);
        toolbar.setFloatable(false);

        clearButton = new JButton(getIcon("restart.png"));
        clearButton.setToolTipText("Restart Chat");
        clearButton.addActionListener(e -> restartChat());
        functionsButton = new JToggleButton(getIcon("functions.png"), true);
        functionsButton.setToolTipText("Enable / Disable Functions");
        functionsButton.addActionListener(e -> chat.setFunctionsEnabled(functionsButton.isSelected()));

        attachButton = new JButton(getIcon("attach.png"));
        attachButton.setToolTipText("Attach Files");
        attachButton.addActionListener(e -> chatPanel.getAttachmentsPanel().showFileChooser());

        screenshotButton = new JButton(getIcon("desktop_screenshot.png"));
        screenshotButton.setToolTipText("Attach Desktop Screenshot");
        screenshotButton.addActionListener(e -> attachScreenshot());

        captureFramesButton = new JButton(getIcon("capture_frames.png"));
        captureFramesButton.setToolTipText("Attach Application Frames");
        captureFramesButton.addActionListener(e -> attachFrameCaptures());

        saveSessionButton = new JButton(getIcon("save.png"));
        saveSessionButton.setToolTipText("Save Session");
        saveSessionButton.addActionListener(e -> saveSession());

        loadSessionButton = new JButton(getIcon("load.png"));
        loadSessionButton.setToolTipText("Load Session");
        loadSessionButton.addActionListener(e -> loadSession());

        toolbar.add(clearButton);
        toolbar.add(functionsButton);
        toolbar.add(attachButton);
        toolbar.add(screenshotButton);
        toolbar.add(captureFramesButton);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(saveSessionButton);
        toolbar.add(loadSessionButton);

        add(toolbar, BorderLayout.WEST);

        JPanel topPanel = new JPanel(new BorderLayout());
        usageLabel = new JLabel("Usage: 0 / 1,000,000 Tokens");
        topPanel.add(usageLabel, BorderLayout.WEST);

        modelIdComboBox = new JComboBox<>();
        if (config != null && config.getApi() != null) {
            for (String model : config.getApi().getAvailableModelIds()) {
                modelIdComboBox.addItem(model);
            }
            modelIdComboBox.setSelectedItem(config.getApi().getModelId());
        } else {
            modelIdComboBox.addItem("Loading...");
            modelIdComboBox.setEnabled(false);
        }

        modelIdComboBox.addActionListener(e -> {
            String selectedModel = (String) modelIdComboBox.getSelectedItem();
            if (selectedModel != null && config != null && config.getApi() != null) {
                config.getApi().setModelId(selectedModel);
                log.info("Model ID changed to: {}", selectedModel);
            }
        });

        JPanel modelIdPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        modelIdPanel.add(new JLabel("Model:"));
        modelIdPanel.add(modelIdComboBox);
        topPanel.add(modelIdPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        chatPanel = new ChatPanel(chat, editorKitProvider, config);
        heatmapPanel = new ContextHeatmapPanel();
        heatmapPanel.setFunctionManager(chat.getFunctionManager());
        
        systemInstructionsPanel = new SystemInstructionsPanel(chat, editorKitProvider, config);
        geminiKeysPanel = new GeminiKeysPanel(config);
        functionsPanel = new FunctionsPanel(chat, config);
        
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Chat", chatPanel);
        tabbedPane.addTab("Context Heatmap", heatmapPanel);
        tabbedPane.addTab("System Instructions", systemInstructionsPanel);
        tabbedPane.addTab("Functions", functionsPanel);
        tabbedPane.addTab("API Keys", geminiKeysPanel);
        
        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected == chatPanel) {
                chatPanel.redraw(); // Redraw the chat when its tab is selected
            } else if (selected == heatmapPanel) {
                heatmapPanel.updateContext(chat.getContext()); // Update heatmap when selected
            } else if (selected == systemInstructionsPanel) {
                systemInstructionsPanel.refresh();
            } else if (selected == functionsPanel) {
                functionsPanel.refresh();
            }
        });
        
        add(tabbedPane, BorderLayout.CENTER);

        FileDropListener fileDropListener = new FileDropListener();
        new DropTarget(this, fileDropListener);
        new DropTarget(chatPanel, fileDropListener);

        setVisible(true);
    }

    public static class ScrollablePanel extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24; // Increased for faster scrolling
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private class FileDropListener extends DropTargetAdapter {

        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : droppedFiles) {
                    chatPanel.getAttachmentsPanel().addStagedFile(file);
                }
            } catch (Exception ex) {
                log.warn("Drag and drop failed", ex);
            }
        }
    }

    public void initChatInSwingWorker() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                chat.init();
                return null;
            }

            @Override
            protected void done() {
                if (config != null && config.getApi() != null) {
                    modelIdComboBox.removeAllItems();
                    for (String model : config.getApi().getAvailableModelIds()) {
                        modelIdComboBox.addItem(model);
                    }
                    modelIdComboBox.setSelectedItem(config.getApi().getModelId());
                    modelIdComboBox.setEnabled(true);
                }
            }
        }.execute();
    }

    @Override
    public void contextCleared(GeminiChat source) {
        SwingUtilities.invokeLater(() -> {
            updateUsageLabel(null);
            heatmapPanel.updateContext(Collections.emptyList());
            systemInstructionsPanel.refresh();
            initChatInSwingWorker();
        });
    }

    @Override
    public void contextChanged(GeminiChat source) {
        SwingUtilities.invokeLater(() -> {
            List<ChatMessage> currentContext = chat.getContext();
            if (!currentContext.isEmpty()) {
                updateUsageLabel(currentContext.get(currentContext.size() - 1).getUsageMetadata());
            }
            // Also update the heatmap in the background so it's ready when the user clicks the tab
            heatmapPanel.updateContext(currentContext);
        });
    }

    public void restartChat() {
        chat.clear();
    }

    private void attachScreenshot() {
        chatPanel.getAttachmentsPanel().addAll(UICapture.screenshotAllScreenDevices());
    }

    private void attachFrameCaptures() {
        chatPanel.getAttachmentsPanel().addAll(UICapture.screenshotAllJFrames());
    }

    private void updateUsageLabel(GenerateContentResponseUsageMetadata usage) {
        if (usage != null) {
            int totalTokens = usage.totalTokenCount().orElse(0);
            int maxTokens = ContextWindow.getTokenThreshold();
            double percentage = ((double) totalTokens / maxTokens) * 100;
            String prompt = "Prompt:" + usage.promptTokenCount().orElse(0);
            String candidates = "Candidates:" + usage.candidatesTokenCount().orElse(0);
            String cached = "Cached:" + usage.cachedContentTokenCount().orElse(0);
            String thoughts = "Thoughts:" + usage.thoughtsTokenCount().orElse(0);
            String usageText = String.format("Usage: %d / %d Tokens (%.2f%%) %s %s %s %s",
                    totalTokens, maxTokens, percentage, prompt, candidates, cached, thoughts);
            if (usage.trafficType().isPresent()) {
                String trafficType = " Traffic:" + usage.trafficType().get().toString();
                usageText += trafficType;
            }

            usageLabel.setText(usageText);
        }
    }

    private void saveSession() {
        try {
            File sessionsDir = GeminiConfig.getWorkingFolder("sessions");
            JFileChooser fileChooser = new JFileChooser(sessionsDir);
            fileChooser.setDialogTitle("Save Session");
            fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
            int userSelection = fileChooser.showSaveDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                String fileName = fileToSave.getName();
                if (!fileName.toLowerCase().endsWith(".json")) {
                    fileName += ".json";
                }
                String sessionName = StringUtils.removeEnd(fileName, ".json");

                chat.getContextManager().saveSession(sessionName);
                JOptionPane.showMessageDialog(this, "Session saved successfully as " + sessionName, "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            log.error("Failed to save session", ex);
            JOptionPane.showMessageDialog(this, "Error saving session: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSession() {
        try {
            File sessionsDir = GeminiConfig.getWorkingFolder("sessions");
            JFileChooser fileChooser = new JFileChooser(sessionsDir);
            fileChooser.setDialogTitle("Load Session");
            fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
            int userSelection = fileChooser.showOpenDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToLoad = fileChooser.getSelectedFile();
                String sessionId = StringUtils.removeEnd(fileToLoad.getName(), ".json");

                chat.getContextManager().loadSession(sessionId);
                JOptionPane.showMessageDialog(this, "Session loaded successfully from " + sessionId, "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            log.error("Failed to load session", ex);
            JOptionPane.showMessageDialog(this, "Error loading session: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
