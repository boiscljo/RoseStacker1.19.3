package dev.rosewood.rosestacker.hook;

import dev.rosewood.rosegarden.utils.RoseGardenUtils;
import dev.rosewood.roseloot.util.LootUtils;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

public class SpawnerFlagPersistenceHook {

    private static Boolean mcMMOEnabled;
    private static McMMOHook mcMMOHookHandler;
    private static Boolean jobsEnabled;
    private static Boolean roseLootEnabled;

    /**
     * @return true if mcMMO is enabled, false otherwise
     */
    public static boolean mcMMOEnabled() {
        if (mcMMOEnabled != null)
            return mcMMOEnabled;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("mcMMO");
        mcMMOEnabled = plugin != null && plugin.getDescription().getVersion().startsWith("2");
        if (mcMMOEnabled)
            mcMMOHookHandler = RoseGardenUtils.isUpdateAvailable("2.1.210", plugin.getDescription().getVersion()) ? new OldMcMMOHook() : new NewMcMMOHook();
        return mcMMOEnabled;
    }

    /**
     * @return true if Jobs is enabled, false otherwise
     */
    public static boolean jobsEnabled() {
        if (jobsEnabled != null)
            return jobsEnabled;
        return jobsEnabled = Bukkit.getPluginManager().getPlugin("Jobs") != null;
    }

    /**
     * @return true if RoseLoot is enabled, false otherwise
     */
    public static boolean roseLootEnabled() {
        if (roseLootEnabled != null)
            return roseLootEnabled;
        return roseLootEnabled = Bukkit.getPluginManager().getPlugin("RoseLoot") != null;
    }

    /**
     * Flags a LivingEntity as having been spawned from a spawner
     *
     * @param entity The LivingEntity to flag
     */
    public static void flagSpawnerSpawned(org.bukkit.entity.Entity entity) {
        if (mcMMOEnabled())
            mcMMOHookHandler.flagSpawnerMetadata(entity);

        if (jobsEnabled()) {
            Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("Jobs");
            if (jobsPlugin != null)
                entity.setMetadata("jobsMobSpawner", new FixedMetadataValue(jobsPlugin, true));
        }

        if (roseLootEnabled())
            if(entity instanceof LivingEntity living)
                LootUtils.setEntitySpawnReason(living, SpawnReason.SPAWNER);
    }

    /**
     * Set's the LivingEntity's spawner persistence state if it was spawned from a spawner
     *
     * @param entity The entity to set the persistence state of
     */
    public static void setPersistence(org.bukkit.entity.Entity entity) {
        if (!PersistentDataUtils.isSpawnedFromSpawner(entity))
            return;

        if (mcMMOEnabled())
            mcMMOHookHandler.flagSpawnerMetadata(entity);

        if (jobsEnabled()) {
            Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("Jobs");
            if (jobsPlugin != null)
                entity.setMetadata("jobsMobSpawner", new FixedMetadataValue(jobsPlugin, true));
        }
    }

}
