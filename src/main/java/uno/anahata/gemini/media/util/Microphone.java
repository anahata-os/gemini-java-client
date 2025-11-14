package uno.anahata.gemini.media.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import uno.anahata.gemini.AnahataExecutors;

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
            microphoneExecutor.submit(() -> {
                try {
                    AudioSystem.write(new AudioInputStream(targetDataLine), AudioFileFormat.Type.WAVE, byteArrayOutputStream);
                } catch (IOException e) {
                    log.error("Error during audio recording", e);
                }
            });
        }
    }

    public static File stopRecording() throws IOException {
        synchronized (audioLock) {
            if (!recording.get()) {
                throw new IllegalStateException("Recording is not in progress");
            }
            targetDataLine.stop();
            targetDataLine.close();
            recording.set(false);
            File tempFile = File.createTempFile("recording", ".wav");
            try (ByteArrayInputStream bais = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                 AudioInputStream ais = new AudioInputStream(bais, FORMAT, byteArrayOutputStream.size() / FORMAT.getFrameSize())) {
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
