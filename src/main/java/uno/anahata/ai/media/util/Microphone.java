/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.AnahataExecutors;

/**
 * A low-level service for controlling the microphone and playing back recorded audio.
 * This class is NOT an AI tool.
 */
@Slf4j
public final class Microphone {
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, true);
    private static TargetDataLine targetDataLine;
    private static final AtomicBoolean recording = new AtomicBoolean(false);
    private static final Object audioLock = new Object();
    private static ByteArrayOutputStream byteArrayOutputStream;

    @Getter
    private static final ExecutorService microphoneExecutor = AnahataExecutors.newCachedThreadPoolExecutor("microphone-recorder");

    private Microphone() {}

    public static boolean isRecording() {
        return recording.get();
    }

    public static void startRecording() throws LineUnavailableException {
        synchronized (audioLock) {
            if (recording.get()) {
                throw new IllegalStateException("Recording is already in progress");
            }
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(FORMAT);
            targetDataLine.start();
            recording.set(true);
            byteArrayOutputStream = new ByteArrayOutputStream();
            
            // BUGFIX: Read from the line into a buffer, then write the buffer to the output stream.
            // Do not write the live AudioInputStream directly, as its length is unknown.
            microphoneExecutor.submit(() -> {
                byte[] buffer = new byte[4096];
                int bytesRead;
                try {
                    while (recording.get()) {
                        bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            byteArrayOutputStream.write(buffer, 0, bytesRead);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error during audio recording buffer read", e);
                }
            });
        }
    }

    public static File stopRecording() throws IOException {
        synchronized (audioLock) {
            if (!recording.get()) {
                // This can happen if the user clicks stop very quickly after start.
                // It's not a critical error, just return null.
                log.warn("Stop recording called, but recording was not in progress.");
                return null;
            }
            // Signal the recording thread to stop
            recording.set(false); 
            
            // Stop and close the line to release hardware resources
            targetDataLine.stop();
            targetDataLine.close();
            
            File tempFile = File.createTempFile("recording", ".wav");
            
            // Now, create a new AudioInputStream from our complete, in-memory buffer (which has a known length)
            // and write that to the final file.
            byte[] audioData = byteArrayOutputStream.toByteArray();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream ais = new AudioInputStream(bais, FORMAT, audioData.length / FORMAT.getFrameSize())) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempFile);
            }
            return tempFile;
        }
    }

    public static void play(byte[] data) {
        microphoneExecutor.submit(() -> {
            synchronized (audioLock) {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(ais);
                    clip.start();
                    Thread.sleep(clip.getMicrosecondLength() / 1000);
                } catch (Exception e) {
                    log.error("Error playing audio", e);
                }
            }
        });
    }
}
