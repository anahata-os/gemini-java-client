package uno.anahata.gemini.ui.render;

import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingChunkWeb;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.GroundingSupport;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.ui.IconUtils;
import uno.anahata.gemini.ui.SwingChatConfig.UITheme;
import uno.anahata.gemini.ui.WrapLayout;

@Slf4j
public class GroundingMetadataRenderer extends JPanel implements Scrollable {
    private static final int V_GAP = 10;
    private final UITheme theme;

    public GroundingMetadataRenderer(GroundingMetadata metadata, UITheme theme) {
        log.debug("Rendering GroundingMetadata in AI Studio style (Pure Swing): {}", metadata.toString());
        this.theme = theme;
        setLayout(new BorderLayout());
        setOpaque(false);
        
        // Outer Border for the entire component
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.getModelBorder(), 1), // Use Model Border for consistency
            new EmptyBorder(0, 0, 0, 0) // Inner padding handled by sub-panels
        )); 

        // 1. Main Header (NORTH)
        add(renderHeader(), BorderLayout.NORTH);

        // 2. Content Panel (CENTER)
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(theme.getGroundingContentBg());
        contentPanel.setBorder(new EmptyBorder(V_GAP, V_GAP, 0, V_GAP)); // Padding around the sections

        // Add sections
        metadata.groundingSupports().ifPresent(supports -> {
            if (!supports.isEmpty()) {
                contentPanel.add(renderGroundingSupports(supports));
                contentPanel.add(Box.createVerticalStrut(V_GAP));
            }
        });

        metadata.groundingChunks().ifPresent(chunks -> {
            if (!chunks.isEmpty()) {
                contentPanel.add(renderSources(chunks));
                contentPanel.add(Box.createVerticalStrut(V_GAP));
            }
        });

        metadata.webSearchQueries().ifPresent(queries -> {
            if (!queries.isEmpty()) {
                contentPanel.add(renderSearchSuggestions(queries));
                // contentPanel.add(Box.createVerticalStrut(V_GAP)); // Removed final strut
            }
        });
        
        // Add vertical glue to push content to the top
        contentPanel.add(Box.createVerticalGlue());
        
        add(contentPanel, BorderLayout.CENTER);
        
        // FIX: Remove bottom padding to prevent double-spacing with parent ContentRenderer
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.getModelBorder(), 1),
            new EmptyBorder(0, 0, 0, 0)
        ));
    }
    
    private JPanel renderHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(theme.getGroundingHeaderBg());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        
        JLabel titleLabel = new JLabel("Grounding Metadata");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(theme.getFontColor());
        
        try {
            // Use the Anahata icon from resources
            //ImageIcon originalIcon = );            
            titleLabel.setIcon(IconUtils.getIcon("anahata.png"));
            titleLabel.setIconTextGap(6);
        } catch (Exception e) {
            log.warn("Could not load icon for header.", e);
        }
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        return headerPanel;
    }

    private JPanel createDetailSection(String title) {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Inner Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(theme.getGroundingDetailsHeaderBg());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(theme.getGroundingDetailsHeaderColor());
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Content Panel
        JPanel contentPanel = new JPanel(new BorderLayout()); // Use BorderLayout for content wrapper
        contentPanel.setBackground(theme.getGroundingDetailsContentBg());
        contentPanel.setBorder(new EmptyBorder(8, 10, 8, 10));
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Inner Content Holder (uses BoxLayout for vertical stacking of labels/components)
        JPanel innerContentHolder = new JPanel();
        innerContentHolder.setLayout(new BoxLayout(innerContentHolder, BoxLayout.Y_AXIS));
        innerContentHolder.setOpaque(false);
        
        contentPanel.add(innerContentHolder, BorderLayout.NORTH); // Add to NORTH to prevent vertical stretching
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        // Add a border around the whole detail section
        mainPanel.setBorder(BorderFactory.createLineBorder(theme.getModelBorder(), 1));
        
        return mainPanel;
    }

    private JPanel renderGroundingSupports(List<GroundingSupport> supports) {
        JPanel mainPanel = createDetailSection("Supporting Text");
        JPanel contentPanel = (JPanel) mainPanel.getComponent(1); // Get the content panel (BorderLayout)
        JPanel innerContentHolder = (JPanel) contentPanel.getComponent(0); // Get the inner content holder (BoxLayout)
        
        supports.stream()
            .filter(support -> support.segment().isPresent() && support.segment().get().text().isPresent())
            .forEach(support -> {
                String text = support.segment().get().text().get();
                JLabel textLabel = new JLabel("<html>" + text + "</html>");
                textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                innerContentHolder.add(textLabel);
            });

        innerContentHolder.add(Box.createVerticalGlue());
        return mainPanel;
    }

    private JPanel renderSources(List<GroundingChunk> chunks) {
        JPanel mainPanel = createDetailSection("Sources");
        JPanel contentPanel = (JPanel) mainPanel.getComponent(1); // Get the content panel (BorderLayout)
        JPanel innerContentHolder = (JPanel) contentPanel.getComponent(0); // Get the inner content holder (BoxLayout)

        AtomicInteger count = new AtomicInteger(1);
        chunks.stream()
            .filter(chunk -> chunk.web().isPresent())
            .map(chunk -> createCitationLabel(chunk.web().get(), count.getAndIncrement()))
            .forEach(label -> {
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                innerContentHolder.add(label);
            });
        
        innerContentHolder.add(Box.createVerticalGlue());
        return mainPanel;
    }

    private JLabel createCitationLabel(GroundingChunkWeb webChunk, int index) {
        String uri = webChunk.uri().orElse(null);
        String title = webChunk.title().orElse(uri);
        String labelText = String.format("<html>%d. <a href='%s'>%s</a></html>", index, uri, title);
        return createClickableLabel(labelText, uri);
    }

    private JPanel renderSearchSuggestions(List<String> queries) {
        JPanel mainPanel = createDetailSection("Search Suggestions");
        JPanel contentPanel = (JPanel) mainPanel.getComponent(1);
        JPanel innerContentHolder = (JPanel) contentPanel.getComponent(0);

        // Use WrapLayout for correct wrapping of chips
        JPanel chipsContainer = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        chipsContainer.setOpaque(false);
        chipsContainer.setBackground(theme.getGroundingDetailsContentBg());
        chipsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (String query : queries) {
            String searchUri = "https://www.google.com/search?q=" + query.replace(" ", "+");
            chipsContainer.add(new ChipLabel(query, searchUri, theme));
        }
        
        innerContentHolder.add(chipsContainer);
        innerContentHolder.add(Box.createVerticalGlue());
        return mainPanel;
    }

    private JLabel createClickableLabel(String text, String uri) {
        JLabel label = new JLabel(text);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (uri != null) {
                    try {
                        Desktop.getDesktop().browse(new URI(uri));
                    } catch (IOException | URISyntaxException ex) {
                        log.error("Failed to open URI: {}", uri, ex);
                    }
                }
            }
        });
        return label;
    }

    // Scrollable interface implementation for correct wrapping in JScrollPane
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

    private static class ChipLabel extends JLabel {
        private static final int ARC = 16;
        private static final int PADDING_X = 12;
        private static final int PADDING_Y = 4;
        private final UITheme theme;

        public ChipLabel(String text, String uri, UITheme theme) {
            super(text);
            this.theme = theme;
            setFont(getFont().deriveFont(12f));
            setForeground(theme.getChipText());
            setBackground(theme.getChipBackground());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(PADDING_Y, PADDING_X, PADDING_Y, PADDING_X));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (uri != null) {
                        try {
                            Desktop.getDesktop().browse(new URI(uri));
                        } catch (IOException | URISyntaxException ex) {
                            log.error("Failed to open URI: {}", uri, ex);
                        }
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.setColor(theme.getChipBorder());
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            Insets insets = getInsets();
            size.width += insets.left + insets.right;
            size.height += insets.top + insets.bottom;
            return size;
        }
    }
}
