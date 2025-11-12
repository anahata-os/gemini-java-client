package uno.anahata.gemini.ui.actions;

import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.Executors;
import uno.anahata.gemini.functions.spi.Audio;

/**
 * A simple utility to play raw audio data.
 */
@Slf4j
public class AudioPlayer {

    /**
     * Plays the given audio data in a background thread.
     * Manages the global audio lock to prevent conflicts with recording.
     *
     * @param audioData The raw byte array of the audio to play.
     */
    public static void play(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            log.warn("Audio data is empty, cannot play.");
            return;
        }

        Executors.cachedThreadPool.submit(() -> {
            if (!Audio.acquireAudioLock()) {
                log.warn("Could not acquire audio lock. Another audio operation is in progress.");
                // Optionally, show a dialog to the user here.
                return;
            }

            try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioData))) {
                Clip clip = AudioSystem.getClip();
                
                // Listener to release the lock when playback finishes or stops.
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        Audio.releaseAudioLock();
                        log.info("Playback finished, audio lock released.");
                    }
                });

                clip.open(ais);
                clip.start();
                log.info("Starting audio playback.");

            } catch (Exception e) {
                log.error("Error playing audio", e);
                Audio.releaseAudioLock(); // Ensure lock is released on error
            }
        });
    }
}
