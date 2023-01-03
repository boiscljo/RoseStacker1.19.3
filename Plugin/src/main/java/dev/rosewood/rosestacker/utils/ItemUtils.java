package dev.rosewood.rosestacker.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.rosewood.rosegarden.utils.HexUtils;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.manager.ConfigurationManager;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.spawner.SpawnerType;
import dev.rosewood.rosestacker.stack.settings.BlockStackSettings;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.ApiStatus;

public final class ItemUtils {

    private final static Map<String, ItemStack> skullCache = new HashMap<>();
    private static Field field_SkullMeta_profile;

    private static ItemStack cachedStackingTool;

    public static Material getWoolMaterial(DyeColor dyeColor) {
        if (dyeColor == null)
            return Material.WHITE_WOOL;
        return Material.matchMaterial(dyeColor.name() + "_WOOL");
    }

    public static void takeItems(int amount, Player player, EquipmentSlot handType) {
        if (player.getGameMode() == GameMode.CREATIVE)
            return;

        ItemStack itemStack = handType == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        int newAmount = itemStack.getAmount() - amount;
        if (newAmount <= 0) {
            if (handType == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        } else {
            itemStack.setAmount(newAmount);
        }
    }

    /**
     * Drops a List of ItemStacks into a Player's Inventory, with any overflow dropped onto the ground
     *
     * @param player The Player to give items to
     * @param itemStacks The ItemStacks to give
     */
    public static void dropItemsToPlayer(Player player, Collection<ItemStack> itemStacks) {
        List<ItemStack> extraItems = new ArrayList<>();
        for (ItemStack itemStack : itemStacks)
            extraItems.addAll(player.getInventory().addItem(itemStack).values());

        if (!extraItems.isEmpty())
            RoseStacker.getInstance().getManager(StackManager.class).preStackItems(extraItems, player.getLocation());
    }

    public static void damageTool(ItemStack itemStack) {
        Damageable damageable = (Damageable) itemStack.getItemMeta();
        if (damageable == null)
            return;

        damageable.setDamage(damageable.getDamage() + 1);
        itemStack.setItemMeta((ItemMeta) damageable);
    }

    /**
     * Gets a custom player head from a base64 encoded texture
     *
     * @param texture The texture to apply to the player head
     * @return A player head with the custom texture applied
     */
    public static ItemStack getCustomSkull(String texture) {
        if (skullCache.containsKey(texture))
            return skullCache.get(texture).clone();

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (texture == null || texture.isEmpty())
            return skull;

        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta == null)
            return skull;

        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", texture));
        
        try {
            if (field_SkullMeta_profile == null) {
                field_SkullMeta_profile = skullMeta.getClass().getDeclaredField("profile");
                field_SkullMeta_profile.setAccessible(true);
            }
            //MAGMA: This break the server somehow, so temporarely disabled unless we can find a solution
            //field_SkullMeta_profile.set(skullMeta, profile);
            
        } catch (ReflectiveOperationException ex) {
            ex.printStackTrace();
        }

        skull.setItemMeta(skullMeta);

        skullCache.put(texture, skull);
        return skull.clone();
    }

    public static ItemStack getBlockAsStackedItemStack(Material material, int amount) {
        ItemStack itemStack = new ItemStack(material);
        if (amount == 1)
            return itemStack;

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null)
            return itemStack;

        BlockStackSettings stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getBlockStackSettings(material);
        StringPlaceholders placeholders = StringPlaceholders.builder("amount", StackerUtils.formatNumber(amount)).addPlaceholder("name", stackSettings.getDisplayName()).build();
        String displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("block-stack-display", placeholders);

        itemMeta.setDisplayName(displayString);

        // Set the lore, if defined
        List<String> lore = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessages("stack-item-lore-block", placeholders);
        if (!lore.isEmpty())
            itemMeta.setLore(lore);

        itemStack.setItemMeta(itemMeta);

        // Set stack size
        NMSHandler nmsHandler = NMSAdapter.getHandler();
        itemStack = nmsHandler.setItemStackNBT(itemStack, "StackSize", amount);

