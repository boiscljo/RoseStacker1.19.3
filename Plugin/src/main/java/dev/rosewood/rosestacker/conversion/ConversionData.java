package dev.rosewood.rosestacker.conversion;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;

public class ConversionData {

    private final Entity entity;
    private final Item item;
    private final int stackSize;
    private final UUID uuid;

    private ConversionData(org.bukkit.entity.Entity entity, Item item, int stackSize, UUID uuid) {
        this.entity = entity;
        this.item = item;
        this.stackSize = stackSize;
        this.uuid = uuid;
    }

    public ConversionData(UUID uuid, int stackSize) {
        this(null, null, stackSize, uuid);
    }

    public ConversionData(Entity entity, int stackSize) {
        this(entity instanceof LivingEntity ? (org.bukkit.entity.Entity) entity : null,
                entity instanceof Item ? (Item) entity : null,
                stackSize, null);
    }

    public ConversionData(Entity entity) {
        this(entity, -1);
    }

    /**
     * @return the LivingEntity this data holds, or null if there is none
     */
    public Entity getEntity() {
        return this.entity;
    }

    /**
     * @return the Item this data holds, or null if there is none
     */
    public Item getItem() {
        return this.item;
    }

    /**
     * @return the stack size this data holds
     */
    public int getStackSize() {
        return this.stackSize;
    }

    /**
     * @return the UUID of the LivingEntity or Item that this data holds
     */
    public UUID getUniqueId() {
        return this.uuid;
    }

}
