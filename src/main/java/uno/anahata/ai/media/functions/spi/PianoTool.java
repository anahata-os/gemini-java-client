package uno.anahata.ai.media.functions.spi;

import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import lombok.AllArgsConstructor;
import lombok.Getter;
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

    @Getter
    @AllArgsConstructor
    public static class Note {
        private final String pitch;
        private final int durationMs;
        private final int velocity;
    }

    @AIToolMethod("Plays a simple melody from a list of notes asynchronously.")
    public static String playMelody(
            @AIToolParam("A list of notes to play.") List<Note> notes
    ) {
        pianoExecutor.submit(() -> {
            String originalMixer = System.getProperty("gervill.mixer");
            try {
                String targetMixerName = findTargetMixerName();
                if (targetMixerName != null) {
                    System.setProperty("gervill.mixer", targetMixerName);
                }
                
                try (Synthesizer synthesizer = MidiSystem.getSynthesizer()) {
                    synthesizer.open();
                    MidiChannel channel = synthesizer.getChannels()[0];

                    for (Note note : notes) {
                        if (Thread.currentThread().isInterrupted()) break;

                        if (note.getPitch().equalsIgnoreCase("R")) {
                            Thread.sleep(note.getDurationMs());
                        } else {
                            int midiNote = getMidiNote(note.getPitch());
                            if (midiNote != -1) {
                                channel.noteOn(midiNote, note.getVelocity());
                                Thread.sleep(note.getDurationMs());
                                channel.noteOff(midiNote);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error playing melody", e);
            } finally {
                // Restore original property or clear it
                if (originalMixer != null) {
                    System.setProperty("gervill.mixer", originalMixer);
                } else {
                    System.clearProperty("gervill.mixer");
                }
            }
        });
        return "Melody playback started.";
    }
    
    /**
     * Finds a suitable target audio mixer for MIDI playback.
     * This is to work around issues where the default Java Sound configuration
     * doesn't correctly route Gervill's output to the speakers.
     * @return The name of a suitable mixer, or null if none is found.
     */
    private static String findTargetMixerName() {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                // We are looking for a mixer that can act as a playback target.
                // It must support SourceDataLine.
                if (mixer.getSourceLineInfo().length > 0) {
                    // And it shouldn't be the Gervill synthesizer itself, which is a source.
                    if (!info.getName().contains("Gervill")) {
                        // Prefer hardware ports. On Linux/ALSA, these often have "plughw" in the name,
                        // or it could be the system default.
                        if (info.getName().contains("plughw") || info.getName().contains("default")) {
                            log.info("Found suitable audio output mixer for PianoTool: {}", info.getName());
                            return info.getName();
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore mixers that can't be opened or inspected.
                log.debug("Could not inspect mixer {}", info.getName(), e);
            }
        }
        log.warn("Could not find an ideal audio output mixer for PianoTool. Sound may not play.");
        return null;
    }


    /**
     * Converts a note name (e.g., "C4", "G#5") to its MIDI note number.
     * This is a simplified implementation.
     */
    private static int getMidiNote(String noteName) {
        String[] noteParts = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        try {
            String pitch = noteName.substring(0, noteName.length() - 1).toUpperCase();
            int octave = Integer.parseInt(noteName.substring(noteName.length() - 1));

            int noteIndex = -1;
            for (int i = 0; i < noteParts.length; i++) {
                if (noteParts[i].equals(pitch)) {
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
