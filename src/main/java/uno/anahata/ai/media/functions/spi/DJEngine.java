/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.functions.spi;

import lombok.extern.slf4j.Slf4j;
import javax.sound.midi.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * The core engine for the DJ Tool, managing MIDI sequencing and synthesis.
 * This class is UI-agnostic and runs in a background thread.
 */
@Slf4j
public class DJEngine implements Runnable, AutoCloseable {
    private Sequencer sequencer;
    private Synthesizer synthesizer;
    private final BlockingQueue<Runnable> commandQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    private volatile Thread engineThread;
    private final Map<String, Integer> trackNameToIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> trackInstruments = new ConcurrentHashMap<>();
    private volatile String lastStyle = "None";
    private CountDownLatch readyLatch;
    private Consumer<Void> pulseListener;

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

    public void setPulseListener(Consumer<Void> listener) {
        this.pulseListener = listener;
    }

    public void startMusic(String style) {
        this.lastStyle = style;
        submitCommand(() -> {
            if ("psytrance".equalsIgnoreCase(style)) {
                playPsytrance();
            } else if ("techno".equalsIgnoreCase(style)) {
                playTechno();
            }
        });
    }
    
    public void stopMusic() {
        this.lastStyle = "None";
        if (sequencer != null && sequencer.isRunning()) {
            sequencer.stop();
        }
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
        log.info("DJ Engine thread started.");
    }

    @Override
    public void close() {
        running = false;
        if (engineThread != null) {
            engineThread.interrupt();
        }
    }

    @Override
    public void run() {
        try {
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            sequencer = MidiSystem.getSequencer(false);
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
            
            sequencer.addMetaEventListener(meta -> {
                if (pulseListener != null) pulseListener.accept(null);
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
            
            int bassInst = trackInstruments.getOrDefault("bass", 38);
            int leadInst = trackInstruments.getOrDefault("lead", 81);
            
            setupInstrument(bass, 1, bassInst);
            setupInstrument(lead, 2, leadInst);
            
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
            
            int bassInst = trackInstruments.getOrDefault("bass", 39);
            int leadInst = trackInstruments.getOrDefault("lead", 62);
            
            setupInstrument(bass, 1, bassInst);
            setupInstrument(lead, 2, leadInst);
            
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
        if (trackIndex != null && sequencer != null) {
            sequencer.setTrackMute(trackIndex, mute);
        }
    }

    public boolean isTrackMuted(String trackName) {
        Integer trackIndex = trackNameToIndex.get(trackName.toLowerCase());
        return trackIndex != null && sequencer != null && sequencer.getTrackMute(trackIndex);
    }

    public void setTrackInstrument(String trackName, int instrumentId) {
        trackInstruments.put(trackName.toLowerCase(), instrumentId);
        submitCommand(() -> {
            int channel = getChannelForTrack(trackName);
            if (channel != -1 && synthesizer != null) {
                synthesizer.getChannels()[channel].programChange(instrumentId);
            }
        });
    }

    public int getTrackInstrument(String trackName) {
        return trackInstruments.getOrDefault(trackName.toLowerCase(), -1);
    }

    private int getChannelForTrack(String trackName) {
        switch (trackName.toLowerCase()) {
            case "bass": return 1;
            case "lead": return 2;
            case "drums": return 9;
            default: return -1;
        }
    }

    public void setTempo(int bpm) {
        if (sequencer != null) {
            sequencer.setTempoInBPM(bpm);
        }
    }

    public int getTempo() {
        return sequencer != null ? (int) sequencer.getTempoInBPM() : 0;
    }

    public String getLastStyle() {
        return lastStyle;
    }

    public long getTickPosition() {
        return sequencer != null ? sequencer.getTickPosition() : 0;
    }

    public long getTickLength() {
        return sequencer != null ? sequencer.getTickLength() : 0;
    }

    private void setupInstrument(Track track, int channel, int instrument) throws InvalidMidiDataException {
        track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, instrument, 0), 0));
    }

    private void addNote(Track track, int channel, long tick, long duration, int key, int velocity) throws InvalidMidiDataException {
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, channel, key, velocity), tick));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, channel, key, 0), tick + duration));
    }
}