        return itemStack;
    }

    public static boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }

    @ApiStatus.Obsolete
    public static ItemStack getSpawnerAsStackedItemStack(EntityType entityType, int amount) {
        return getSpawnerAsStackedItemStack(SpawnerType.of(entityType), amount);
    }

    public static ItemStack getSpawnerAsStackedItemStack(SpawnerType spawnerType, int amount) {
        ItemStack itemStack = new ItemStack(Material.SPAWNER);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null)
            return itemStack;

        SpawnerStackSettings stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getSpawnerStackSettings(spawnerType);
        StringPlaceholders placeholders = StringPlaceholders.builder("amount", StackerUtils.formatNumber(amount)).addPlaceholder("name", stackSettings.getDisplayName()).build();
        String displayString;
        if (amount == 1) {
            displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("spawner-stack-display-single", placeholders);
        } else {
            displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("spawner-stack-display", placeholders);
        }

        itemMeta.setDisplayName(displayString);

        // Set the lore, if defined
        List<String> lore = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessages("stack-item-lore-spawner", placeholders);
        if (!lore.isEmpty())
            itemMeta.setLore(lore);

        if (!spawnerType.isEmpty()) {
            // Set the spawned type directly onto the spawner item for hopeful compatibility with other plugins
            BlockStateMeta blockStateMeta = (BlockStateMeta) itemMeta;
            CreatureSpawner creatureSpawner = (CreatureSpawner) blockStateMeta.getBlockState();
            creatureSpawner.setSpawnedType(spawnerType.getOrThrow());
            blockStateMeta.setBlockState(creatureSpawner);
        }

        itemStack.setItemMeta(itemMeta);

        // Set stack size and spawned entity type
        NMSHandler nmsHandler = NMSAdapter.getHandler();
        itemStack = nmsHandler.setItemStackNBT(itemStack, "StackSize", amount);
        itemStack = nmsHandler.setItemStackNBT(itemStack, "EntityType", spawnerType.getEnumName());

        return itemStack;
    }

    public static ItemStack getEntityAsStackedItemStack(EntityType entityType, int amount) {
        EntityStackSettings stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getEntityStackSettings(entityType);
        Material spawnEggMaterial = stackSettings.getEntityTypeData().getSpawnEggMaterial();
        System.out.println("218:"+entityType);
        System.out.println("219:"+spawnEggMaterial);
        if (spawnEggMaterial == null)
            return null;

        ItemStack itemStack = new ItemStack(spawnEggMaterial);
        if (amount == 1)
            return itemStack;

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null)
            return itemStack;

        StringPlaceholders placeholders = StringPlaceholders.builder("amount", StackerUtils.formatNumber(amount)).addPlaceholder("name", stackSettings.getDisplayName()).build();
        String displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("entity-stack-display-spawn-egg", placeholders);

        itemMeta.setDisplayName(displayString);

        // Set the lore, if defined
        List<String> lore = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessages("stack-item-lore-entity", placeholders);
        if (!lore.isEmpty())
            itemMeta.setLore(lore);

        itemStack.setItemMeta(itemMeta);

        // Set stack size
        NMSHandler nmsHandler = NMSAdapter.getHandler();
        itemStack = nmsHandler.setItemStackNBT(itemStack, "StackSize", amount);
        System.out.println("246:"+nmsHandler.getItemStackNBTInt(itemStack,"StackSize"));
        return itemStack;
    }

    public static int getStackedItemStackAmount(ItemStack itemStack) {
        NMSHandler nmsHandler = NMSAdapter.getHandler();
        int stackSize = nmsHandler.getItemStackNBTInt(itemStack, "StackSize");
        return Math.max(stackSize, 1);
    }

    public static boolean hasStoredStackSize(ItemStack itemStack) {
        NMSHandler nmsHandler = NMSAdapter.getHandler();
        return nmsHandler.getItemStackNBTInt(itemStack, "StackSize") > 0;
    }

    public static EntityType getStackedItemEntityType(ItemStack itemStack) {
        if (itemStack.getType() != Material.SPAWNER)
            return null;

        // First, check our NBT value
        NMSHandler nmsHandler = NMSAdapter.getHandler();
        String entityTypeName = nmsHandler.getItemStackNBTString(itemStack, "EntityType");
        if (!entityTypeName.isEmpty()) {
            try {
                if (entityTypeName.equals("EMPTY"))
                    return null;
                return EntityType.valueOf(entityTypeName);
            } catch (Exception ignored) { }
        }

        // Try formats from other plugins/servers

        // Purpur servers
        entityTypeName = nmsHandler.getItemStackNBTString(itemStack, "Purpur.mob_type");

        // EpicSpawners Pre-v7
        if (entityTypeName.isEmpty())
            entityTypeName = nmsHandler.getItemStackNBTString(itemStack, "type").toUpperCase().replace(' ', '_');

        // EpicSpawners Post-v7
        if (entityTypeName.isEmpty())
            entityTypeName = nmsHandler.getItemStackNBTString(itemStack, "data");

        // MineableSpawners
        if (entityTypeName.isEmpty())
            entityTypeName = nmsHandler.getItemStackNBTString(itemStack, "ms_mob");

        if (!entityTypeName.isEmpty()) {
            try {
                NamespacedKey entityTypeKey = NamespacedKey.fromString(entityTypeName);
                for (EntityType entityType : EntityType.values())
                    if (entityType != EntityType.UNKNOWN && entityType.getKey().equals(entityTypeKey) || entityTypeName.equalsIgnoreCase(entityType.name()))
                        return EntityType.valueOf(entityTypeName);
            } catch (Exception ignored) { }
        }

        // Try checking the spawner data then?
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null)
            return null;

        BlockStateMeta blockStateMeta = (BlockStateMeta) itemMeta;
        CreatureSpawner creatureSpawner = (CreatureSpawner) blockStateMeta.getBlockState();
        if (creatureSpawner.getSpawnedType() != EntityType.PIG)
            return creatureSpawner.getSpawnedType();

        // Use the name to determine the type, name must be colored
        String name = ChatColor.stripColor(itemMeta.getDisplayName());
        if (!name.equals(itemMeta.getDisplayName())) {
            try {
                // This tries to support other spawner plugins by checking the item name
                name = name.toUpperCase();
                int spawnerIndex = name.indexOf("SPAWNER");
                String entityName = name.substring(0, spawnerIndex).trim();
                return EntityType.valueOf(entityName.replaceAll(" ", "_"));
            } catch (Exception ignored) { }
        }

        return null;
    }

    public static SpawnerType getStackedItemSpawnerType(ItemStack itemStack) {
        EntityType entityType = getStackedItemEntityType(itemStack);
        return entityType == null ? SpawnerType.empty() : SpawnerType.of(entityType);
    }

    public static ItemStack getStackingTool() {
        if (cachedStackingTool != null)
            return cachedStackingTool;

        Material material = Material.matchMaterial(ConfigurationManager.Setting.STACK_TOOL_MATERIAL.getString());
        if (material == null) {
            material = Material.STICK;
            RoseStacker.getInstance().getLogger().warning("Invalid material for stacking tool in config.yml!");
        }

        String name = HexUtils.colorify(ConfigurationManager.Setting.STACK_TOOL_NAME.getString());
        List<String> lore = ConfigurationManager.Setting.STACK_TOOL_LORE.getStringList().stream().map(HexUtils::colorify).toList();

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);

        item.setItemMeta(meta);
        cachedStackingTool = item;
        return item;
    }

    public static boolean isStackingTool(ItemStack item) {
        return getStackingTool().isSimilar(item);
    }

    public static List<ItemStack> getMultipliedItemStack(ItemStack itemStack, double multiplier) {
        int amount = (int) Math.round(itemStack.getAmount() * multiplier);
        if (amount == 0)
            return List.of();

        List<ItemStack> items = new ArrayList<>();
        while (amount > 0) {
            if (amount > itemStack.getMaxStackSize()) {
                ItemStack clone = itemStack.clone();
                clone.setAmount(itemStack.getMaxStackSize());
                items.add(clone);
                amount -= itemStack.getMaxStackSize();
            } else {
                ItemStack clone = itemStack.clone();
                clone.setAmount(amount);
                items.add(clone);
                amount = 0;
            }
        }
        return items;
    }

    public static void clearCache() {
        skullCache.clear();
        cachedStackingTool = null;
    }

}
