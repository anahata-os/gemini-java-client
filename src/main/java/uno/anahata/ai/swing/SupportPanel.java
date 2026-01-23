/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;

/**
 * A panel providing support links and community resources for users.
 */
@Slf4j
public class SupportPanel extends JPanel {

    public SupportPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(20, 20, 10, 20);
        
        // Title
        JLabel titleLabel = new JLabel("Support & Community");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        add(titleLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 20, 20, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        add(new JSeparator(), gbc);

        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(5, 20, 5, 20);

        // Discord
        gbc.gridy++;
        add(createLinkRow("Join our Discord", "https://discord.com/invite/M396BNtX", 
                "Connect with the community and get real-time help.", "discord.png"), gbc);

        // GitHub Issues
        gbc.gridy++;
        add(createLinkRow("Report an Issue", "https://github.com/anahata-os/gemini-java-client/issues", 
                "Found a bug? Let us know on GitHub.", "github.png"), gbc);

        // Email
        gbc.gridy++;
        add(createLinkRow("Email Support", "mailto:support@anahata.uno", 
                "Send us a direct message at support@anahata.uno", "email.png"), gbc);

        // Website
        gbc.gridy++;
        add(createLinkRow("Official Website", "https://anahata.uno/", 
                "Learn more about the Anahata ecosystem.", "anahata.png"), gbc);

        // Javadocs
        gbc.gridy++;
        add(createLinkRow("Browse Javadocs", "https://anahata-os.github.io/gemini-java-client/", 
                "Technical documentation and API reference.", "javadoc.png"), gbc);

        // Spacer to push everything to the top-left
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }

    private JPanel createLinkRow(String title, String url, String description, String iconName) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        
        JButton btn = new JButton(title, IconUtils.getIcon(iconName));
        btn.setPreferredSize(new Dimension(200, 40));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> openWebpage(url));
        
        JLabel descLabel = new JLabel("  - " + description);
        descLabel.setForeground(Color.GRAY);
        descLabel.setFont(descLabel.getFont().deriveFont(12f));

        row.add(btn);
        row.add(descLabel);
        
        return row;
    }

    private void openWebpage(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            log.error("Failed to open URL: " + url, e);
            JOptionPane.showMessageDialog(this, "Could not open link: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
