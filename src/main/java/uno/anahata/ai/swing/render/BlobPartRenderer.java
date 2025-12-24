/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render;

import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import uno.anahata.ai.media.util.Microphone;
import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;

public class BlobPartRenderer implements PartRenderer {

    private static final int THUMBNAIL_MAX_SIZE = 250;

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        Optional<Blob> blobOpt = part.inlineData();
        if (blobOpt.isEmpty()) {
            return new JLabel("Error: Blob data not found.");
        }

        Blob blob = blobOpt.get();
        String mimeType = blob.mimeType().orElse("application/octet-stream");
        byte[] data = blob.data().get();

        if (data == null || data.length == 0) {
            return new JLabel("Error: Blob data is empty.");
        }

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.setOpaque(false);

        // Handle Audio Blobs
        if (mimeType.startsWith("audio/")) {
            JButton playButton = new JButton("▶ Play Audio");
            playButton.addActionListener(e -> {
                playButton.setEnabled(false);
                playButton.setText("Playing...");
                Microphone.play(data);
                // A more robust solution would listen for playback to finish
                // and re-enable the button. For now, we just prevent re-clicks.
                // A listener on the audio clip could re-enable it via SwingUtilities.invokeLater.
            });
            panel.add(playButton, BorderLayout.CENTER);
        } 
        // Handle Image Blobs
        else if (mimeType.startsWith("image/")) {
            try {
                BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(data));
                if (originalImage != null) {
                    Image thumbnail = createThumbnail(originalImage);
                    JLabel imageLabel = new JLabel(new ImageIcon(thumbnail));
                    imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                    panel.add(imageLabel, BorderLayout.CENTER);
                }
            } catch (IOException e) {
                // Could not create image, will just show info
                System.err.println("Failed to create thumbnail for blob: " + e.getMessage());
            }
        }

        // Info Panel for mimeType and size (for all blob types)
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel mimeTypeLabel = new JLabel("MIME Type: " + mimeType);
        mimeTypeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sizeLabel = new JLabel("Size: " + formatSize(data.length));
        sizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(mimeTypeLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        infoPanel.add(sizeLabel);

        panel.add(infoPanel, BorderLayout.SOUTH);

        return panel;
    }

    private Image createThumbnail(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= THUMBNAIL_MAX_SIZE && height <= THUMBNAIL_MAX_SIZE) {
            return original;
        }

        double thumbRatio = (double) THUMBNAIL_MAX_SIZE / (double) Math.max(width, height);
        int newWidth = (int) (width * thumbRatio);
        int newHeight = (int) (height * thumbRatio);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, original.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : original.getType());
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        int exp = (int) (Math.log(size) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }
}