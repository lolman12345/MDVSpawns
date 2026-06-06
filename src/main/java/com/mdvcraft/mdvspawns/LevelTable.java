package com.mdvcraft.mdvspawns;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LevelTable {
    private final List<Entry> entries = new ArrayList<>();

    public static LevelTable fromSection(ConfigurationSection section) {
        LevelTable table = new LevelTable();
        if (section == null) {
            table.add(1, 1, 100);
            return table;
        }
        for (String key : section.getKeys(false)) {
            double weight = section.getDouble(key, 0.0);
            if (weight <= 0) continue;
            String cleaned = key.trim().replace(" ", "");
            try {
                if (cleaned.contains("-")) {
                    String[] parts = cleaned.split("-", 2);
                    int min = Integer.parseInt(parts[0]);
                    int max = Integer.parseInt(parts[1]);
                    table.add(min, max, weight);
                } else {
                    int lvl = Integer.parseInt(cleaned);
                    table.add(lvl, lvl, weight);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (table.entries.isEmpty()) {
            table.add(1, 1, 100);
        }
        return table;
    }

    public void add(int min, int max, double weight) {
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        min = Math.max(1, min);
        max = Math.max(min, max);
        entries.add(new Entry(min, max, weight));
    }

    public int roll(Random random) {
        double total = 0;
        for (Entry e : entries) total += e.weight;
        if (total <= 0) return 1;
        double r = random.nextDouble() * total;
        double cursor = 0;
        for (Entry e : entries) {
            cursor += e.weight;
            if (r <= cursor) {
                if (e.min == e.max) return e.min;
                return e.min + random.nextInt(e.max - e.min + 1);
            }
        }
        Entry last = entries.get(entries.size() - 1);
        return last.min;
    }

    private record Entry(int min, int max, double weight) {
    }
}
