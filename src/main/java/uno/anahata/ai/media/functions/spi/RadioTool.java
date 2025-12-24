/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.functions.spi;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javazoom.jl.player.Player;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.AnahataExecutors;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;

/**
 * AI-callable tools for playing internet radio streams.
 */
@Slf4j
public class RadioTool {

    private static final ExecutorService radioExecutor = AnahataExecutors.newCachedThreadPoolExecutor("radio-player");
    private static volatile Player player;
    private static volatile Future<?> playbackTask;
    private static String currentStationUrl;

    private static final Map<String, String> STATIONS;

    static {
        Map<String, String> stations = new LinkedHashMap<>();
        stations.put("SomaFM Groove Salad", "http://ice1.somafm.com/groovesalad-128-mp3");
        stations.put("SomaFM DEF CON Radio", "http://ice1.somafm.com/defcon-128-mp3");
        stations.put("SomaFM Drone Zone", "http://ice1.somafm.com/dronezone-128-mp3");
        stations.put("SomaFM Cliqhop IDM", "http://ice1.somafm.com/cliqhop-128-mp3");
        STATIONS = Collections.unmodifiableMap(stations);
    }

    @AIToolMethod("Lists available internet radio stations.")
    public static Map<String, String> listStations() {
        return STATIONS;
    }

    @AIToolMethod("Starts playing an internet radio stream. Only one stream can play at a time.")
    public static String start(
            @AIToolParam("The URL of the radio station to play. See listStations() for options.") String stationUrl
    ) throws Exception {
        if (playbackTask != null && !playbackTask.isDone()) {
            return "A station is already playing. Please stop it first.";
        }

        currentStationUrl = stationUrl;
        playbackTask = radioExecutor.submit(() -> {
            try (InputStream inputStream = new URL(stationUrl).openStream()) {
                player = new Player(inputStream);
                player.play();
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    log.error("Error playing radio stream", e);
                }
            } finally {
                player = null;
                playbackTask = null;
                currentStationUrl = null;
            }
        });

        return "Radio playback started for: " + stationUrl;
    }

    @AIToolMethod("Stops the currently playing radio stream.")
    public static String stop() {
        if (player != null) {
            player.close();
        }
        if (playbackTask != null) {
            playbackTask.cancel(true);
        }
        
        player = null;
        playbackTask = null;
        currentStationUrl = null;
        
        return "Radio playback stopped.";
    }
    
    @AIToolMethod("Gets the URL of the currently playing station, if any.")
    public static String getCurrentStation() {
        return currentStationUrl;
    }
}