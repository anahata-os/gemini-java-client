package uno.anahata.gemini.ui;

import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.DurationFormatUtils;


/**
 * Utility class for time-related formatting.
 * @author AI
 */
public class TimeUtils {

    /**
     * Formats a duration in milliseconds into a HH:MM:SS string.
     *
     * @param millis The duration in milliseconds.
     * @return A string formatted as HH:MM:SS.
     */
    public static String formatMillis(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Formats a duration in milliseconds into a concise string like "1h 2m 3s".
     * Omits zero-value components.
     *
     * @param millis The duration in milliseconds.
     * @return A concise, human-readable time string.
     */
    public static String formatMillisConcise(long millis) {
        if (millis < 1000) {
            return "0s";
        }
        // Use Apache Commons Lang to create a format like "1 hour 2 minutes 3 seconds"
        // and then abbreviate it.
        String formatted = DurationFormatUtils.formatDurationWords(millis, true, true);
        return formatted
                .replace(" hours", "h")
                .replace(" hour", "h")
                .replace(" minutes", "m")
                .replace(" minute", "m")
                .replace(" seconds", "s")
                .replace(" second", "s");
    }
}
