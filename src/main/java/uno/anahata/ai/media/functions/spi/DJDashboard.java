/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.functions.spi;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * An interactive dashboard for the DJ Engine, providing real-time visual feedback
 * and manual controls for the user.
 */
public class DJDashboard extends JPanel {
    private final int[] visualizerBars = new int[15];
    private final Random random = new Random();
    private final Timer visualizerTimer;
    
    private final JLabel styleLabel;
    private final JLabel tempoLabel;
    private final JSlider tempoSlider;
    private final Map<String, JButton> muteButtons = new HashMap<>();
    private final Map<String, JComboBox<InstrumentItem>> instrumentCombos = new HashMap<>();
    private final TimelinePanel timelinePanel;
    private final DJEngine engine;

    private static class InstrumentItem {
        int id;
        String name;
        InstrumentItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return String.format("%03d: %s", id, name); }
    }

    private static final InstrumentItem[] COMMON_INSTRUMENTS = {
        new InstrumentItem(38, "Slap Bass"), new InstrumentItem(39, "Synth Bass 2"),
        new InstrumentItem(34, "Electric Bass (pick)"), new InstrumentItem(35, "Fretless Bass"),
        new InstrumentItem(80, "Square Lead"), new InstrumentItem(81, "Saw Lead"),
        new InstrumentItem(87, "Bass + Lead"), new InstrumentItem(90, "Polysynth"),
        new InstrumentItem(62, "Synth Brass 2"), new InstrumentItem(50, "Synth Strings 1"),
        new InstrumentItem(30, "Distortion Guitar"), new InstrumentItem(102, "Echo Drops")
    };

    public DJDashboard(DJEngine engine) {
        this.engine = engine;
        setBackground(new Color(15, 15, 20));
        setLayout(new BorderLayout());
        
        // --- Visualizer Panel (Top) ---
        JPanel visPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int barWidth = getWidth() / visualizerBars.length;
                for (int i = 0; i < visualizerBars.length; i++) {
                    g2.setColor(new Color(0, 150 + visualizerBars[i], 255, 200));
                    int h = (int) (getHeight() * (visualizerBars[i] / 100.0));
                    g2.fillRect(i * barWidth, getHeight() - h, barWidth - 2, h);
                }
            }
        };
        visPanel.setPreferredSize(new Dimension(500, 100));
        visPanel.setBackground(Color.BLACK);
        visPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 255, 150)));
        add(visPanel, BorderLayout.NORTH);

        // --- Info & Controls Panel (Center) ---
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        styleLabel = createNeonLabel("STYLE: NONE", 24, new Color(50, 255, 50));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        centerPanel.add(styleLabel, gbc);

        tempoLabel = createNeonLabel("TEMPO: 0 BPM", 18, new Color(0, 180, 255));
        gbc.gridy = 1;
        centerPanel.add(tempoLabel, gbc);

        tempoSlider = new JSlider(60, 200, 120);
        tempoSlider.setOpaque(false);
        tempoSlider.addChangeListener(e -> {
            if (!tempoSlider.getValueIsAdjusting()) {
                engine.submitCommand(() -> engine.setTempo(tempoSlider.getValue()));
            }
        });
        gbc.gridy = 2;
        centerPanel.add(tempoSlider, gbc);

        // Timeline
        timelinePanel = new TimelinePanel();
        gbc.gridy = 3;
        centerPanel.add(timelinePanel, gbc);

        // Track Controls (Mute + Instrument)
        JPanel trackPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        trackPanel.setOpaque(false);
        for (String track : new String[]{"bass", "lead", "drums"}) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.setOpaque(false);
            
            JButton muteBtn = createNeonBtn(track.toUpperCase() + " [ON]");
            muteBtn.addActionListener(e -> {
                boolean currentlyMuted = engine.isTrackMuted(track);
                engine.submitCommand(() -> engine.setTrackMute(track, !currentlyMuted));
            });
            muteButtons.put(track, muteBtn);
            row.add(muteBtn);
            
            if (!"drums".equals(track)) {
                JLabel instLabel = new JLabel("INST:");
                instLabel.setForeground(Color.LIGHT_GRAY);
                row.add(instLabel);
                
                JComboBox<InstrumentItem> combo = new JComboBox<>(COMMON_INSTRUMENTS);
                combo.setPreferredSize(new Dimension(250, 25));
                combo.addActionListener(e -> {
                    InstrumentItem item = (InstrumentItem) combo.getSelectedItem();
                    if (item != null) engine.setTrackInstrument(track, item.id);
                });
                instrumentCombos.put(track, combo);
                row.add(combo);
            }
            
            trackPanel.add(row);
        }
        gbc.gridy = 4;
        centerPanel.add(trackPanel, gbc);

        // Style & Action Buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        actionPanel.setOpaque(false);
        JButton psyBtn = createNeonBtn("PSYTRANCE");
        psyBtn.addActionListener(e -> engine.startMusic("psytrance"));
        JButton techBtn = createNeonBtn("TECHNO");
        techBtn.addActionListener(e -> engine.startMusic("techno"));
        JButton stopBtn = createNeonBtn("STOP");
        stopBtn.setForeground(Color.RED);
        stopBtn.addActionListener(e -> engine.submitCommand(() -> engine.stopMusic()));
        
        actionPanel.add(psyBtn);
        actionPanel.add(techBtn);
        actionPanel.add(stopBtn);
        gbc.gridy = 5;
        centerPanel.add(actionPanel, gbc);

        add(centerPanel, BorderLayout.CENTER);

        engine.setPulseListener(v -> pulse());

        visualizerTimer = new Timer(50, e -> {
            for (int i = 0; i < visualizerBars.length; i++) {
                visualizerBars[i] = Math.max(0, visualizerBars[i] - 5);
            }
            refresh();
            visPanel.repaint();
        });
        visualizerTimer.start();
    }

    private void refresh() {
        styleLabel.setText("STYLE: " + engine.getLastStyle().toUpperCase());
        int tempo = engine.getTempo();
        tempoLabel.setText("TEMPO: " + tempo + " BPM");
        if (!tempoSlider.getValueIsAdjusting()) {
            tempoSlider.setValue(tempo);
        }
        
        for (Map.Entry<String, JButton> entry : muteButtons.entrySet()) {
            String track = entry.getKey();
            boolean active = !engine.isTrackMuted(track);
            JButton btn = entry.getValue();
            
            // Always update text and color to reflect current state
            btn.setText(track.toUpperCase() + (active ? " [ON]" : " [MUTE]"));
            btn.setForeground(active ? Color.GREEN : Color.RED);
            btn.setBorder(BorderFactory.createLineBorder(active ? Color.GREEN : Color.RED, 1));
        }

        for (Map.Entry<String, JComboBox<InstrumentItem>> entry : instrumentCombos.entrySet()) {
            String track = entry.getKey();
            int instId = engine.getTrackInstrument(track);
            JComboBox<InstrumentItem> combo = entry.getValue();
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (combo.getItemAt(i).id == instId) {
                    if (combo.getSelectedIndex() != i) combo.setSelectedIndex(i);
                    break;
                }
            }
        }

        timelinePanel.update(engine.getTickPosition(), engine.getTickLength());
    }

    public void pulse() {
        for (int i = 0; i < visualizerBars.length; i++) {
            visualizerBars[i] = 50 + random.nextInt(50);
        }
    }

    private JLabel createNeonLabel(String text, int size, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Monospaced", Font.BOLD, size));
        l.setForeground(color);
        return l;
    }

    private JButton createNeonBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(new Color(0, 255, 150));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(0, 255, 150), 1));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private class TimelinePanel extends JPanel {
        private long currentTick = 0;
        private long totalTicks = 1;

        public TimelinePanel() {
            setPreferredSize(new Dimension(400, 30));
            setBackground(new Color(20, 20, 30));
            setBorder(BorderFactory.createLineBorder(new Color(50, 50, 70)));
        }

        public void update(long current, long total) {
            this.currentTick = current;
            this.totalTicks = total > 0 ? total : 1;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background track
            g2.setColor(new Color(40, 40, 60));
            g2.fillRoundRect(5, h / 2 - 2, w - 10, 4, 4, 4);

            // Progress
            double progress = (double) (currentTick % totalTicks) / totalTicks;
            int progressX = (int) (5 + (w - 10) * progress);

            g2.setColor(new Color(0, 255, 150));
            g2.fillRoundRect(5, h / 2 - 2, progressX - 5, 4, 4, 4);

            // Playhead
            g2.setColor(Color.WHITE);
            g2.fillOval(progressX - 5, h / 2 - 5, 10, 10);
            g2.setColor(new Color(0, 255, 150));
            g2.drawOval(progressX - 5, h / 2 - 5, 10, 10);
        }
    }
}
