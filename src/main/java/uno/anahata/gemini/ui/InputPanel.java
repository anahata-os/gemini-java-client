package uno.anahata.gemini.ui;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.functions.spi.Audio;

@Slf4j
@Getter
public class InputPanel extends JPanel {

    private final GeminiPanel parentPanel;
    private final GeminiChat chat;

    // UI Components
    private JTextArea inputTextArea;
    private JButton sendButton;
    private JToggleButton micButton;
    private JButton attachButton;
    private JButton screenshotButton;
    private JButton captureFramesButton;
    private AttachmentsPanel attachmentsPanel;

    public InputPanel(GeminiPanel parentPanel) {
        super(new BorderLayout(5, 5));
        this.parentPanel = parentPanel;
        this.chat = parentPanel.getChat();
        initComponents();
    }

    private void initComponents() {
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        attachmentsPanel = new AttachmentsPanel();
        add(attachmentsPanel, BorderLayout.NORTH);

        inputTextArea = new JTextArea(3, 20);
        inputTextArea.setLineWrap(true);
        inputTextArea.setWrapStyleWord(true);

        // Ctrl+Enter to send
        KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK);
        inputTextArea.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnter, "sendMessage");
        inputTextArea.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        JScrollPane inputScrollPane = new JScrollPane(inputTextArea);
        add(inputScrollPane, BorderLayout.CENTER);

        // Panel for buttons on the south side
        JPanel southButtonPanel = new JPanel(new BorderLayout(5, 0));

        // Panel for action buttons (mic, attach, etc.) on the west
        JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        micButton = new JToggleButton(new MicrophoneIcon(24));
        micButton.setSelectedIcon(new RecordingIcon(24));
        micButton.setToolTipText("Click to start/stop recording");
        micButton.addActionListener(e -> toggleRecording());

        attachButton = new JButton(IconUtils.getIcon("attach.png"));
        attachButton.setToolTipText("Attach Files");
        attachButton.addActionListener(e -> showFileChooser());

        screenshotButton = new JButton(IconUtils.getIcon("desktop_screenshot.png"));
        screenshotButton.setToolTipText("Attach Desktop Screenshot");
        screenshotButton.addActionListener(e -> attachScreenshot());

        captureFramesButton = new JButton(IconUtils.getIcon("capture_frames.png"));
        captureFramesButton.setToolTipText("Attach Application Frames");
        captureFramesButton.addActionListener(e -> attachFrameCaptures());

        actionButtonPanel.add(micButton);
        actionButtonPanel.add(attachButton);
        actionButtonPanel.add(screenshotButton);
        actionButtonPanel.add(captureFramesButton);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());

        southButtonPanel.add(actionButtonPanel, BorderLayout.WEST);
        southButtonPanel.add(sendButton, BorderLayout.EAST);

        add(southButtonPanel, BorderLayout.SOUTH);

        // Setup Drag and Drop for the entire panel and its key components
        FileDropListener fileDropListener = new FileDropListener();
        new DropTarget(this, fileDropListener);
        new DropTarget(attachmentsPanel, fileDropListener);
        new DropTarget(inputTextArea, fileDropListener);
        new DropTarget(inputScrollPane, fileDropListener);
    }

    private void toggleRecording() {
        if (micButton.isSelected()) {
            // Start recording
            new SwingWorker<Void, Void>() {
                Exception error = null;

                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        Audio.startRecording();
                    } catch (Exception e) {
                        this.error = e;
                    }
                    return null;
                }

                @Override
                protected void done() {
                    if (error != null) {
                        log.error("Failed to start recording", error);
                        JOptionPane.showMessageDialog(InputPanel.this, "Could not start recording:\n" + error.getMessage(), "Microphone Error", JOptionPane.ERROR_MESSAGE);
                        micButton.setSelected(false); // Revert button state on error
                    }
                }
            }.execute();
        } else {
            // Stop recording
            new SwingWorker<File, Void>() {
                Exception error = null;

                @Override
                protected File doInBackground() throws Exception {
                    try {
                        return Audio.stopRecording();
                    } catch (Exception e) {
                        this.error = e;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    try {
                        File audioFile = get();
                        if (error != null) {
                            log.error("Failed to stop recording", error);
                            JOptionPane.showMessageDialog(InputPanel.this, "Error during recording:\n" + error.getMessage(), "Recording Error", JOptionPane.ERROR_MESSAGE);
                        } else if (audioFile != null) {
                            attachmentsPanel.addStagedFile(audioFile);
                            inputTextArea.setText("Transcribe the attached audio file.");
                            sendMessage();
                        }
                    } catch (Exception e) {
                        log.error("Failed to get recording result", e);
                    }
                }
            }.execute();
        }
    }

    private void sendMessage() {
        final String text = inputTextArea.getText().trim();
        final List<Part> stagedParts = attachmentsPanel.getStagedParts();
        if (text.isEmpty() && stagedParts.isEmpty()) {
            return;
        }

        requestInProgress();

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
                    get(); // Catches exceptions from doInBackground
                } catch (Exception e) {
                    log.error("Exception sending message", e);
                } finally {
                    attachmentsPanel.clearStagedParts();
                    enableInputAndRequestFocus();
                }
            }
        }.execute();
    }

    private void requestInProgress() {
        inputTextArea.setEnabled(false);
        sendButton.setEnabled(false);
        micButton.setEnabled(false);
        attachButton.setEnabled(false);
        screenshotButton.setEnabled(false);
        captureFramesButton.setEnabled(false);
    }

    private void enableInputAndRequestFocus() {
        inputTextArea.setText("");
        inputTextArea.setEnabled(true);
        sendButton.setEnabled(true);
        micButton.setEnabled(true);
        attachButton.setEnabled(true);
        screenshotButton.setEnabled(true);
        captureFramesButton.setEnabled(true);
        inputTextArea.requestFocusInWindow();
    }

    private void showFileChooser() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Images & Text", "jpg", "jpeg", "png", "gif", "txt", "java", "md"));
        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();
            for (File file : files) {
                attachmentsPanel.addStagedFile(file);
            }
        }
    }

    private void attachScreenshot() {
        attachmentsPanel.addAll(UICapture.screenshotAllScreenDevices());
    }



    private void attachFrameCaptures() {
        attachmentsPanel.addAll(UICapture.screenshotAllJFrames());
    }


    /**
     * Handles file drops for the entire input panel area.
     */
    private class FileDropListener extends DropTargetAdapter {

        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : droppedFiles) {
                    getAttachmentsPanel().addStagedFile(file);
                }
            } catch (Exception ex) {
                log.warn("Drag and drop failed", ex);
            }
        }
    }
}
