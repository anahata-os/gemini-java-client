/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;
import uno.anahata.ai.swing.render.editorkit.DefaultEditorKitProvider;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.ContextListener;
import uno.anahata.ai.tools.FunctionPrompter;
import uno.anahata.ai.status.ChatStatus;
import uno.anahata.ai.status.StatusListener;
import uno.anahata.ai.context.provider.ContextProvider;

@Slf4j
@Getter
public class ChatPanel extends JPanel implements ContextListener, StatusListener {

    private final Chat chat;
    private final SwingChatConfig config;

    // UI Components
    private JToolBar toolbar;
    private JButton clearButton;
    private JToggleButton liveWorkspaceButton;
    private JButton saveSessionButton;
    private JButton loadSessionButton;
    private JToggleButton functionsButton;
    private JComboBox<String> modelIdComboBox;

    private final EditorKitProvider editorKitProvider;
    private ConversationPanel chatPanel;
    private InputPanel inputPanel;
    private ContextHeatmapPanel heatmapPanel;
    private JTabbedPane tabbedPane;
    private JSplitPane mainSplitPane; // The new main layout component

    private ContextProvidersPanel contextProvidersPanel;
    private GeminiKeysPanel geminiKeysPanel;
    private ToolsPanel functionsPanel;
    private StatusPanel statusPanel;

    /**
     * Creates a new ChatPanel with default configuration and syntax highlighting.
     */
    public ChatPanel() {
        this(new SwingChatConfig());
    }

    /**
     * Creates a new ChatPanel with the specified configuration and default syntax highlighting.
     * @param config The configuration for the AI assistant.
     */
    public ChatPanel(SwingChatConfig config) {
        this(config, new DefaultEditorKitProvider());
    }

