package uno.anahata.gemini.ui.render;

import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingChunkWeb;
import com.google.genai.types.GroundingMetadata;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * A component to render grounding metadata and citations.
 * @author Anahata
 */
public class GroundingMetadataRenderer extends JPanel {

    public GroundingMetadataRenderer(GroundingMetadata metadata) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(new TitledBorder("Citations"));

        if (metadata.groundingChunks() == null || !metadata.groundingChunks().isPresent() || metadata.groundingChunks().get().isEmpty()) {
            add(new JLabel("No citation information provided."));
            return;
        }

        int count = 1;
        for (GroundingChunk chunk : metadata.groundingChunks().get()) {
            Optional<GroundingChunkWeb> webChunkOpt = chunk.web();
            if (webChunkOpt.isPresent()) {
                GroundingChunkWeb webChunk = webChunkOpt.get();
                String uri = webChunk.uri().orElse(null);
                String title = webChunk.title().orElse("Unknown Source");
                String labelText = String.format("<html><font color='gray'>[%d]</font> <a href='%s'>%s</a></html>", count++, uri, title);

                JLabel citationLabel = new JLabel(labelText);
                citationLabel.setBorder(new EmptyBorder(2, 4, 2, 4));

                if (uri != null && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    citationLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    citationLabel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            try {
                                Desktop.getDesktop().browse(new URI(uri));
                            } catch (IOException | URISyntaxException ex) {
                                System.err.println("Failed to open citation URI: " + uri);
                            }
                        }
                    });
                }
                add(citationLabel);
                add(Box.createVerticalStrut(4));
            }
        }
        
        if (count == 1) {
             add(new JLabel("No web citation information found."));
        }
    }
}
