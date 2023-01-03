package dev.rosewood.rosestacker.conversion.handler;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.conversion.ConversionData;
import dev.rosewood.rosestacker.stack.Stack;
import dev.rosewood.rosestacker.stack.StackType;
import dev.rosewood.rosestacker.stack.StackedEntity;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.LivingEntity;

public class EntityConversionHandler extends ConversionHandler {

    public EntityConversionHandler(RosePlugin rosePlugin) {
        super(rosePlugin, StackType.ENTITY);
    }

    @Override
    public Set<Stack<?>> handleConversion(Set<ConversionData> conversionData) {
        Set<Stack<?>> stacks = new HashSet<>();

        for (ConversionData data : conversionData) {
            org.bukkit.entity.Entity entity = data.getEntity();
            entity.setCustomName(null); // This could cause data loss if the entity actually has a custom name, but we have to remove the stack tag
            entity.setCustomNameVisible(false);

            StackedEntity stackedEntity = new StackedEntity(data.getEntity(), this.createEntityStackNBT(entity.getType(), data.getStackSize(), entity.getLocation()));
            this.stackManager.addEntityStack(stackedEntity);
            stacks.add(stackedEntity);
        }

        return stacks;
    }

}
