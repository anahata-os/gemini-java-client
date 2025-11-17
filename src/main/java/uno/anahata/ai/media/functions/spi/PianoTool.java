package uno.anahata.ai.media.functions.spi;

import java.util.concurrent.ExecutorService;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.AnahataExecutors;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;

/**
 * AI-callable tools for playing simple melodies using the Java MIDI synthesizer.
 */
@Slf4j
public class PianoTool {

    private static final int DEFAULT_VELOCITY = 64; // Standard MIDI velocity
    private static final ExecutorService pianoExecutor = AnahataExecutors.newCachedThreadPoolExecutor("piano-player");

    @AIToolMethod("Plays a simple melody from a string of notes asynchronously.")
    public static String playMelody(
            @AIToolParam("A space-separated string of notes (e.g., 'C4 D4 E4 C4'). " +
                         "Notes are in standard pitch notation. Use 'R' for a rest.") String notes,
            @AIToolParam("The duration of each note in milliseconds.") int noteDurationMs
    ) {
        pianoExecutor.submit(() -> {
            try (Synthesizer synthesizer = MidiSystem.getSynthesizer()) {
                synthesizer.open();
                MidiChannel channel = synthesizer.getChannels()[0];

                String[] noteArray = notes.split("\\s+");
                for (String note : noteArray) {
                    if (Thread.currentThread().isInterrupted()) break;
                    
                    if (note.equalsIgnoreCase("R")) {
                        Thread.sleep(noteDurationMs);
                    } else {
                        int midiNote = getMidiNote(note);
                        if (midiNote != -1) {
                            channel.noteOn(midiNote, DEFAULT_VELOCITY);
                            Thread.sleep(noteDurationMs);
                            channel.noteOff(midiNote);
                        }
                    }
                }
            } catch (Exception e) {
                 log.error("Error playing melody", e);
            }
        });
        return "Melody playback started.";
    }

    /**
     * Converts a note name (e.g., "C4", "G#5") to its MIDI note number.
     * This is a simplified implementation.
     */
    private static int getMidiNote(String noteName) {
        String[] noteParts = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        try {
            String pitch = noteName.substring(0, noteName.length() - 1);
            int octave = Integer.parseInt(noteName.substring(noteName.length() - 1));
            
            int noteIndex = -1;
            for (int i = 0; i < noteParts.length; i++) {
                if (noteParts[i].equalsIgnoreCase(pitch)) {
                    noteIndex = i;
                    break;
                }
            }
            
            if (noteIndex == -1) return -1;
            
            return noteIndex + (octave * 12);
        } catch (Exception e) {
            return -1; // Invalid note format
        }
    }
}