    /**
     * Creates a new ChatPanel with the specified configuration and custom syntax highlighting.
     * @param config The configuration for the AI assistant.
     * @param editorKitProvider The provider for syntax highlighting.
     */
    public ChatPanel(SwingChatConfig config, EditorKitProvider editorKitProvider) {
        super();
        this.config = config;
        this.editorKitProvider = editorKitProvider;
        
        FunctionPrompter prompter = new SwingFunctionPrompter(this);
        this.chat = new Chat(config, prompter);
        this.chat.addContextListener(this);
        this.chat.addStatusListener(this);
        
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        toolbar = new JToolBar(JToolBar.VERTICAL);
        toolbar.setFloatable(false);

        clearButton = new JButton(IconUtils.getIcon("restart.png"));
        clearButton.setToolTipText("Restart Chat");
        clearButton.addActionListener(e -> restartChat());

        functionsButton = new JToggleButton(IconUtils.getIcon("functions.png"), true);
        functionsButton.setToolTipText("Enable / Disable Functions");
        functionsButton.addActionListener(e -> chat.setFunctionsEnabled(functionsButton.isSelected()));

        liveWorkspaceButton = new JToggleButton(IconUtils.getIcon("compress.png"), true);
        liveWorkspaceButton.setToolTipText("Toggle Live Workspace View");
        
        // Initialize button state from the provider
        boolean liveEnabled = chat.getConfig().getContextProviders().stream()
                .filter(p -> "ui-screen-capture".equals(p.getId()))
                .map(ContextProvider::isEnabled)
                .findFirst()
                .orElse(false);
        liveWorkspaceButton.setSelected(liveEnabled);
        
        liveWorkspaceButton.addActionListener(e -> {
            chat.getConfig().getContextProviders().stream()
                .filter(p -> "ui-screen-capture".equals(p.getId()))
                .findFirst()
                .ifPresent(p -> p.setEnabled(liveWorkspaceButton.isSelected()));
        });

        saveSessionButton = new JButton(IconUtils.getIcon("save.png"));
        saveSessionButton.setToolTipText("Save Session");
        saveSessionButton.addActionListener(e -> saveSession());

        loadSessionButton = new JButton(IconUtils.getIcon("load.png"));
        loadSessionButton.setToolTipText("Load Session");
        loadSessionButton.addActionListener(e -> loadSession());

        toolbar.add(clearButton);
        toolbar.add(functionsButton);
        toolbar.add(liveWorkspaceButton);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(saveSessionButton);
        toolbar.add(loadSessionButton);

        add(toolbar, BorderLayout.WEST);

        // --- NORTH Panel (Model ID only) ---
        JPanel northPanel = new JPanel(new BorderLayout());
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
        northPanel.add(modelIdPanel, BorderLayout.EAST);
        add(northPanel, BorderLayout.NORTH);

        // --- SOUTH Panel (Input and Status) ---
        inputPanel = new InputPanel(this);
        statusPanel = new StatusPanel(this);

        JPanel mainSouthPanel = new JPanel(new BorderLayout());
        mainSouthPanel.add(inputPanel, BorderLayout.CENTER);
        mainSouthPanel.add(statusPanel, BorderLayout.SOUTH);
        // We will add this to the split pane later, not directly to the main panel.

        // --- CENTER Panel (Tabs) ---
        chatPanel = new ConversationPanel(this);
        heatmapPanel = new ContextHeatmapPanel();
        heatmapPanel.setToolManager(chat.getToolManager());

        contextProvidersPanel = new ContextProvidersPanel(this);
        geminiKeysPanel = new GeminiKeysPanel(config);
        functionsPanel = new ToolsPanel(chat, config);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Chat", chatPanel);
        tabbedPane.addTab("Context Heatmap", heatmapPanel);
        tabbedPane.addTab("Context Providers", contextProvidersPanel);
        tabbedPane.addTab("Tools", functionsPanel);
        tabbedPane.addTab("API Keys", geminiKeysPanel);

        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected == heatmapPanel) {
                heatmapPanel.updateContext(chat.getContext()); // Update heatmap when selected
            } else if (selected == contextProvidersPanel) {
                contextProvidersPanel.refresh();
            } else if (selected == functionsPanel) {
                functionsPanel.refresh();
            }
        });

        // --- JSplitPane for Main Layout ---
        mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, mainSouthPanel);
        mainSplitPane.setResizeWeight(0.8); // Give more space to the chat history initially
        mainSplitPane.setOneTouchExpandable(true);
        // By removing the setBorder call, we restore the default L&F border, making the divider visible.

        add(mainSplitPane, BorderLayout.CENTER);

        FileDropListener fileDropListener = new FileDropListener();
        new DropTarget(this, fileDropListener);
        new DropTarget(chatPanel, fileDropListener);
        new DropTarget(inputPanel, fileDropListener); // Also allow dropping on the input panel

        setVisible(true);
    }

    @Override
    public void contextChanged(Chat source) {
        SwingUtilities.invokeLater(() -> {
            if (tabbedPane.getSelectedComponent() == heatmapPanel) {
                heatmapPanel.updateContext(chat.getContext());
            }
        });
    }

    @Override
    public void contextCleared(Chat source) {
        // The ConversationPanel already listens and clears itself.
        // The heatmap will be cleared the next time it's selected.
    }

    @Override
    public void statusChanged(ChatStatus status, String lastExceptionToString) {
        SwingUtilities.invokeLater(() -> statusPanel.refresh());
    }

    private class FileDropListener extends DropTargetAdapter {

        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : droppedFiles) {
                    inputPanel.getAttachmentsPanel().addStagedFile(file);
                }
            } catch (Exception ex) {
                log.warn("Drag and drop failed", ex);
            }
        }
    }

    public void checkAutobackupOrStartupContent() {
        File autobackupFile = config.getAutobackupFile();
        boolean restoreAttempted = false;

        if (autobackupFile.exists() && autobackupFile.length() > 0) {
            
            /*
            int response = JOptionPane.showConfirmDialog(
                    this,
                    "An automatic backup from a previous session was found. \n"
                            + "\n\n" + autobackupFile + ""
                            + "\n\nDo you want to restore it?",
                    
                    "Anahata AI - Restore Session? " + autobackupFile.getName(),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (response == JOptionPane.YES_OPTION) {
                restoreAttempted = true;
                loadAutobackupInSwingWorker();
            }*/
            
            //trying always load
            restoreAttempted = true;
            loadAutobackupInSwingWorker();
        }

        if (!restoreAttempted) {
            initFreshChatInSwingWorker();
        }
    }

    private void loadAutobackupInSwingWorker() {
        new SwingWorker<Void, Void>() {
            Exception error = null;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    String sessionName = "autobackup-" + config.getSessionId();
                    chat.getContextManager().getSessionManager().loadSession(sessionName);
                } catch (IOException e) {
                    log.error("Exception restoring " + config.getAutobackupFile(), e);
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    log.error("Failed to load autobackup session", error);
                    JOptionPane.showMessageDialog(ChatPanel.this, "Error loading backup: " + error.getMessage() + "\nStarting a new session.", "Error", JOptionPane.ERROR_MESSAGE);
                    initFreshChatInSwingWorker();
                } else {
                    finalizeUIInitialization();
                }
            }
        }.execute();
    }

    private void initFreshChatInSwingWorker() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                chat.init();
                return null;
            }

            @Override
            protected void done() {
                finalizeUIInitialization();
            }
        }.execute();
    }

    private void finalizeUIInitialization() {
        if (config != null && config.getApi() != null) {
            modelIdComboBox.removeAllItems();
            for (String model : config.getApi().getAvailableModelIds()) {
                modelIdComboBox.addItem(model);
            }
            modelIdComboBox.setSelectedItem(config.getApi().getModelId());
            modelIdComboBox.setEnabled(true);
        }
    }

    public void restartChat() {
        chat.clear();
    }

    private void saveSession() {
        try {
            File sessionsDir = config.getWorkingFolder("sessions");
            JFileChooser fileChooser = new JFileChooser(sessionsDir);
            fileChooser.setDialogTitle("Save Session");
            fileChooser.setFileFilter(new FileNameExtensionFilter("Kryo Session Files", "kryo"));
            int userSelection = fileChooser.showSaveDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                String fileName = fileToSave.getName();
                if (!fileName.toLowerCase().endsWith(".kryo")) {
                    fileName += ".kryo";
                }
                String sessionName = StringUtils.removeEnd(fileName, ".kryo");

                chat.getContextManager().getSessionManager().saveSession(sessionName);
                JOptionPane.showMessageDialog(this, "Session saved successfully as " + sessionName, "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            log.error("Failed to save session", ex);
            JOptionPane.showMessageDialog(this, "Error saving session: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSession() {
        try {
            File sessionsDir = config.getWorkingFolder("sessions");
            JFileChooser fileChooser = new JFileChooser(sessionsDir);
            fileChooser.setDialogTitle("Load Session");
            fileChooser.setFileFilter(new FileNameExtensionFilter("Kryo Session Files", "kryo"));
            int userSelection = fileChooser.showOpenDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToLoad = fileChooser.getSelectedFile();
                String sessionId = StringUtils.removeEnd(fileToLoad.getName(), ".kryo");

                chat.getContextManager().getSessionManager().loadSession(sessionId);
                JOptionPane.showMessageDialog(this, "Session loaded successfully from " + sessionId, "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            log.error("Failed to load session", ex);
            JOptionPane.showMessageDialog(this, "Error loading session: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}