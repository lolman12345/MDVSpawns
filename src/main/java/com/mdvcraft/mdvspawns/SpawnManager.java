package com.mdvcraft.mdvspawns;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class SpawnManager implements Listener {
    private final MDVSpawnsPlugin plugin;
    private final MythicSpawner mythicSpawner;
    private final Random random = new Random();
    private final Map<String, SpawnRule> rules = new LinkedHashMap<>();
    private final Set<UUID> tracked = new HashSet<>();

    private BukkitTask spawnTask;
    private BukkitTask cleanupTask;

    public SpawnManager(MDVSpawnsPlugin plugin) {
        this.plugin = plugin;
        this.mythicSpawner = new MythicSpawner(plugin);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reloadRules();
        scanExistingTaggedMobs();
        scheduleTasks();
    }

    public void stop() {
        if (spawnTask != null) spawnTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        spawnTask = null;
        cleanupTask = null;
        tracked.clear();
    }

    public void reloadAll() {
        plugin.reloadConfig();
        plugin.loadSettings();
        reloadRules();
        if (spawnTask != null) spawnTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        scheduleTasks();
    }

    private void scheduleTasks() {
        long tickInterval = Math.max(20L, plugin.getTickInterval());
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSpawn, tickInterval, tickInterval);

        long cleanupInterval = Math.max(100L, plugin.getCleanupInterval());
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, cleanupInterval, cleanupInterval);
    }

    public void reloadRules() {
        rules.clear();
        File folder = new File(plugin.getDataFolder(), "RandomSpawners");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta RandomSpawners.");
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No hay archivos .yml en RandomSpawners.");
            return;
        }

        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String id : yml.getKeys(false)) {
                ConfigurationSection section = yml.getConfigurationSection(id);
                if (section == null) continue;
                try {
                    SpawnRule rule = new SpawnRule(id, section, plugin);
                    if (rule.mythicMob == null || rule.mythicMob.isBlank()) {
                        plugin.getLogger().warning("Spawner '" + id + "' no tiene mythicmob.");
                        continue;
                    }
                    rules.put(id, rule);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Error cargando spawner '" + id + "' en " + file.getName(), ex);
                }
            }
        }
        plugin.getLogger().info("RandomSpawners cargados: " + rules.size());
    }

    private void tickSpawn() {
        if (!plugin.isEnabledSystem()) return;
        if (rules.isEmpty()) return;

        cleanupInvalidOnly();

        int global = countGlobal();
        if (global >= plugin.getGlobalLimit()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isAllowedGamemode(player.getGameMode())) continue;
            if (!player.isValid() || player.isDead()) continue;
            if (global >= plugin.getGlobalLimit()) return;

            int nearPlayer = countNear(player.getLocation(), plugin.getPerPlayerCountRadius(), null);
            if (nearPlayer >= plugin.getPerPlayerLimit()) continue;

            for (int attempt = 0; attempt < plugin.getAttemptsPerPlayer(); attempt++) {
                if (global >= plugin.getGlobalLimit()) return;

                SpawnRule rule = rollRule(player.getWorld().getName());
                if (rule == null) continue;

                int effectivePlayerLimit = rule.effectiveMaxPerPlayer(plugin.getPerPlayerLimit());
                if (countNear(player.getLocation(), plugin.getPerPlayerCountRadius(), null) >= effectivePlayerLimit) continue;

                int groupSize = rule.rollGroupSize(random);
                for (int i = 0; i < groupSize; i++) {
                    if (global >= plugin.getGlobalLimit()) return;
                    if (countNear(player.getLocation(), plugin.getPerPlayerCountRadius(), null) >= effectivePlayerLimit) break;

                    Location loc = findValidLocation(player, rule);
                    if (loc == null) continue;

                    int chunkLimit = rule.effectiveMaxPerChunk(plugin.getPerChunkLimit());
                    if (countInChunk(loc.getChunk()) >= chunkLimit) continue;

                    if (rule.nearbyAmount > 0 && rule.nearbyRadius > 0) {
                        if (countNear(loc, rule.nearbyRadius, rule.mythicMob) >= rule.nearbyAmount) continue;
                    }

                    int level = rule.levelEnabled && plugin.isLevelingEnabled() ? rule.levelTable.roll(random) : 1;
                    LivingEntity entity = mythicSpawner.spawn(rule.mythicMob, loc, level);
                    if (entity == null) continue;

                    tagEntity(entity, rule, player, level);
                    tracked.add(entity.getUniqueId());
                    global++;

                    if (plugin.isDebug()) {
                        plugin.getLogger().info("Spawn " + rule.mythicMob + " nv" + level + " por " + player.getName() + " en " + formatLoc(loc));
                    }
                }
            }
        }
    }

    private SpawnRule rollRule(String worldName) {
        List<SpawnRule> candidates = new ArrayList<>();
        for (SpawnRule rule : rules.values()) {
            if (!rule.enabled) continue;
            if (!rule.canUseInWorld(worldName)) continue;
            candidates.add(rule);
        }
        Collections.shuffle(candidates, random);
        for (SpawnRule rule : candidates) {
            if (random.nextDouble() <= rule.chanceFraction) return rule;
        }
        return null;
    }

    private Location findValidLocation(Player player, SpawnRule rule) {
        for (int attempt = 0; attempt < plugin.getLocationAttempts(); attempt++) {
            Location loc = switch (rule.searchMode) {
                case SURFACE -> randomSurfaceLocation(player, rule);
                case CAVE -> randomCaveLocation(player, rule);
            };
            if (loc != null && isValidSpawnLocation(loc, player, rule)) return loc;
        }
        return null;
    }

    private Location randomSurfaceLocation(Player player, SpawnRule rule) {
        World world = player.getWorld();
        double angle = random.nextDouble() * Math.PI * 2.0;
        int min = Math.max(1, rule.minDistance);
        int max = Math.max(min, rule.maxDistance);
        double distance = min + random.nextDouble() * (max - min);

        int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return null;

        int y = world.getHighestBlockYAt(x, z) + 1;
        if (y < rule.minY || y > rule.maxY) return null;
        if (Math.abs(y - player.getLocation().getBlockY()) > rule.verticalRange) return null;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private Location randomCaveLocation(Player player, SpawnRule rule) {
        World world = player.getWorld();
        double angle = random.nextDouble() * Math.PI * 2.0;
        int min = Math.max(1, rule.minDistance);
        int max = Math.max(min, rule.maxDistance);
        double distance = min + random.nextDouble() * (max - min);

        int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return null;

        int low = Math.max(rule.minY, player.getLocation().getBlockY() - rule.verticalRange);
        int high = Math.min(rule.maxY, player.getLocation().getBlockY() + rule.verticalRange);
        if (low > high) return null;
        int y = ThreadLocalRandom.current().nextInt(low, high + 1);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private boolean isValidSpawnLocation(Location loc, Player owner, SpawnRule rule) {
        World world = loc.getWorld();
        if (world == null) return false;

        if (!rule.canUseInWorld(world.getName())) return false;
        if (loc.getBlockY() < rule.minY || loc.getBlockY() > rule.maxY) return false;

        for (Player other : world.getPlayers()) {
            if (other.getLocation().distanceSquared(loc) < plugin.getAvoidAnyPlayerMinDistance() * plugin.getAvoidAnyPlayerMinDistance()) {
                return false;
            }
        }

        if (!rule.biomes.isEmpty() && !rule.biomes.contains(loc.getBlock().getBiome())) return false;

        Block feet = loc.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);

        if (!feet.isPassable() || !head.isPassable()) return false;
        if (feet.isLiquid() || head.isLiquid()) return false;

        if (!rule.blocks.isEmpty()) {
            if (!rule.blocks.contains(below.getType())) return false;
        } else if (!below.getType().isSolid()) {
            return false;
        }

        return true;
    }

    public boolean forceSpawn(String ruleId, Player player) {
        SpawnRule rule = rules.get(ruleId);
        if (rule == null || !rule.canUseInWorld(player.getWorld().getName())) return false;
        Location loc = findValidLocation(player, rule);
        if (loc == null) return false;
        int level = rule.levelEnabled && plugin.isLevelingEnabled() ? rule.levelTable.roll(random) : 1;
        LivingEntity entity = mythicSpawner.spawn(rule.mythicMob, loc, level);
        if (entity == null) return false;
        tagEntity(entity, rule, player, level);
        tracked.add(entity.getUniqueId());
        return true;
    }

    private void tagEntity(LivingEntity entity, SpawnRule rule, Player owner, int level) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(plugin.getKeySpawned(), PersistentDataType.BYTE, (byte) 1);
        pdc.set(plugin.getKeySpawner(), PersistentDataType.STRING, rule.id);
        pdc.set(plugin.getKeyMob(), PersistentDataType.STRING, rule.mythicMob);
        pdc.set(plugin.getKeyOwner(), PersistentDataType.STRING, owner.getUniqueId().toString());
        pdc.set(plugin.getKeyTime(), PersistentDataType.LONG, System.currentTimeMillis());
        pdc.set(plugin.getKeyLevel(), PersistentDataType.INTEGER, level);
    }

    public boolean isTagged(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.getKeySpawned(), PersistentDataType.BYTE);
    }

    private int countGlobal() {
        cleanupInvalidOnly();
        return tracked.size();
    }

    public int countNear(Location center, int radius, String mythicMobFilter) {
        World world = center.getWorld();
        if (world == null) return 0;
        int count = 0;
        double r = radius;
        for (Entity entity : world.getNearbyEntities(center, r, r, r)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isTagged(living)) continue;
            if (mythicMobFilter != null && !mythicMobFilter.isBlank()) {
                String mob = living.getPersistentDataContainer().get(plugin.getKeyMob(), PersistentDataType.STRING);
                if (!mythicMobFilter.equalsIgnoreCase(mob)) continue;
            }
            count++;
        }
        return count;
    }

    public int countInChunk(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity && isTagged(entity)) count++;
        }
        return count;
    }

    public int countTracked() {
        cleanupInvalidOnly();
        return tracked.size();
    }

    public Collection<String> getRuleIds() {
        return Collections.unmodifiableSet(rules.keySet());
    }

    private void scanExistingTaggedMobs() {
        tracked.clear();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isTagged(entity)) tracked.add(entity.getUniqueId());
            }
        }
    }

    private void cleanupInvalidOnly() {
        tracked.removeIf(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            return entity == null || !entity.isValid() || entity.isDead();
        });
    }

    private void cleanup() {
        cleanupInvalidOnly();
        if (!plugin.isDespawnEnabled()) return;

        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();
        for (UUID uuid : tracked) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                toRemove.add(uuid);
                continue;
            }

            Long spawnedAt = living.getPersistentDataContainer().get(plugin.getKeyTime(), PersistentDataType.LONG);
            if (spawnedAt == null) spawnedAt = now;
            long ageSeconds = (now - spawnedAt) / 1000L;
            if (ageSeconds < plugin.getDespawnAfterSeconds()) continue;

            boolean playerNear = false;
            World world = living.getWorld();
            double maxDistSq = plugin.getDespawnDistance() * plugin.getDespawnDistance();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(living.getLocation()) <= maxDistSq) {
                    playerNear = true;
                    break;
                }
            }
            if (!playerNear) {
                living.remove();
                toRemove.add(uuid);
            }
        }
        tracked.removeAll(toRemove);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (isTagged(event.getEntity())) tracked.remove(event.getEntity().getUniqueId());
    }

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }
}
