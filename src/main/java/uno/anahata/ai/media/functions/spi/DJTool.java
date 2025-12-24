package uno.anahata.ai.media.functions.spi;

import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;
import uno.anahata.ai.tools.spi.RunningJVM;

import javax.sound.midi.*;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

/**
 * A tool that manages a persistent, background MIDI sequencer to allow for continuous music playback
 * that can be modified across multiple user turns, simulating a live DJ session.
 * Features a "super cool" Swing-based dashboard for real-time visual feedback.
 */
@Slf4j
public class DJTool {

    private static final String ENGINE_KEY = "uno.anahata.ai.media.functions.spi.DJEngine";

    private static DJEngine getEngine() {
        DJEngine engine = (DJEngine) RunningJVM.chatTemp.computeIfAbsent(ENGINE_KEY, k -> {
            log.info("Creating and storing a new DJEngine instance.");
            return new DJEngine();
        });
        engine.ensureThreadIsAliveAndReady();
        return engine;
    }

    @AIToolMethod("Starts the DJ engine with a foundational beat.")
    public static String start(@AIToolParam("The style of music to play, e.g., 'psytrance', 'techno'") String style) {
        getEngine().startMusic(style);
        return "DJ Engine started with a " + style + " beat. Check the dashboard for visual feedback!";
    }

    @AIToolMethod("Stops the DJ engine and all music.")
    public static String stop() {
        Object engineObj = RunningJVM.chatTemp.remove(ENGINE_KEY);
        if (engineObj instanceof AutoCloseable) {
            try {
                ((AutoCloseable) engineObj).close();
                log.info("DJ Engine stopped and removed from shared context.");
                return "DJ Engine stopped and dashboard closed.";
            } catch (Exception e) {
                log.error("Error while closing DJ Engine", e);
                return "Error stopping DJ Engine: " + e.getMessage();
            }
        }
        return "DJ Engine was not running.";
    }

    @AIToolMethod("Mutes or unmutes a specific track.")
    public static String setTrackMute(
            @AIToolParam("The name of the track (e.g., 'drums', 'bass', 'lead')") String trackName,
            @AIToolParam("True to mute, false to unmute") boolean mute) {
        getEngine().submitCommand(() -> getEngine().setTrackMute(trackName, mute));
        return "Mute for track '" + trackName + "' set to " + mute;
    }

    @AIToolMethod("Sets the tempo of the music.")
    public static String setTempo(@AIToolParam("The new tempo in beats per minute (BPM)") int bpm) {
        getEngine().submitCommand(() -> getEngine().setTempo(bpm));
        return "Tempo set to " + bpm + " BPM.";
    }

    @AIToolMethod("Records a snippet of the current audio output to a WAV file.")
    public static String recordSnippet(
            @AIToolParam("The duration of the recording in seconds.") int durationSeconds) throws Exception {
        return getEngine().submitRecordSnippet(durationSeconds);
    }

    private static class DJEngine implements Runnable, AutoCloseable {
        private Sequencer sequencer;
        private Synthesizer synthesizer;
        private final BlockingQueue<Runnable> commandQueue = new LinkedBlockingQueue<>();
        private volatile boolean running = false;
        private volatile Thread engineThread;
        private final Map<String, Integer> trackNameToIndex = new HashMap<>();
        private volatile String lastStyle;
        private CountDownLatch readyLatch;
        
        // UI Components
        private JFrame frame;
        private DJDashboard dashboard;

