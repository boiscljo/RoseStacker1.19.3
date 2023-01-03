package dev.rosewood.rosestacker.listener;

import dev.rosewood.guiframework.framework.util.GuiUtil;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.event.AsyncEntityDeathEvent;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.EntityCacheManager;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataEntry;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataStorageType;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.StackedItem;
import dev.rosewood.rosestacker.stack.StackedSpawner;
import dev.rosewood.rosestacker.stack.settings.ItemStackSettings;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.entity.ChickenStackSettings;
import dev.rosewood.rosestacker.stack.settings.entity.MushroomCowStackSettings;
import dev.rosewood.rosestacker.stack.settings.entity.SheepStackSettings;
import dev.rosewood.rosestacker.utils.EntityUtils;
import dev.rosewood.rosestacker.utils.ItemUtils;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import dev.rosewood.rosestacker.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.MushroomCow.Variant;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityTransformEvent.TransformReason;
import org.bukkit.event.entity.PigZapEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class EntityListener implements Listener {

    private static final Set<SpawnReason> DELAYED_SPAWN_REASONS = EnumSet.of(
            SpawnReason.BEEHIVE,
            SpawnReason.BUILD_IRONGOLEM,
            SpawnReason.BUILD_SNOWMAN,
            SpawnReason.BUILD_WITHER);

    private final RosePlugin rosePlugin;
    private final StackManager stackManager;
    private final StackSettingManager stackSettingManager;
    private final EntityCacheManager entityCacheManager;

    public EntityListener(RosePlugin rosePlugin) {
        this.rosePlugin = rosePlugin;

        this.stackManager = this.rosePlugin.getManager(StackManager.class);
        this.stackSettingManager = this.rosePlugin.getManager(StackSettingManager.class);
        this.entityCacheManager = this.rosePlugin.getManager(EntityCacheManager.class);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (this.stackManager.isWorldDisabled(entity.getWorld()))
            return;

        if (!this.stackManager.isItemStackingEnabled() || this.stackManager.isEntityStackingTemporarilyDisabled())
            return;

        if (entity instanceof Item item) {
            ItemStackSettings itemStackSettings = this.stackSettingManager.getItemStackSettings(item);
            if (itemStackSettings != null && !itemStackSettings.isStackingEnabled())
                return;

            this.entityCacheManager.preCacheEntity(entity);
            this.stackManager.createItemStack(item, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();
        if (this.stackManager.isWorldDisabled(entity.getWorld()))
            return;

        if (!this.stackManager.isEntityStackingEnabled() || this.stackManager.isEntityStackingTemporarilyDisabled())
            return;

        Runnable task = () -> {
            PersistentDataUtils.setEntitySpawnReason(entity, event.getSpawnReason());
            this.entityCacheManager.preCacheEntity(entity);

            // Try to immediately stack everything except bees from hives and built entities
            // due to them duplicating
            this.stackManager.createEntityStack(entity, !DELAYED_SPAWN_REASONS.contains(event.getSpawnReason()));

            PersistentDataUtils.applyDisabledAi(entity);
        };

        // Delay stacking by 1 tick for spawn eggs due to an egg duplication issue
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            ThreadUtils.runSync(task);
        } else {
            task.run();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {

        org.bukkit.entity.Entity entity = event.getEntity();

        StackManager stackManager = this.rosePlugin.getManager(StackManager.class);
        if (stackManager.isWorldDisabled(event.getEntity().getWorld()))
            return;

        PersistentDataUtils.tagSpawnedFromSpawner(entity);
        SpawnerStackSettings stackSettings = this.stackSettingManager.getSpawnerStackSettings(event.getSpawner());
        StackedSpawner stackedSpawner = this.stackManager.getStackedSpawner(event.getSpawner().getBlock());
        if (stackedSpawner == null)
            stackedSpawner = stackManager.createSpawnerStack(event.getSpawner().getBlock(), 1, false);

        boolean placedByPlayer = stackedSpawner != null && stackedSpawner.isPlacedByPlayer();
        if (stackSettings.isMobAIDisabled()
                && (!Setting.SPAWNER_DISABLE_MOB_AI_ONLY_PLAYER_PLACED.getBoolean() || placedByPlayer))
            PersistentDataUtils.removeEntityAi(entity);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        // Withers can still target enitites due to custom boss AI, so prevent them from
        // targeting when AI is disabled
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity))
            return;

        boolean disableAttacking = (event.getEntityType() == EntityType.WITHER
                && PersistentDataUtils.isAiDisabled((Wither) event.getEntity()))
                || (Setting.SPAWNER_DISABLE_ATTACKING.getBoolean())
                        && PersistentDataUtils.isSpawnedFromSpawner((org.bukkit.entity.Entity) event.getEntity());
        if (disableAttacking)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        // Endermen can still target enitites due to custom dodging AI, so prevent them
        // from teleporting when AI is disabled
        if (event.getEntityType() == EntityType.ENDERMAN
                && PersistentDataUtils.isAiDisabled((Enderman) event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTeleport(EntityPortalEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == event.getTo().getWorld())
            return;

        if (this.stackManager.isWorldDisabled(event.getEntity().getWorld()))
            return;

        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity livingEntity) {
            if (!this.stackManager.isEntityStackingEnabled())
                return;

            StackedEntity stackedEntity = this.stackManager.getStackedEntity(livingEntity);
            if (stackedEntity != null) {
                this.stackManager.changeStackingThread(livingEntity.getUniqueId(), stackedEntity,
                        event.getFrom().getWorld(), event.getTo().getWorld());
                stackedEntity.updateDisplay();
            }
        } else if (entity instanceof Item item) {
            if (!this.stackManager.isItemStackingEnabled())
                return;

            StackedItem stackedItem = this.stackManager.getStackedItem(item);
            if (stackedItem != null) {
                this.stackManager.changeStackingThread(item.getUniqueId(), stackedItem, event.getFrom().getWorld(),
                        event.getTo().getWorld());
                stackedItem.updateDisplay();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Prevent guardians with disabled AI from spiking their attacker
        if (event.getEntity().getType() == EntityType.PLAYER
                && (event.getDamager() instanceof Guardian || event.getDamager() instanceof Slime)
                && PersistentDataUtils.isAiDisabled((org.bukkit.entity.Entity) event.getDamager())) {
            event.setCancelled(true);
        }

        if (event.getEntity().getType() == EntityType.PLAYER)
            return;
        org.bukkit.entity.Entity entity = event.getEntity();
        if (!Setting.ENTITY_INSTANT_KILL_DISABLED_AI.getBoolean()
                || this.stackManager.isWorldDisabled(entity.getWorld()) || !PersistentDataUtils.isAiDisabled(entity))
            return;

        Entity damager = event.getDamager();
        if ((damager instanceof Projectile projectile && !(projectile.getShooter() instanceof Player))
                || !(damager instanceof Player))
            return;
        if (entity instanceof LivingEntity living) {
            AttributeInstance attributeInstance = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attributeInstance != null) {
                event.setDamage(attributeInstance.getValue() * 2);
            } else {
                event.setDamage(living.getHealth() * 2);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();
        if (event.getEntity().getType() == EntityType.ARMOR_STAND
                || event.getEntity().getType() == EntityType.PLAYER)
            return;

        if (this.stackManager.isWorldDisabled(entity.getWorld()))
            return;

        if (!this.stackManager.isEntityStackingEnabled())
            return;

        StackedEntity stackedEntity = this.stackManager.getStackedEntity(entity);
        if (stackedEntity == null || stackedEntity.getStackSize() == 1)
            return;

        if (!Setting.ENTITY_SHARE_DAMAGE_CONDITIONS.getStringList().contains(event.getCause().name()))
            return;

        double damage = event.getFinalDamage();

        List<Entity> killedEntities = stackedEntity.getDataStorage().removeIf(internal -> {
            if (internal instanceof LivingEntity living) {
                if (living.getHealth() - damage <= 0) {
                    return true; // Don't set the health below 0, as that will trigger the death event which we
                                 // want to avoid
                } else {
                    living.setHealth(living.getHealth() - damage);
                    return false;
                }
            }
            return true;
        });

        // Only try dropping loot if something actually died
        if (!killedEntities.isEmpty()) {
            stackedEntity.dropPartialStackLoot(killedEntities, 1, new ArrayList<>(), EntityUtils
                    .getApproximateExperience(stackedEntity.getStackSettings().getEntityType().getEntityClass()));

            if (entity instanceof LivingEntity living) {
                Player killer = living.getKiller();
                if (killer != null && killedEntities.size() - 1 > 0)
                    killer.incrementStatistic(Statistic.KILL_ENTITY, entity.getType(), killedEntities.size() - 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent
                || !(entity instanceof LivingEntity))
            return;

        // Don't allow mobs to naturally burn in the daylight if their AI is disabled
        if (PersistentDataUtils.isAiDisabled((org.bukkit.entity.Entity) entity)
                && !Setting.SPAWNER_DISABLE_MOB_AI_OPTIONS_UNDEAD_BURN_IN_DAYLIGHT.getBoolean())
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof LivingEntity living)
            this.handleEntityDeath(null, living);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event instanceof AsyncEntityDeathEvent))
            this.handleEntityDeath(event, event.getEntity());
    }

    private void handleEntityDeath(EntityDeathEvent event, org.bukkit.entity.Entity entity) {
        if (this.stackManager.isWorldDisabled(entity.getWorld()))
            return;

        if (!this.stackManager.isEntityStackingEnabled())
            return;

        StackedEntity stackedEntity = this.stackManager.getStackedEntity(entity);
        if (stackedEntity == null)
            return;

        if (stackedEntity.getStackSize() == 1) {
            this.stackManager.removeEntityStack(stackedEntity);
            return;
        }

        // Should we kill the entire stack at once?
        if (event != null && stackedEntity.isEntireStackKilledOnDeath()) {
            stackedEntity.killEntireStack(event);
            return;
        }

        Vector previousVelocity = entity.getVelocity().clone();
        Runnable task = () -> {
            // Should we kill multiple entities?
            if (Setting.ENTITY_MULTIKILL_ENABLED.getBoolean()) {
                int multikillAmount = Setting.ENTITY_MULTIKILL_AMOUNT.getInt();
                int killAmount = 1;
                LivingEntity living=null;
                if (entity instanceof LivingEntity living_)
                    living = living_;
                if (!Setting.ENTITY_MULTIKILL_PLAYER_ONLY.getBoolean()
                        || (living != null && living.getKiller() != null)) {
                    if (Setting.ENTITY_MULTIKILL_ENCHANTMENT_ENABLED.getBoolean()) {
                        Enchantment requiredEnchantment = Enchantment.getByKey(
                                NamespacedKey.fromString(Setting.ENTITY_MULTIKILL_ENCHANTMENT_TYPE.getString()));
                        if (requiredEnchantment == null) {
                            // Only decrease stack size by 1 and print a warning to the console
                            RoseStacker.getInstance().getLogger().warning("Invalid multikill enchantment type: "
                                    + Setting.ENTITY_MULTIKILL_ENCHANTMENT_TYPE.getString());
                        } else if (event != null && event.getEntity().getKiller() != null) {
                            Player killer = event.getEntity().getKiller();
                            int enchantmentLevel = killer.getInventory().getItemInMainHand()
                                    .getEnchantmentLevel(requiredEnchantment);
                            if (enchantmentLevel > 0)
                                killAmount = multikillAmount * enchantmentLevel;
                        }
                    } else {
                        killAmount = multikillAmount;
                    }
                }

                stackedEntity.killPartialStack(event, killAmount);
            } else {
                // Decrease stack size by 1
                stackedEntity.decreaseStackSize();
            }

            stackedEntity.getEntity().setVelocity(new Vector());

            if (Setting.ENTITY_KILL_TRANSFER_VELOCITY.getBoolean())
                stackedEntity.getEntity().setVelocity(previousVelocity);
        };

        if (Setting.ENTITY_KILL_DELAY_NEXT_SPAWN.getBoolean()) {
            ThreadUtils.runSync(task);
        } else {
            task.run();
        }

        if (Setting.ENTITY_KILL_TRANSFER_VELOCITY.getBoolean())
            entity.setVelocity(new Vector());

        if (!Setting.ENTITY_DISPLAY_CORPSE.getBoolean())
            entity.remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        this.handleEntityTransformation(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPigZap(PigZapEvent event) {
        this.handleEntityTransformation(event);
    }

    private void handleEntityTransformation(EntityTransformEvent event) {
        if (this.stackManager.isWorldDisabled(event.getEntity().getWorld()))
            return;

        if (!this.stackManager.isEntityStackingEnabled())
            return;

        if (event.getEntity() instanceof Slime) {
            if (PersistentDataUtils.isAiDisabled((org.bukkit.entity.Entity) event.getEntity()))
                event.getTransformedEntities().stream().map(x -> (Slime) x)
                        .forEach(PersistentDataUtils::removeEntityAi);
            if (PersistentDataUtils.isSpawnedFromSpawner((org.bukkit.entity.Entity) event.getEntity()))
                event.getTransformedEntities().stream().map(x -> (Slime) x)
                        .forEach(PersistentDataUtils::tagSpawnedFromSpawner);
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity)
                || !(event.getTransformedEntity() instanceof LivingEntity transformedEntity)
                || event.getEntity().getType() == event.getTransformedEntity().getType()
                || !this.stackManager.isEntityStacked((org.bukkit.entity.Entity) event.getEntity()))
            return;

        StackedEntity stackedEntity = this.stackManager.getStackedEntity((org.bukkit.entity.Entity) event.getEntity());
        if (stackedEntity.getStackSize() == 1)
            return;

        if (Setting.ENTITY_TRANSFORM_ENTIRE_STACK.getBoolean()) {
            NMSHandler nmsHandler = NMSAdapter.getHandler();
            StackedEntityDataEntry<?> serialized = nmsHandler.getEntityAsNBT(transformedEntity);
            event.setCancelled(true);

            // Handle mooshroom shearing
            if (event.getEntityType() == EntityType.MUSHROOM_COW) {
                MushroomCowStackSettings stackSettings = (MushroomCowStackSettings) stackedEntity.getStackSettings();
                int mushroomsDropped = 5;
                if (stackSettings.shouldDropAdditionalMushroomsPerCowInStack())
                    mushroomsDropped += (stackedEntity.getStackSize() - 1)
                            * stackSettings.getExtraMushroomsPerCowInStack();

                Material dropType = ((MushroomCow) event.getEntity()).getVariant() == Variant.BROWN
                        ? Material.BROWN_MUSHROOM
                        : Material.RED_MUSHROOM;
                this.stackManager.preStackItems(GuiUtil.getMaterialAmountAsItemStacks(dropType, mushroomsDropped),
                        event.getEntity().getLocation());
            }

            boolean aiDisabled = PersistentDataUtils.isAiDisabled((org.bukkit.entity.Entity) event.getEntity());
            event.getEntity().remove();
            ThreadUtils.runSync(() -> {
                this.stackManager.setEntityStackingTemporarilyDisabled(true);
                org.bukkit.entity.Entity newEntity = nmsHandler.createEntityFromNBT(serialized,
                        transformedEntity.getLocation(), true, transformedEntity.getType());
                if (aiDisabled)
                    PersistentDataUtils.removeEntityAi(newEntity);
                StackedEntity newStack = this.stackManager.createEntityStack(newEntity, false);
                this.stackManager.setEntityStackingTemporarilyDisabled(false);
                if (newStack == null)
                    return;

                stackedEntity.getDataStorage().forEach(entity -> {
                    if (aiDisabled)
                        PersistentDataUtils.removeEntityAi(entity);
                    newStack.increaseStackSize(entity, false);
                });
                newStack.updateDisplay();
            });
        } else {
            // Make sure disabled AI gets transferred
            if (PersistentDataUtils.isAiDisabled((org.bukkit.entity.Entity) event.getEntity()))
                PersistentDataUtils.removeEntityAi((org.bukkit.entity.Entity) event.getTransformedEntity());

            if (event.getTransformReason() == TransformReason.LIGHTNING) { // Wait for lightning to disappear
                ThreadUtils.runSyncDelayed(stackedEntity::decreaseStackSize, 20);
            } else {
                ThreadUtils.runSync(stackedEntity::decreaseStackSize);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChickenLayEgg(EntityDropItemEvent event) {
        if (event.getEntityType() != EntityType.CHICKEN || event.getItemDrop().getItemStack().getType() != Material.EGG)
            return;

        if (this.stackManager.isWorldDisabled(event.getEntity().getWorld()))
            return;

        if (!this.stackManager.isEntityStackingEnabled())
            return;

        Chicken chickenEntity = (Chicken) event.getEntity();
        StackedEntity stackedEntity = this.stackManager.getStackedEntity(chickenEntity);
        if (stackedEntity == null || stackedEntity.getStackSize() == 1)
            return;

        ChickenStackSettings chickenStackSettings = (ChickenStackSettings) stackedEntity.getStackSettings();
        if (!chickenStackSettings.shouldMultiplyEggDropsByStackSize())
            return;

        event.getItemDrop().remove();
        List<ItemStack> items = GuiUtil.getMaterialAmountAsItemStacks(Material.EGG, stackedEntity.getStackSize());
        this.stackManager.preStackItems(items, event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerShearSheep(PlayerShearEntityEvent event) {
        handleSheepShear(this.rosePlugin, event.getEntity());
    }

    public static boolean handleSheepShear(RosePlugin rosePlugin, Entity entity) {
        if (entity.getType() != EntityType.SHEEP)
            return false;

        StackManager stackManager = rosePlugin.getManager(StackManager.class);
        if (stackManager.isWorldDisabled(entity.getWorld()))
            return false;

        if (!stackManager.isEntityStackingEnabled())
            return false;

        Sheep sheepEntity = (Sheep) entity;
        StackedEntity stackedEntity = stackManager.getStackedEntity(sheepEntity);
        if (stackedEntity == null)
            return false;

        SheepStackSettings sheepStackSettings = (SheepStackSettings) stackedEntity.getStackSettings();
        if (!sheepStackSettings.shouldShearAllSheepInStack()) {
            ThreadUtils.runSync(() -> {
                if (!stackedEntity.shouldStayStacked() && stackedEntity.getStackSize() > 1)
                    stackManager.splitEntityStack(stackedEntity);
            });
            return false;
        }

        List<ItemStack> drops = new ArrayList<>();
        stackManager.setEntityUnstackingTemporarilyDisabled(true);
        ThreadUtils.runAsync(() -> {
            try {
                stackedEntity.getDataStorage().forEach(internal -> {
                    Sheep sheep = (Sheep) internal;
                    if (!sheep.isSheared()
                            || stackManager.getEntityDataStorageType() == StackedEntityDataStorageType.SIMPLE) {
                        sheep.setSheared(true);
                        drops.add(new ItemStack(ItemUtils.getWoolMaterial(sheep.getColor()), getWoolDropAmount()));
                    }
                });

                ThreadUtils.runSync(() -> stackManager.preStackItems(drops, sheepEntity.getLocation()));
            } finally {
                stackManager.setEntityUnstackingTemporarilyDisabled(false);
            }
        });

        return true;
    }

    /**
     * @return a number between 1 and 3 inclusively
     */
    private static int getWoolDropAmount() {
        return (int) (Math.random() * 3) + 1;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSheepRegrowWool(SheepRegrowWoolEvent event) {
        if (this.stackManager.isWorldDisabled(event.getEntity().getWorld()))
            return;

        if (!this.stackManager.isEntityStackingEnabled())
            return;

        Sheep sheepEntity = event.getEntity();
        StackedEntity stackedEntity = this.stackManager.getStackedEntity(sheepEntity);
        if (stackedEntity == null || stackedEntity.getStackSize() == 1)
            return;

        SheepStackSettings sheepStackSettings = (SheepStackSettings) stackedEntity.getStackSettings();
        double regrowPercentage = sheepStackSettings.getPercentageOfWoolToRegrowPerGrassEaten() / 100D;
        int regrowAmount = Math.max(1, (int) Math.round(stackedEntity.getStackSize() * regrowPercentage));

        if (sheepEntity.isSheared()) {
            sheepEntity.setSheared(false);
            regrowAmount--;
        }

        if (regrowAmount < 1)
            return;

        AtomicInteger regrowRemaining = new AtomicInteger(regrowAmount);
        ThreadUtils.runAsync(() -> stackedEntity.getDataStorage().forEach(internal -> {
            Sheep sheep = (Sheep) internal;
            if (!sheep.isSheared() && regrowRemaining.getAndDecrement() > 0)
                sheep.setSheared(true);
        }));
    }

}
