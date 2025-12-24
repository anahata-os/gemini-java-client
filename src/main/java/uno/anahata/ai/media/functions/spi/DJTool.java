/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.functions.spi;

import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;
import uno.anahata.ai.tools.spi.RunningJVM;
import javax.swing.SwingUtilities;

/**
 * A tool that manages a persistent, background MIDI sequencer to allow for continuous music playback
 * that can be modified across multiple user turns, simulating a live DJ session.
 * Features a Swing-based dashboard for real-time visual feedback.
 */
@Slf4j
public class DJTool {

    private static final String ENGINE_KEY = "uno.anahata.ai.media.functions.spi.DJEngine";
    private static final String WINDOW_KEY = "uno.anahata.ai.media.functions.spi.DJWindow";

    private static DJEngine getEngine() {
        DJEngine engine = (DJEngine) RunningJVM.chatTemp.computeIfAbsent(ENGINE_KEY, k -> {
            log.info("Creating and storing a new DJEngine instance.");
            return new DJEngine();
        });
        engine.ensureThreadIsAliveAndReady();
        ensureWindowIsVisible(engine);
        return engine;
    }

    private static void ensureWindowIsVisible(DJEngine engine) {
        SwingUtilities.invokeLater(() -> {
            DJWindow window = (DJWindow) RunningJVM.chatTemp.get(WINDOW_KEY);
            if (window == null || !window.isDisplayable()) {
                window = new DJWindow(engine);
                RunningJVM.chatTemp.put(WINDOW_KEY, window);
            }
            window.setVisible(true);
            window.toFront();
        });
    }

    @AIToolMethod("Starts the DJ engine with a foundational beat.")
    public static String start(@AIToolParam("The style of music to play, e.g., 'psytrance', 'techno'") String style) {
        getEngine().startMusic(style);
        return "DJ Engine started with a " + style + " beat. Check the dashboard for visual feedback!";
    }

    @AIToolMethod("Stops the DJ engine and all music.")
    public static String stop() {
        DJWindow window = (DJWindow) RunningJVM.chatTemp.remove(WINDOW_KEY);
        if (window != null) {
            SwingUtilities.invokeLater(window::dispose);
        }
        
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

    @AIToolMethod("Sets the MIDI instrument for a specific track.")
    public static String setTrackInstrument(
            @AIToolParam("The name of the track (e.g., 'bass', 'lead')") String trackName,
            @AIToolParam("The MIDI instrument ID (0-127).") int instrumentId) {
        getEngine().setTrackInstrument(trackName, instrumentId);
        return "Instrument for track '" + trackName + "' set to ID " + instrumentId;
    }

    @AIToolMethod("Sets the tempo of the music.")
    public static String setTempo(@AIToolParam("The new tempo in beats per minute (BPM)") int bpm) {
        getEngine().submitCommand(() -> getEngine().setTempo(bpm));
        return "Tempo set to " + bpm + " BPM.";
    }
}