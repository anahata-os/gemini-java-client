package uno.anahata.gemini.media.functions.spi;

import java.io.File;
import javax.sound.sampled.LineUnavailableException;
import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.media.util.Microphone;

/**
 * AI-callable tools for audio recording.
 */
public final class AudioTool {

    @AIToolMethod("Starts recording audio from the default microphone.")
    public static void startRecording() throws LineUnavailableException {
        Microphone.startRecording();
    }

    @AIToolMethod("Stops the current audio recording and returns the audio file.")
    public static File stopRecording() throws Exception {
        return Microphone.stopRecording();
    }
}
