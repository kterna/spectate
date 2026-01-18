package com.spectate.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.spectate.SpectateMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SpectateStatsManager {
    private static final SpectateStatsManager INSTANCE = new SpectateStatsManager();
    private static final String STATS_FILE_NAME = "spectate_stats.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, SpectateStats> statsCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private final Path statsFile;

    public static SpectateStatsManager getInstance() { return INSTANCE; }

    private SpectateStatsManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.statsFile = configDir.resolve("spectate").resolve(STATS_FILE_NAME);
    }

    public void initialize() {
        loadStats();
    }

    public void updateName(UUID uuid, String name) {
        nameCache.put(uuid, name);
    }
    
    public String getName(UUID uuid) {
        return nameCache.getOrDefault(uuid, uuid.toString());
    }

    public synchronized void addSpectatingTime(UUID uuid, long millis) {
        SpectateStats stats = statsCache.computeIfAbsent(uuid, k -> new SpectateStats());
        stats.totalSpectatingTime += millis;
        saveStats();
    }

    public synchronized void addSpectatedTime(UUID uuid, long millis) {
        SpectateStats stats = statsCache.computeIfAbsent(uuid, k -> new SpectateStats());
        stats.totalSpectatedTime += millis;
        saveStats();
    }

    public SpectateStats getStats(UUID uuid) {
        return statsCache.getOrDefault(uuid, new SpectateStats());
    }

    public List<Map.Entry<UUID, Long>> getTopViewing(int limit) {
        return statsCache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().totalSpectatingTime, e1.getValue().totalSpectatingTime))
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().totalSpectatingTime))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<UUID, Long>> getTopWatched(int limit) {
        return statsCache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().totalSpectatedTime, e1.getValue().totalSpectatedTime))
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().totalSpectatedTime))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void loadStats() {
        if (Files.notExists(statsFile)) return;
        try (FileReader reader = new FileReader(statsFile.toFile())) {
             Type type = new TypeToken<StatsData>() {}.getType();
             StatsData data = GSON.fromJson(reader, type);
             if (data != null) {
                 if (data.stats != null) statsCache.putAll(data.stats);
                 if (data.names != null) nameCache.putAll(data.names);
             }
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] Failed to load stats.", e);
        }
    }

    private synchronized void saveStats() {
        try {
            Files.createDirectories(statsFile.getParent());
            try (FileWriter writer = new FileWriter(statsFile.toFile())) {
                StatsData data = new StatsData();
                data.stats = statsCache;
                data.names = nameCache;
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
             SpectateMod.LOGGER.error("[Spectate] Failed to save stats.", e);
        }
    }
    
    private static class StatsData {
        Map<UUID, SpectateStats> stats;
        Map<UUID, String> names;
    }
}