        public synchronized void ensureThreadIsAliveAndReady() {
            if (engineThread == null || !engineThread.isAlive()) {
                log.info("DJ Engine thread is not alive. Starting a new one.");
                start();
            }
            try {
                if (readyLatch != null && !readyLatch.await(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("DJ Engine thread failed to initialize in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for DJ Engine to initialize.", e);
            }
        }

        public void startMusic(String style) {
            this.lastStyle = style;
            submitCommand(() -> {
                if ("psytrance".equalsIgnoreCase(style)) {
                    playPsytrance();
                } else if ("techno".equalsIgnoreCase(style)) {
                    playTechno();
                }
                updateUI();
            });
        }

        public void submitCommand(Runnable command) {
            commandQueue.add(command);
        }

        private synchronized void start() {
            if (engineThread != null && engineThread.isAlive()) return;
            readyLatch = new CountDownLatch(1);
            running = true;
            engineThread = new Thread(this);
            engineThread.setName("DJ-Engine-Thread-" + engineThread.getId());
            engineThread.setDaemon(true);
            engineThread.start();
            
            SwingUtilities.invokeLater(this::initUI);
            
            log.info("DJ Engine thread started.");
            if (lastStyle != null) {
                startMusic(lastStyle);
            }
        }

        private void initUI() {
            frame = new JFrame("Anahata DJ Dashboard");
            dashboard = new DJDashboard();
            frame.setContentPane(dashboard);
            frame.setSize(500, 400);
            frame.setLocationRelativeTo(null);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            frame.setVisible(true);
        }

        private void updateUI() {
            if (dashboard != null) {
                SwingUtilities.invokeLater(() -> {
                    dashboard.setStyle(lastStyle);
                    dashboard.setTempo((int) sequencer.getTempoInBPM());
                    dashboard.setTrackStatus("drums", !sequencer.getTrackMute(trackNameToIndex.getOrDefault("drums", 0)));
                    dashboard.setTrackStatus("bass", !sequencer.getTrackMute(trackNameToIndex.getOrDefault("bass", 1)));
                    dashboard.setTrackStatus("lead", !sequencer.getTrackMute(trackNameToIndex.getOrDefault("lead", 2)));
                });
            }
        }

        @Override
        public void close() {
            running = false;
            if (engineThread != null) {
                engineThread.interrupt();
            }
            SwingUtilities.invokeLater(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        @Override
        public void run() {
            try {
                synthesizer = MidiSystem.getSynthesizer();
                synthesizer.open();
                sequencer = MidiSystem.getSequencer(false);
                sequencer.open();
                sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
                
                // Add a meta event listener for visualizer feedback
                sequencer.addMetaEventListener(meta -> {
                    if (dashboard != null) dashboard.pulse();
                });

                log.info("DJ Engine thread initialized successfully.");
                readyLatch.countDown();

                while (running) {
                    try {
                        Runnable command = commandQueue.poll(1, TimeUnit.SECONDS);
                        if (command != null) {
                            command.run();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                }
            } catch (Exception e) {
                log.error("Fatal error in DJ Engine thread", e);
                readyLatch.countDown();
            } finally {
                log.info("DJ Engine thread shutting down.");
                if (sequencer != null && sequencer.isOpen()) sequencer.close();
                if (synthesizer != null && synthesizer.isOpen()) synthesizer.close();
            }
        }

        public void playPsytrance() {
            try {
                if (sequencer.isRunning()) sequencer.stop();
                trackNameToIndex.clear();
                Sequence sequence = new Sequence(Sequence.PPQ, 24);
                trackNameToIndex.put("drums", 0);
                trackNameToIndex.put("bass", 1);
                trackNameToIndex.put("lead", 2);
                Track drums = sequence.createTrack();
                Track bass = sequence.createTrack();
                Track lead = sequence.createTrack();
                setupInstrument(bass, 1, 38);
                setupInstrument(lead, 2, 81);
                for (int m = 0; m < 4; m++) {
                    long tick = m * 4 * 24;
                    for (int i = 0; i < 4; i++) addNote(drums, 9, tick + i * 24, 12, 36, 120);
                    for (int i = 0; i < 8; i++) addNote(drums, 9, tick + i * 12, 12, 42, 90);
                    for (int i = 0; i < 8; i++) {
                        addNote(bass, 1, tick + i * 12, 6, 36, 110);
                        addNote(bass, 1, tick + i * 12 + 6, 6, 36 + 3, 100);
                    }
                    for (int i = 0; i < 16; i++) addNote(lead, 2, tick + i * 6, 6, 36 + 24 + (i % 4) * 3, 90);
                }
                sequencer.setSequence(sequence);
                sequencer.setTempoInBPM(140);
                sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                sequencer.start();
            } catch (Exception e) {
                log.error("Failed to play psytrance", e);
            }
        }

        public void playTechno() {
            try {
                if (sequencer.isRunning()) sequencer.stop();
                trackNameToIndex.clear();
                Sequence sequence = new Sequence(Sequence.PPQ, 24);
                trackNameToIndex.put("drums", 0);
                trackNameToIndex.put("bass", 1);
                trackNameToIndex.put("lead", 2);
                Track drums = sequence.createTrack();
                Track bass = sequence.createTrack();
                Track lead = sequence.createTrack();
                setupInstrument(bass, 1, 39);
                setupInstrument(lead, 2, 62);
                for (int m = 0; m < 4; m++) {
                    long tick = m * 4 * 24;
                    for (int i = 0; i < 4; i++) addNote(drums, 9, tick + i * 24, 12, 36, 127); // Kick
                    for (int i = 0; i < 4; i++) addNote(drums, 9, tick + i * 24 + 12, 12, 42, 100); // Hat
                    for (int i = 0; i < 16; i++) addNote(bass, 1, tick + i * 6, 4, 36 - 12, 110);
                    for (int i = 0; i < 8; i++) addNote(lead, 2, tick + i * 12 + 6, 12, 48 + (i % 2) * 7, 80);
                }
                sequencer.setSequence(sequence);
                sequencer.setTempoInBPM(128);
                sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                sequencer.start();
            } catch (Exception e) {
                log.error("Failed to play techno", e);
            }
        }

        public void setTrackMute(String trackName, boolean mute) {
            Integer trackIndex = trackNameToIndex.get(trackName.toLowerCase());
            if (trackIndex != null) {
                sequencer.setTrackMute(trackIndex, mute);
                updateUI();
            }
        }

        public void setTempo(int bpm) {
            sequencer.setTempoInBPM(bpm);
            updateUI();
        }

        public String submitRecordSnippet(int durationSeconds) throws Exception {
            CompletableFuture<String> future = new CompletableFuture<>();
            submitCommand(() -> {
                try {
                    future.complete(recordSnippetInternal(durationSeconds));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future.get(durationSeconds + 10, TimeUnit.SECONDS);
        }

        private String recordSnippetInternal(int durationSeconds) throws Exception {
            if (sequencer == null || sequencer.getSequence() == null) {
                throw new IllegalStateException("Sequencer is not ready.");
            }
            Sequence currentSequence = sequencer.getSequence();
            float currentTempo = sequencer.getTempoInBPM();
            long currentTick = sequencer.getTickPosition();

            try (Synthesizer offlineSynth = MidiSystem.getSynthesizer();
                 Sequencer offlineSequencer = MidiSystem.getSequencer(false)) {

                if (!(offlineSynth instanceof com.sun.media.sound.AudioSynthesizer)) {
                    throw new UnsupportedOperationException("Offline rendering requires Gervill.");
                }
                com.sun.media.sound.AudioSynthesizer audioSynth = (com.sun.media.sound.AudioSynthesizer) offlineSynth;
                offlineSynth.open();
                offlineSequencer.open();
                offlineSequencer.getTransmitter().setReceiver(offlineSynth.getReceiver());

                offlineSequencer.setSequence(currentSequence);
                offlineSequencer.setTempoInBPM(currentTempo);
                offlineSequencer.setTickPosition(currentTick);

                AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
                File outputFile = File.createTempFile("dj_snippet_", ".wav");

                try (AudioInputStream stream = audioSynth.openStream(format, null)) {
                    offlineSequencer.start();
                    long frameSize = format.getFrameSize();
                    long bytesToRead = (long) (durationSeconds * format.getFrameRate() * frameSize);
                    byte[] audioData = new byte[(int) bytesToRead];
                    int totalBytesRead = 0, bytesRead;
                    while (totalBytesRead < bytesToRead && (bytesRead = stream.read(audioData, totalBytesRead, audioData.length - totalBytesRead)) != -1) {
                        totalBytesRead += bytesRead;
                    }
                    offlineSequencer.stop();
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData, 0, totalBytesRead);
                         AudioInputStream recordingStream = new AudioInputStream(bais, format, totalBytesRead / frameSize)) {
                        AudioSystem.write(recordingStream, AudioFileFormat.Type.WAVE, outputFile);
                    }
                    return outputFile.getAbsolutePath();
                }
            }
        }

        private void setupInstrument(Track track, int channel, int instrument) throws InvalidMidiDataException {
            track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, instrument, 0), 0));
        }

        private void addNote(Track track, int channel, long tick, long duration, int key, int velocity) throws InvalidMidiDataException {
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, channel, key, velocity), tick));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, channel, key, 0), tick + duration));
        }
    }

    private static class DJDashboard extends JPanel {
        private String style = "None";
        private int tempo = 0;
        private final Map<String, Boolean> trackStatus = new HashMap<>();
        private final int[] visualizerBars = new int[10];
        private final Random random = new Random();
        private final Timer visualizerTimer;

        public DJDashboard() {
            setBackground(new Color(20, 20, 25));
            setLayout(new BorderLayout());
            
            visualizerTimer = new Timer(50, e -> {
                for (int i = 0; i < visualizerBars.length; i++) {
                    visualizerBars[i] = Math.max(0, visualizerBars[i] - 5);
                }
                repaint();
            });
            visualizerTimer.start();
        }

        public void setStyle(String style) { this.style = style; repaint(); }
        public void setTempo(int tempo) { this.tempo = tempo; repaint(); }
        public void setTrackStatus(String track, boolean active) { trackStatus.put(track, active); repaint(); }
        
        public void pulse() {
            for (int i = 0; i < visualizerBars.length; i++) {
                visualizerBars[i] = 50 + random.nextInt(50);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw Visualizer
            int barWidth = getWidth() / visualizerBars.length;
            for (int i = 0; i < visualizerBars.length; i++) {
                g2.setColor(new Color(0, 150 + visualizerBars[i], 255));
                int h = (int) (getHeight() * (visualizerBars[i] / 100.0));
                g2.fillRect(i * barWidth, getHeight() - h, barWidth - 2, h);
            }

            // Draw Info Overlay
            g2.setColor(new Color(255, 255, 255, 200));
            g2.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2.drawString("STYLE: " + style.toUpperCase(), 20, 40);
            g2.drawString("TEMPO: " + tempo + " BPM", 20, 70);

            // Draw Track Status
            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            int y = 110;
            for (String track : new String[]{"drums", "bass", "lead"}) {
                boolean active = trackStatus.getOrDefault(track, true);
                g2.setColor(active ? Color.GREEN : Color.RED);
                g2.fillOval(20, y - 12, 10, 10);
                g2.setColor(Color.WHITE);
                g2.drawString(track.toUpperCase() + (active ? " [ON]" : " [MUTE]"), 40, y);
                y += 25;
            }
            
            // Draw "Anahata DJ" Logo
            g2.setColor(new Color(255, 255, 255, 100));
            g2.setFont(new Font("SansSerif", Font.ITALIC, 12));
            g2.drawString("Anahata AI DJ Engine v2.0", getWidth() - 160, 25);
        }
    }
}
