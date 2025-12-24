/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

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
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jdesktop.swingx.JXTextArea;
import uno.anahata.ai.Chat;
import uno.anahata.ai.media.util.Microphone;

@Slf4j
@Getter
public class InputPanel extends JPanel {

    private final ChatPanel parentPanel;
    private final Chat chat;

    // UI Components
    private JXTextArea inputTextArea;
    private JButton sendButton;
    private JToggleButton micButton;
    private JButton attachButton;
    private JButton screenshotButton;
    private JButton captureFramesButton;
    private AttachmentsPanel attachmentsPanel;

    public InputPanel(ChatPanel parentPanel) {
        // Use a simple BorderLayout. The JScrollPane in the CENTER will automatically fill available space.
        super(new BorderLayout(5, 5));
        this.parentPanel = parentPanel;
        this.chat = parentPanel.getChat();
        initComponents();
    }

    private void initComponents() {
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        attachmentsPanel = new AttachmentsPanel();
        add(attachmentsPanel, BorderLayout.NORTH);

        // Use JXTextArea from SwingX for placeholder text.
        inputTextArea = new JXTextArea("Type your message here (Ctrl+Enter to send)");
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

        // The JScrollPane will grow to fill the CENTER of the BorderLayout.
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
                        Microphone.startRecording();
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
                        return Microphone.stopRecording();
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
                            // BUGFIX: Do not send automatically. Let the user see the attachment and click send.
                            // inputTextArea.setText(""); // Send no text, the audio is the message
                            // sendMessage();
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
        // BUGFIX: Create a defensive copy of the list. Do not get a reference that will be cleared.
        final List<Part> stagedParts = new ArrayList<>(attachmentsPanel.getStagedParts());
        
        if (text.isEmpty() && stagedParts.isEmpty()) {
            return;
        }

        // --- UX IMPROVEMENT ---
        // Clear the input immediately after capturing the content.
        inputTextArea.setText("");
        attachmentsPanel.clearStagedParts();
        // --------------------

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
                    enableInputAndRequestFocus();
                }
            }
        }.execute();
    }

    private void requestInProgress() {
        // Keep the text area enabled, but disable action buttons to prevent conflicts.
        sendButton.setEnabled(false);
        micButton.setEnabled(false);
        attachButton.setEnabled(false);
        screenshotButton.setEnabled(false);
        captureFramesButton.setEnabled(false);
    }

    private void enableInputAndRequestFocus() {
        // Re-enable action buttons. Text area is always enabled and is cleared immediately on send.
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
