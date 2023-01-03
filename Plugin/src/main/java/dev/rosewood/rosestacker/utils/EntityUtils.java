package dev.rosewood.rosestacker.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.Lootable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class EntityUtils {

    private static final Random RANDOM = new Random();
    private static Map<EntityType, BoundingBox> cachedBoundingBoxes;

    private static final Cache<ChunkLocation, ChunkSnapshot> chunkSnapshotCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .build();

    /**
     * Get loot for a given entity
     *
     * @param entity The entity to drop loot for
     * @param killer The player who is killing that entity
     * @param lootedLocation The location the entity is being looted at
     * @return The loot
     */
    public static Collection<ItemStack> getEntityLoot(org.bukkit.entity.Entity entity, Player killer, Location lootedLocation) {
        if (entity instanceof Lootable lootable) {
            if (lootable.getLootTable() == null)
                return Set.of();

            LootContext lootContext = new LootContext.Builder(lootedLocation)
                    .lootedEntity(entity)
                    .killer(killer)
                    .build();

            return lootable.getLootTable().populateLoot(RANDOM, lootContext);
        }

        return Set.of();
    }

    /**
     * Gets the approximate amount of experience that an entity of a certain type would drop.
     * This is only an incredibly rough estimate and isn't 1:1 with vanilla.
     *
     * @param type The entity type
     * @return The amount of experience that the entity would probably drop
     */
    public static int getApproximateExperience(Class<? extends Entity> type) {
        if (type == null || NPC.class.isAssignableFrom(type) || Golem.class.isAssignableFrom(type) || type == Bat.class) {
            return 0;
        } else if (Animals.class.isAssignableFrom(type)) {
            return StackerUtils.randomInRange(1, 3);
        } else if (type == Wither.class) {
            return 50;
        } else if (type == Blaze.class || Guardian.class.isAssignableFrom(type)) {
            return 10;
        } else {
            return 5;
        }
    }

    /**
     * A line of sight algorithm to check if two entities can see each other without obstruction
     *
     * @param entity1 The first entity
     * @param entity2 The second entity
     * @param accuracy How often should we check for obstructions? Smaller numbers = more checks (Recommended 0.75)
     * @param requireOccluding Should occluding blocks be required to count as a solid block?
     * @return true if the entities can see each other, otherwise false
     */
    public static boolean hasLineOfSight(Entity entity1, Entity entity2, double accuracy, boolean requireOccluding) {
        if (entity1 instanceof LivingEntity) // Try to use the NMS method if possible, it's significantly faster
            return NMSAdapter.getHandler().hasLineOfSight((org.bukkit.entity.Entity) entity1, entity2);

        Location location1 = entity1.getLocation().clone();
        Location location2 = entity2.getLocation().clone();

        if (entity2 instanceof LivingEntity living)
            location2.add(0, living.getEyeHeight(), 0);

        Vector vector1 = location1.toVector();
        Vector vector2 = location2.toVector();
        Vector direction = vector2.clone().subtract(vector1).normalize();
        double distance = vector1.distance(vector2);
        double numSteps = distance / accuracy;
        double stepSize = distance / numSteps;
        for (double i = 0; i < distance; i += stepSize) {
            Location location = location1.clone().add(direction.clone().multiply(i));
            Block block = location.getBlock();
            Material type = block.getType();
            if (type.isSolid() && (!requireOccluding || StackerUtils.isOccluding(type)))
                return false;
        }

        return true;
    }

    /**
     * Checks if a Player is looking at a dropped item
     *
     * @param player The Player
     * @param item The Item
     * @return true if the Player is looking at the Item, otherwise false
     */
    public static boolean isLookingAtItem(Player player, Item item) {
        Location playerLocation = player.getEyeLocation();
        Vector playerVision = playerLocation.getDirection();

        Vector playerVector = playerLocation.toVector();
        Vector itemLocation = item.getLocation().toVector().add(new Vector(0, 0.3, 0));
        Vector direction = playerVector.clone().subtract(itemLocation).normalize();

        Vector crossProduct = playerVision.getCrossProduct(direction);
        return crossProduct.lengthSquared() <= 0.01;
    }

    /**
     * Gets all blocks that an EntityType would intersect at a Location
     *
     * @param entityType The type of Entity
     * @param location The Location the Entity would be at
     * @return A List of Blocks the Entity intersects with
     */
    public static Map<Location, Material> getIntersectingBlocks(EntityType entityType, Location location) {
        BoundingBox bounds = getBoundingBox(entityType, location).expand(-0.1);
        Map<Location, Material> intersectingBlocks = new HashMap<>();
        World world = location.getWorld();
        if (world == null)
            return intersectingBlocks;

        int minX = floorCoordinate(bounds.getMinX());
        int maxX = floorCoordinate(bounds.getMaxX());
        int minY = floorCoordinate(bounds.getMinY());
        int maxY = floorCoordinate(bounds.getMaxY());
        int minZ = floorCoordinate(bounds.getMinZ());
        int maxZ = floorCoordinate(bounds.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location blockLocation = new Location(world, x, y, z);
                    intersectingBlocks.put(blockLocation, getLazyBlockMaterial(blockLocation));
                }
            }
        }

        return intersectingBlocks;
    }

    public static Material getLazyBlockMaterial(Location location) {
        World world = location.getWorld();
        if (world == null || location.getBlockY() < world.getMinHeight() || location.getBlockY() >= world.getMaxHeight())
            return Material.AIR;

        try {
            ChunkLocation pair = new ChunkLocation(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
            return chunkSnapshotCache.get(pair, () -> {
                Chunk chunk = location.getWorld().getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
                return chunk.getChunkSnapshot();
            }).getBlockType(location.getBlockX() & 15, location.getBlockY(), location.getBlockZ() & 15);
        } catch (Exception e) {
            RoseStacker.getInstance().getLogger().warning("Failed to fetch block type at " + location);
            e.printStackTrace();
            return Material.AIR;
        }
    }

    /**
     * Gets the would-be bounding box of an entity at a location
     *
     * @param entityType The entity type the entity would be
     * @param location The location the entity would be at
     * @return A bounding box for the entity type at the location
     */
    public static BoundingBox getBoundingBox(EntityType entityType, Location location) {
        if (cachedBoundingBoxes == null)
            cachedBoundingBoxes = new HashMap<>();

        BoundingBox boundingBox = cachedBoundingBoxes.get(entityType);
        if (boundingBox == null) {
            boundingBox = NMSAdapter.getHandler().createNewEntityUnspawned(entityType, new Location(location.getWorld(), 0, 0, 0), CreatureSpawnEvent.SpawnReason.CUSTOM).getBoundingBox();
            cachedBoundingBoxes.put(entityType, boundingBox);
        }

        boundingBox = boundingBox.clone();
        boundingBox.shift(location.clone().subtract(0.5, 0, 0.5));
        return boundingBox;
    }

    private static int floorCoordinate(double value) {
        int floored = (int) value;
        return value < (double) floored ? floored - 1 : floored;
    }

    public static void clearCache() {
        cachedBoundingBoxes = null;
    }

    private record ChunkLocation(String world, int x, int z) { }

}
