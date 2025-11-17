/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.ai.swing;

import com.google.genai.types.Part;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import uno.anahata.ai.internal.PartUtils;

/**
 * JPanel for attachments
 * @author pablo
 */
public class AttachmentsPanel extends JPanel{
    
    
    public final List<Part> stagedParts = new ArrayList<>();
    
    private final JFileChooser stagedFilesChooser = new JFileChooser();

    public AttachmentsPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 2));
    }

    public List<Part> getStagedParts() {
        return stagedParts;
    }
    
    
    public void clearStagedParts() {
        stagedParts.clear();
        updateStagedFilesUI();
    }
    
    
    public void showFileChooser() {
        stagedFilesChooser.setMultiSelectionEnabled(true);
        if (stagedFilesChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File file : stagedFilesChooser.getSelectedFiles()) {
                addStagedFile(file);
            }
        }
    }
    
    public void updateStagedFilesUI() {
        removeAll();
        for (Part stagedFile : stagedParts) {
            JPanel fileEntryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            fileEntryPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1, true));
            JLabel nameLabel = new JLabel(PartUtils.toString(stagedFile.inlineData().get()));
            fileEntryPanel.add(nameLabel);
            JButton removeButton = new JButton("x");
            removeButton.setMargin(new java.awt.Insets(0, 2, 0, 2));
            removeButton.addActionListener(e -> {
                stagedParts.remove(stagedFile);
                updateStagedFilesUI();
            });
            fileEntryPanel.add(removeButton);
            add(fileEntryPanel);
        }
        revalidate();
        repaint();
    }
    
    public void addAll(Collection<File> files) {
        for (File file : files) {
            addStagedFile(file);
        }
    }

    public void addStagedFile(File file) {
        if (file == null) {
            return;
        }
        try {
            Part apiPart = PartUtils.toPart(file);
            stagedParts.add(apiPart);
            updateStagedFilesUI();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error reading file: " + e.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
/**
     * Wrapper for attached files
     */
    public static class StagedFile {

        final Part apiPart;
        final Part placeholderPart;
        final String fileName;

        StagedFile(Part apiPart, Part placeholderPart, String fileName) {
            this.apiPart = apiPart;
            this.placeholderPart = placeholderPart;
            this.fileName = fileName;
        }
    }    
}
