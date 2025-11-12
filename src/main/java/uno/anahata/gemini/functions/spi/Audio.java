package uno.anahata.gemini.functions.spi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import lombok.extern.slf4j.Slf4j;

/**
 * A utility class for recording audio from the system's microphone.
 * This class also manages a global lock to prevent concurrent audio operations.
 */
@Slf4j
public class Audio {

    // A global lock to ensure only one audio operation (recording or playback) happens at a time.
    private static volatile boolean audioLock = false;

    // State fields to manage the recording process
    private static TargetDataLine line;
    private static ByteArrayOutputStream out;
    private static Thread recordingThread;

    // Define the audio format: 16kHz, 16-bit, mono, signed, little-endian
    private static final AudioFormat format = new AudioFormat(16000, 16, 1, true, false);

    /**
     * Tries to acquire the global audio lock.
     *
     * @return {@code true} if the lock was acquired, {@code false} otherwise.
     */
    public static synchronized boolean acquireAudioLock() {
        if (audioLock) {
            return false; // Lock is already held
        }
        audioLock = true;
        return true;
    }

    /**
     * Releases the global audio lock.
     */
    public static void releaseAudioLock() {
        audioLock = false;
    }

    /**
     * Starts recording audio from the default microphone in a background thread.
     * If a recording is already in progress or the audio lock is held, this method does nothing.
     *
     * @throws LineUnavailableException if the microphone line is not available.
     */
    public static void startRecording() throws LineUnavailableException {
        if (!acquireAudioLock()) {
            log.warn("Could not acquire audio lock. Another audio operation is in progress.");
            throw new IllegalStateException("Could not acquire audio lock. Another audio operation is in progress.");
        }

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                throw new IllegalStateException("Audio line not supported. Your microphone might not be configured correctly or is in use.");
            }

            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            log.info("Microphone line opened, recording started.");

            out = new ByteArrayOutputStream();
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    while (line.isOpen()) {
                        int bytesRead = line.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Recording thread finished.");
                }
            });
            recordingThread.start();
        } catch (LineUnavailableException | IllegalStateException e) {
            releaseAudioLock(); // Ensure lock is released on failure
            throw e;
        }
    }

    /**
     * Stops the ongoing audio recording, saves the captured audio to a temporary WAV file,
     * and returns the file.
     *
     * @return A {@link File} object pointing to the temporary WAV file, or {@code null} if no recording was in progress.
     * @throws IOException if there is an error writing the audio file.
     * @throws InterruptedException if the recording thread is interrupted unexpectedly.
     */
    public static File stopRecording() throws IOException, InterruptedException {
        if (line == null || !line.isOpen()) {
            log.warn("No recording in progress. Ignoring stop request.");
            // Even if there's no line, the lock might have been acquired and failed before the line was created.
            // It's safe to release it here to prevent a deadlock.
            releaseAudioLock();
            return null;
        }

        try {
            line.stop();
            line.close();
            recordingThread.interrupt(); // Signal the thread to stop
            recordingThread.join(); // Wait for the thread to finish
            log.info("Recording stopped.");

            File tempWavFile = File.createTempFile("recording-", ".wav");
            tempWavFile.deleteOnExit();

            byte[] audioBytes = out.toByteArray();
            if (audioBytes.length == 0) {
                log.warn("No audio was captured. Returning null.");
                return null;
            }

            AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(audioBytes), format, audioBytes.length / format.getFrameSize());
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempWavFile);

            log.info("Successfully recorded audio to {}", tempWavFile.getAbsolutePath());
            return tempWavFile;
        } finally {
            // Reset state and always release the lock
            line = null;
            recordingThread = null;
            releaseAudioLock();
        }
    }
}
