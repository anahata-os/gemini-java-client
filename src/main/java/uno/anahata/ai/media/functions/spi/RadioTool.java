/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.media.functions.spi;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;
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
    private static final Object lock = new Object();
    
    private static Player player;
    private static Future<?> playbackTask;
    private static String currentStationUrl;
    private static RadioToolPanel ui;

    private static final Map<String, String> STATIONS;

    static {
        Map<String, String> stations = new LinkedHashMap<>();
        // SomaFM - The Gold Standard for Coding
        stations.put("SomaFM Groove Salad", "http://ice1.somafm.com/groovesalad-128-mp3");
        stations.put("SomaFM DEF CON Radio", "http://ice1.somafm.com/defcon-128-mp3");
        stations.put("SomaFM Secret Agent", "http://ice1.somafm.com/secretagent-128-mp3");
        stations.put("SomaFM Indie Pop Rocks!", "http://ice1.somafm.com/indiepop-128-mp3");
        stations.put("SomaFM Drone Zone", "http://ice1.somafm.com/dronezone-128-mp3");
        stations.put("SomaFM Cliqhop IDM", "http://ice1.somafm.com/cliqhop-128-mp3");
        stations.put("SomaFM Beat Blender", "http://ice1.somafm.com/beatblender-128-mp3");
        stations.put("SomaFM Fluid", "http://ice1.somafm.com/fluid-128-mp3");
        stations.put("SomaFM Lush", "http://ice1.somafm.com/lush-128-mp3");
        stations.put("SomaFM Space Station Soma", "http://ice1.somafm.com/spacestation-128-mp3");
        
        // High Quality Public Radio & Eclectic
        stations.put("KEXP Seattle", "https://kexp-mp3-128.streamguys1.com/kexp128.mp3");
        stations.put("WQXR New York", "https://stream.wqxr.org/wqxr");
        stations.put("FIP Paris", "http://icecast.radiofrance.fr/fip-midfi.mp3");
        stations.put("Radio Paradise", "http://stream.radioparadise.com/mp3-128");
        
        // Electronic & Focus
        //not working
        //stations.put("Chillhop Radio", "http://stream.chillhop.com/mp3");
        stations.put("Nightride FM (Synthwave)", "https://stream.nightride.fm/nightride.m4a");
        stations.put("NTS Radio 1", "https://stream-relay-geo.ntslive.net/stream");
        //not working
        //stations.put("The Jazz Groove", "http://west-mp3-128.jazzgroove.org");
        
        STATIONS = Collections.unmodifiableMap(stations);
    }

    @AIToolMethod("Lists available internet radio stations.")
    public static Map<String, String> listStations() {
        return STATIONS;
    }

    @AIToolMethod("Starts playing a random internet radio station. Only one stream can play at a time.")
    public static String startRandom() throws Exception {
        return start(null);
    }

    @AIToolMethod("Starts playing a specific internet radio station by its URL.")
    public static String startStation(
            @AIToolParam("The URL of the radio station to play.") String stationUrl
    ) throws Exception {
        return start(stationUrl);
    }

    /**
     * Internal playback method used by the UI and the start tools.
     */
    public static String start(String stationUrl) throws Exception {
        synchronized (lock) {
            stop(); // Ensure previous stream is fully stopped and cleaned up

            if (stationUrl == null || stationUrl.trim().isEmpty()) {
                List<String> urls = new ArrayList<>(STATIONS.values());
                stationUrl = urls.get(new Random().nextInt(urls.size()));
            }

            currentStationUrl = stationUrl;
            
            final String finalStationUrl = stationUrl;
            String name = STATIONS.entrySet().stream()
                    .filter(e -> e.getValue().equals(finalStationUrl))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("Unknown Station");

            SwingUtilities.invokeLater(() -> {
                if (ui == null || !ui.isVisible()) {
                    ui = new RadioToolPanel();
                    ui.setVisible(true);
                }
                ui.updatePlaybackState(name, true);
            });

            playbackTask = radioExecutor.submit(() -> {
                Player p = null;
                try {
                    URLConnection connection = new URL(finalStationUrl).openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    
                    try (InputStream inputStream = connection.getInputStream()) {
                        synchronized (lock) {
                            if (Thread.currentThread().isInterrupted()) return;
                            p = new Player(inputStream);
                            player = p;
                        }
                        p.play();
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException) && player != null) {
                        log.error("Error playing radio stream: {}", finalStationUrl, e);
                    }
                } finally {
                    synchronized (lock) {
                        if (player == p) {
                            player = null;
                            playbackTask = null;
                            currentStationUrl = null;
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (ui != null) {
                            synchronized (lock) {
                                if (currentStationUrl == null) {
                                    ui.updatePlaybackState(null, false);
                                }
                            }
                        }
                    });
                }
            });

            return "Radio playback started for: " + name;
        }
    }

    @AIToolMethod("Stops the currently playing radio stream.")
    public static String stop() {
        synchronized (lock) {
            if (player != null) {
                try {
                    player.close();
                } catch (Exception e) {
                    log.warn("Error closing player", e);
                }
            }
            if (playbackTask != null) {
                playbackTask.cancel(true);
            }
            
            player = null;
            playbackTask = null;
            currentStationUrl = null;
            
            SwingUtilities.invokeLater(() -> {
                if (ui != null) {
                    ui.updatePlaybackState(null, false);
                }
            });
            
            return "Radio playback stopped.";
        }
    }
    
    @AIToolMethod("Gets the URL of the currently playing station, if any.")
    public static String getCurrentStation() {
        synchronized (lock) {
            return currentStationUrl;
        }
    }
}