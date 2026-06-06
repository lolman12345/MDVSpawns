package com.mdvcraft.mdvspawns;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;

public final class MythicSpawner {
    private final MDVSpawnsPlugin plugin;
    private boolean warned = false;

    public MythicSpawner(MDVSpawnsPlugin plugin) {
        this.plugin = plugin;
    }

    public LivingEntity spawn(String mobId, Location location, int level) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            warnOnce("MythicMobs no está cargado. No se puede spawnear: " + mobId);
            return null;
        }

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object mobManager = mythicBukkit.getClass().getMethod("getMobManager").invoke(mythicBukkit);
            Object optionalMob = mobManager.getClass().getMethod("getMythicMob", String.class).invoke(mobManager, mobId);

            Object mythicMob = null;
            if (optionalMob instanceof Optional<?> optional) {
                mythicMob = optional.orElse(null);
            }
            if (mythicMob == null) {
                if (plugin.isDebug()) plugin.getLogger().warning("MythicMob no encontrado: " + mobId);
                return null;
            }

            Class<?> adapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
            Object adaptedLocation = adapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Object activeMob = invokeSpawn(mythicMob, adaptedLocation, level);
            if (activeMob == null) {
                plugin.getLogger().warning("No se pudo invocar spawn() para MythicMob: " + mobId);
                return null;
            }

            Object abstractEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
            Object bukkitEntity = abstractEntity.getClass().getMethod("getBukkitEntity").invoke(abstractEntity);
            if (bukkitEntity instanceof LivingEntity living) {
                return living;
            }
            if (bukkitEntity instanceof Entity entity) {
                plugin.getLogger().warning("El MythicMob spawneado no es LivingEntity: " + entity.getType());
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Error usando API de MythicMobs para spawnear '" + mobId + "'.", throwable);
        }
        return null;
    }

    private Object invokeSpawn(Object mythicMob, Object adaptedLocation, int level) throws Exception {
        Method best = null;
        for (Method method : mythicMob.getClass().getMethods()) {
            if (!method.getName().equals("spawn")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 2) continue;
            if (!params[0].isInstance(adaptedLocation)) continue;
            Class<?> levelType = params[1];
            if (levelType == int.class || levelType == Integer.class || levelType == double.class || levelType == Double.class || levelType == float.class || levelType == Float.class) {
                best = method;
                break;
            }
        }
        if (best == null) return null;

        Class<?>[] params = best.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = adaptedLocation;
        if (params[1] == int.class || params[1] == Integer.class) args[1] = level;
        else if (params[1] == float.class || params[1] == Float.class) args[1] = (float) level;
        else args[1] = (double) level;

        // Algunas versiones tienen parámetros opcionales adicionales.
        for (int i = 2; i < params.length; i++) {
            if (params[i] == boolean.class || params[i] == Boolean.class) args[i] = false;
            else if (params[i].isEnum()) args[i] = params[i].getEnumConstants()[0];
            else args[i] = null;
        }
        return best.invoke(mythicMob, args);
    }

    private void warnOnce(String message) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning(message);
    }
}
