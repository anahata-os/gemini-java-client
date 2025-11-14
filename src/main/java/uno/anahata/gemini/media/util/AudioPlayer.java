package uno.anahata.gemini.media.util;

import java.util.concurrent.ExecutorService;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.AnahataExecutors;

/**
 * A simple UI utility to play short, non-blocking notification sounds from resources.
 * All playback is handled on a dedicated 'audio-player' thread pool.
 */
@Slf4j
public final class AudioPlayer {

    private static final ExecutorService audioExecutor = AnahataExecutors.newCachedThreadPoolExecutor("audio-player");

    private AudioPlayer() {}

    public static void playSound(final String resourceName) {
        if (Microphone.isRecording()) {
            return;
        }
        audioExecutor.submit(() -> {
            try (AudioInputStream inputStream = AudioSystem.getAudioInputStream(
                AudioPlayer.class.getResourceAsStream("/sounds/" + resourceName))) {
                Clip clip = AudioSystem.getClip();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.open(inputStream);
                clip.start();
            } catch (Exception e) {
                log.warn("Could not play sound resource: {}", resourceName, e);
            }
        });
    }
}
