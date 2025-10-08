package uno.anahata.gemini.ui;

import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;
import uno.anahata.gemini.ui.render.editorkit.DefaultEditorKitProvider;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.ContextManager;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.functions.FunctionPrompter;
import uno.anahata.gemini.functions.spi.ContextWindow;
import uno.anahata.gemini.ui.render.ContentRenderer;
import uno.anahata.gemini.ContextListener;

public class GeminiPanel extends JPanel implements ContextListener {

    private static final Logger logger = Logger.getLogger(GeminiPanel.class.getName());

    private GeminiChat chat;
    private GeminiConfig config;
    private volatile boolean contextDirty = false;

    private JLabel usageLabel;
    private JToolBar toolbar;
    private JButton clearButton;
    private JButton attachButton;
    private JButton screenshotButton;
    private JButton captureFramesButton;
    private JButton saveSessionButton;
    private JButton loadSessionButton;
    private JToggleButton functionsButton;
    private JPanel southPanel;
    private AttachmentsPanel attachmentsPanel;
    private JTextField inputField;
    private JComboBox<String> modelIdComboBox;

    private final EditorKitProvider editorKitProvider;
    private JScrollPane chatScrollPane;
    private JPanel chatContentPanel;

    public GeminiPanel() {
        this(new DefaultEditorKitProvider());
    }

    public GeminiPanel(EditorKitProvider editorKitProvider) {
        super();
        this.editorKitProvider = editorKitProvider;
    }

    public void init(GeminiConfig config) {
        this.config = config;
        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        FunctionPrompter prompter = new SwingFunctionPrompter(topFrame, editorKitProvider);
        this.chat = new GeminiChat(config, prompter, this);
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
        attachButton.addActionListener(e -> attachmentsPanel.showFileChooser());

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
                logger.log(Level.INFO, "Model ID changed to: {0}", selectedModel);
            }
        });

        JPanel modelIdPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        modelIdPanel.add(new JLabel("Model:"));
        modelIdPanel.add(modelIdComboBox);
        topPanel.add(modelIdPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        chatContentPanel = new ScrollablePanel();
        chatContentPanel.setLayout(new BoxLayout(chatContentPanel, BoxLayout.Y_AXIS));

        chatScrollPane = new JScrollPane(chatContentPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(chatScrollPane, BorderLayout.CENTER);

        southPanel = new JPanel(new BorderLayout());
        attachmentsPanel = new AttachmentsPanel();
        inputField = new JTextField("Initializing....");
        southPanel.add(attachmentsPanel, BorderLayout.NORTH);
        southPanel.add(inputField, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        inputField.addActionListener(e -> sendMessageFromInputField());

        FileDropListener fileDropListener = new FileDropListener();
        new DropTarget(this, fileDropListener);
        new DropTarget(chatContentPanel, fileDropListener);
        new DropTarget(chatScrollPane, fileDropListener);
        new DropTarget(attachmentsPanel, fileDropListener);

        requestInProgress();
        setVisible(true);
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
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
                    attachmentsPanel.addStagedFile(file);
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Drag and drop failed", ex);
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
                enableInputFieldAndRequestAndFocus();
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
    public void contentAdded(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            updateUsageLabel(message.getUsageMetadata());

            if (contextDirty) {
                return;
            }

            if (message.getContent() != null) {
                ContentRenderer renderer = new ContentRenderer(editorKitProvider);
                int contentIdx = chat.getContextManager().getContext().size() - 1;
                JComponent messageComponent = renderer.render(message, contentIdx, chat.getContextManager());
                
                ChatMessageJPanel messageContainer = new ChatMessageJPanel(message);
                messageContainer.setLayout(new BorderLayout());
                messageContainer.add(messageComponent, BorderLayout.CENTER);
                
                chatContentPanel.add(messageContainer);
                chatContentPanel.revalidate();
                chatContentPanel.repaint();
            }

            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            SwingUtilities.invokeLater(() -> vertical.setValue(vertical.getMaximum()));
        });
    }

    @Override
    public void contextCleared() {
        SwingUtilities.invokeLater(() -> {
            contextDirty = false;
            chatContentPanel.removeAll();
            chatContentPanel.repaint();
            updateUsageLabel(null);
            attachmentsPanel.clearStagedParts();
            initChatInSwingWorker();
        });
    }

    @Override
    public void contextModified() {
        contextDirty = true;
        SwingUtilities.invokeLater(() -> renderDirtyContext());
        logger.info("Context marked as dirty. A full redraw will occur.");
    }

    private void renderDirtyContext() {
        if (!contextDirty) return;
        contextDirty = false;
        logger.info("Performing full UI redraw due to dirty context.");
        chatContentPanel.removeAll();

        for (ChatMessage chatMessage : chat.getContext()) {
            if (chatMessage.getContent() != null) {
                ContentRenderer renderer = new ContentRenderer(editorKitProvider);
                int contentIdx = chat.getContextManager().getContext().indexOf(chatMessage);
                JComponent messageComponent = renderer.render(chatMessage, contentIdx, chat.getContextManager());
                
                ChatMessageJPanel messageContainer = new ChatMessageJPanel(chatMessage);
                messageContainer.setLayout(new BorderLayout());
                messageContainer.add(messageComponent, BorderLayout.CENTER);
                
                chatContentPanel.add(messageContainer);
            }
        }

        chatContentPanel.revalidate();
        chatContentPanel.repaint();
    }

    public void restartChat() {
        requestInProgress();
        chat.clear();
    }

    private void attachScreenshot() {
        attachmentsPanel.addAll(UICapture.screenshotAllScreenDevices());
    }

    private void attachFrameCaptures() {
        attachmentsPanel.addAll(UICapture.screenshotAllJFrames());
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

    private void sendMessageFromInputField() {
        final String text = inputField.getText().trim();
        final List<Part> stagedParts = attachmentsPanel.getStagedParts();
        if (text.isEmpty() && stagedParts.isEmpty()) {
            return;
        }
        requestInProgress();
        final boolean withFunctions = functionsButton.isSelected();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (!stagedParts.isEmpty()) {
                    List<Part> apiParts = new ArrayList<>();
                    if (!text.isEmpty()) {
                        apiParts.add(Part.fromText(text));
                    }
                    apiParts.addAll(stagedParts);
                    Content contentForApi = Content.builder().role("user").parts(apiParts).build();
                    chat.sendContent(contentForApi);
                } else {
                    chat.sendText(text);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Exception sending message", e);
                } finally {
                    attachmentsPanel.clearStagedParts();
                    enableInputFieldAndRequestAndFocus();
                }
            }
        }.execute();

    }

    private void requestInProgress() {
        inputField.setEditable(false);
    }

    private void enableInputFieldAndRequestAndFocus() {
        inputField.setText("");
        inputField.setEditable(true);
        inputField.requestFocusInWindow();
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
            logger.log(Level.SEVERE, "Failed to save session", ex);
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
            logger.log(Level.SEVERE, "Failed to load session", ex);
            JOptionPane.showMessageDialog(this, "Error loading session: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
