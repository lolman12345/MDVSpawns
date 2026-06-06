package com.mdvcraft.mdvspawns;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class MDVSpawnsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private SpawnManager spawnManager;

    private NamespacedKey keySpawned;
    private NamespacedKey keySpawner;
    private NamespacedKey keyMob;
    private NamespacedKey keyOwner;
    private NamespacedKey keyTime;
    private NamespacedKey keyLevel;

    private boolean enabledSystem;
    private boolean debug;
    private long tickInterval;
    private int attemptsPerPlayer;
    private int globalLimit;
    private int perPlayerLimit;
    private int perPlayerCountRadius;
    private int perChunkLimit;
    private int globalMinDistance;
    private int globalMaxDistance;
    private int globalVerticalRange;
    private int avoidAnyPlayerMinDistance;
    private int locationAttempts;
    private boolean despawnEnabled;
    private int despawnDistance;
    private int despawnAfterSeconds;
    private long cleanupInterval;
    private boolean levelingEnabled;
    private LevelTable defaultLevelTable;
    private final Set<GameMode> allowedGamemodes = EnumSet.noneOf(GameMode.class);

    @Override
    public void onEnable() {
        this.keySpawned = new NamespacedKey(this, "spawned");
        this.keySpawner = new NamespacedKey(this, "spawner");
        this.keyMob = new NamespacedKey(this, "mob");
        this.keyOwner = new NamespacedKey(this, "owner");
        this.keyTime = new NamespacedKey(this, "time");
        this.keyLevel = new NamespacedKey(this, "level");

        saveDefaultConfig();
        saveExampleSpawner();
        loadSettings();

        this.spawnManager = new SpawnManager(this);
        this.spawnManager.start();

        Objects.requireNonNull(getCommand("mdvspawns")).setExecutor(this);
        Objects.requireNonNull(getCommand("mdvspawns")).setTabCompleter(this);

        getLogger().info("MDVSpawns 1.0.0 habilitado.");
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) spawnManager.stop();
    }

    private void saveExampleSpawner() {
        File folder = new File(getDataFolder(), "RandomSpawners");
        if (!folder.exists()) folder.mkdirs();
        File example = new File(folder, "goblins.yml");
        if (!example.exists()) {
            saveResource("RandomSpawners/goblins.yml", false);
        }
    }

    public void loadSettings() {
        this.enabledSystem = getConfig().getBoolean("settings.enabled", true);
        this.debug = getConfig().getBoolean("settings.debug", false);
        this.tickInterval = getConfig().getLong("settings.tick-interval", 80L);
        this.attemptsPerPlayer = Math.max(1, getConfig().getInt("settings.attempts-per-player", 2));
        this.globalLimit = Math.max(0, getConfig().getInt("settings.global-limit", 180));
        this.perPlayerLimit = Math.max(0, getConfig().getInt("settings.per-player-limit", 15));
        this.perPlayerCountRadius = Math.max(8, getConfig().getInt("settings.per-player-count-radius", 96));
        this.perChunkLimit = Math.max(1, getConfig().getInt("settings.per-chunk-limit", 3));
        this.globalMinDistance = Math.max(1, getConfig().getInt("settings.min-distance-from-player", 36));
        this.globalMaxDistance = Math.max(globalMinDistance, getConfig().getInt("settings.max-distance-from-player", 72));
        this.globalVerticalRange = Math.max(1, getConfig().getInt("settings.vertical-range", 20));
        this.avoidAnyPlayerMinDistance = Math.max(1, getConfig().getInt("settings.avoid-any-player-min-distance", 32));
        this.locationAttempts = Math.max(1, getConfig().getInt("settings.location-attempts", 24));
        this.despawnEnabled = getConfig().getBoolean("settings.despawn-enabled", true);
        this.despawnDistance = Math.max(16, getConfig().getInt("settings.despawn-distance", 128));
        this.despawnAfterSeconds = Math.max(10, getConfig().getInt("settings.despawn-after-seconds", 300));
        this.cleanupInterval = Math.max(100L, getConfig().getLong("settings.cleanup-interval", 600L));

        this.allowedGamemodes.clear();
        List<String> modes = getConfig().getStringList("settings.allowed-gamemodes");
        if (modes.isEmpty()) modes = List.of("SURVIVAL", "ADVENTURE");
        for (String mode : modes) {
            try {
                allowedGamemodes.add(GameMode.valueOf(mode.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        if (allowedGamemodes.isEmpty()) {
            allowedGamemodes.add(GameMode.SURVIVAL);
            allowedGamemodes.add(GameMode.ADVENTURE);
        }

        this.levelingEnabled = getConfig().getBoolean("leveling.enabled", true);
        ConfigurationSection dist = getConfig().getConfigurationSection("leveling.distribution");
        this.defaultLevelTable = LevelTable.fromSection(dist);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mdvspawns.admin")) {
            sender.sendMessage("§cNo tienes permiso.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6MDVSpawns §7/" + label + " reload|debug|count|force <spawner> [jugador]|list");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                spawnManager.reloadAll();
                sender.sendMessage("§aMDVSpawns recargado. RandomSpawners: §f" + spawnManager.getRuleIds().size());
                return true;
            }
            case "debug" -> {
                this.debug = !this.debug;
                sender.sendMessage("§aDebug MDVSpawns: §f" + debug);
                return true;
            }
            case "count" -> {
                sender.sendMessage("§aMobs MDVSpawns cargados: §f" + spawnManager.countTracked() + "§7/§f" + globalLimit);
                if (sender instanceof Player player) {
                    sender.sendMessage("§aCerca de ti: §f" + spawnManager.countNear(player.getLocation(), perPlayerCountRadius, null) + "§7/§f" + perPlayerLimit);
                    sender.sendMessage("§aEn tu chunk: §f" + spawnManager.countInChunk(player.getChunk()) + "§7/§f" + perChunkLimit);
                }
                return true;
            }
            case "list" -> {
                sender.sendMessage("§aRandomSpawners cargados: §f" + String.join(", ", spawnManager.getRuleIds()));
                return true;
            }
            case "force" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /" + label + " force <spawner> [jugador]");
                    return true;
                }
                Player target;
                if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
                else if (sender instanceof Player player) target = player;
                else target = null;

                if (target == null) {
                    sender.sendMessage("§cJugador no encontrado.");
                    return true;
                }
                boolean ok = spawnManager.forceSpawn(args[1], target);
                sender.sendMessage(ok ? "§aSpawn forzado correctamente." : "§cNo se pudo forzar el spawn. Revisa mundo/condiciones/API MythicMobs.");
                return true;
            }
            default -> {
                sender.sendMessage("§cSubcomando desconocido.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mdvspawns.admin")) return List.of();
        if (args.length == 1) {
            return filter(List.of("reload", "debug", "count", "force", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("force")) {
            return filter(new ArrayList<>(spawnManager.getRuleIds()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("force")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }

    public boolean isEnabledSystem() { return enabledSystem; }
    public boolean isDebug() { return debug; }
    public long getTickInterval() { return tickInterval; }
    public int getAttemptsPerPlayer() { return attemptsPerPlayer; }
    public int getGlobalLimit() { return globalLimit; }
    public int getPerPlayerLimit() { return perPlayerLimit; }
    public int getPerPlayerCountRadius() { return perPlayerCountRadius; }
    public int getPerChunkLimit() { return perChunkLimit; }
    public int getGlobalMinDistance() { return globalMinDistance; }
    public int getGlobalMaxDistance() { return globalMaxDistance; }
    public int getGlobalVerticalRange() { return globalVerticalRange; }
    public int getAvoidAnyPlayerMinDistance() { return avoidAnyPlayerMinDistance; }
    public int getLocationAttempts() { return locationAttempts; }
    public boolean isDespawnEnabled() { return despawnEnabled; }
    public int getDespawnDistance() { return despawnDistance; }
    public int getDespawnAfterSeconds() { return despawnAfterSeconds; }
    public long getCleanupInterval() { return cleanupInterval; }
    public boolean isLevelingEnabled() { return levelingEnabled; }
    public LevelTable getDefaultLevelTable() { return defaultLevelTable; }
    public boolean isAllowedGamemode(GameMode mode) { return allowedGamemodes.contains(mode); }

    public NamespacedKey getKeySpawned() { return keySpawned; }
    public NamespacedKey getKeySpawner() { return keySpawner; }
    public NamespacedKey getKeyMob() { return keyMob; }
    public NamespacedKey getKeyOwner() { return keyOwner; }
    public NamespacedKey getKeyTime() { return keyTime; }
    public NamespacedKey getKeyLevel() { return keyLevel; }
}
