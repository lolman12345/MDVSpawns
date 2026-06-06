package com.mdvcraft.mdvspawns;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class SpawnRule {
    public enum SearchMode {
        SURFACE,
        CAVE
    }

    public final String id;
    public final boolean enabled;
    public final String mythicMob;
    public final double chanceFraction;
    public final int groupMin;
    public final int groupMax;
    public final Set<String> worlds;
    public final SearchMode searchMode;
    public final int minY;
    public final int maxY;
    public final int minDistance;
    public final int maxDistance;
    public final int verticalRange;
    public final int maxPerPlayer;
    public final int maxPerChunk;
    public final int nearbyRadius;
    public final int nearbyAmount;
    public final Set<Biome> biomes;
    public final Set<Material> blocks;
    public final boolean levelEnabled;
    public final LevelTable levelTable;

    public SpawnRule(String id, ConfigurationSection section, MDVSpawnsPlugin plugin) {
        this.id = id;
        this.enabled = section.getBoolean("enabled", true);
        this.mythicMob = section.getString("mythicmob", section.getString("MobType", section.getString("Type", "")));

        double rawChance = section.getDouble("chance", section.getDouble("Chance", 0.0));
        // Compatibilidad: chance 0.03 = 3%, chance 3 = 3%.
        this.chanceFraction = rawChance <= 1.0 ? Math.max(0.0, rawChance) : Math.max(0.0, rawChance / 100.0);

        int[] group = parseRange(section.getString("group-size", section.getString("GroupSize", "1")), 1, 1);
        this.groupMin = Math.max(1, group[0]);
        this.groupMax = Math.max(this.groupMin, group[1]);

        this.worlds = new HashSet<>(section.getStringList("worlds"));
        if (this.worlds.isEmpty()) this.worlds.add(section.getString("Worlds", "world"));

        String mode = section.getString("search-mode", "SURFACE");
        SearchMode parsedMode;
        try {
            parsedMode = SearchMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            parsedMode = SearchMode.SURFACE;
        }
        this.searchMode = parsedMode;

        this.minY = section.getInt("min-y", section.getInt("minY", -64));
        this.maxY = section.getInt("max-y", section.getInt("maxY", 320));
        this.minDistance = section.getInt("min-distance-from-player", plugin.getGlobalMinDistance());
        this.maxDistance = section.getInt("max-distance-from-player", plugin.getGlobalMaxDistance());
        this.verticalRange = section.getInt("vertical-range", plugin.getGlobalVerticalRange());

        this.maxPerPlayer = section.getInt("max-per-player", -1);
        this.maxPerChunk = section.getInt("max-per-chunk", -1);

        ConfigurationSection nearby = section.getConfigurationSection("max-nearby");
        if (nearby != null) {
            this.nearbyRadius = nearby.getInt("radius", -1);
            this.nearbyAmount = nearby.getInt("amount", -1);
        } else {
            this.nearbyRadius = section.getInt("nearby-radius", -1);
            this.nearbyAmount = section.getInt("nearby-amount", -1);
        }

        this.biomes = parseBiomes(section.getStringList("biomes"));
        this.blocks = parseMaterials(section.getStringList("blocks"));

        ConfigurationSection levelSection = section.getConfigurationSection("level");
        this.levelEnabled = levelSection == null || levelSection.getBoolean("enabled", true);
        ConfigurationSection dist = levelSection != null ? levelSection.getConfigurationSection("distribution") : null;
        this.levelTable = dist != null ? LevelTable.fromSection(dist) : plugin.getDefaultLevelTable();
    }

    public boolean canUseInWorld(String world) {
        return worlds.contains(world);
    }

    public int rollGroupSize(Random random) {
        if (groupMin == groupMax) return groupMin;
        return groupMin + random.nextInt(groupMax - groupMin + 1);
    }

    public int effectiveMaxPerPlayer(int global) {
        return maxPerPlayer > 0 ? maxPerPlayer : global;
    }

    public int effectiveMaxPerChunk(int global) {
        return maxPerChunk > 0 ? maxPerChunk : global;
    }

    private static int[] parseRange(String text, int defMin, int defMax) {
        if (text == null || text.isBlank()) return new int[]{defMin, defMax};
        String cleaned = text.trim().replace(" ", "");
        try {
            if (cleaned.contains("-")) {
                String[] parts = cleaned.split("-", 2);
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
            }
            int value = Integer.parseInt(cleaned);
            return new int[]{value, value};
        } catch (NumberFormatException ex) {
            return new int[]{defMin, defMax};
        }
    }

    private static Set<Material> parseMaterials(List<String> values) {
        Set<Material> set = new HashSet<>();
        for (String value : values) {
            try {
                Material material = Material.valueOf(value.toUpperCase(Locale.ROOT));
                set.add(material);
            } catch (Exception ignored) {
            }
        }
        return set;
    }

    private static Set<Biome> parseBiomes(List<String> values) {
        Set<Biome> set = new HashSet<>();
        for (String value : values) {
            try {
                Biome biome = Biome.valueOf(value.toUpperCase(Locale.ROOT));
                set.add(biome);
            } catch (Exception ignored) {
            }
        }
        return set;
    }
}
