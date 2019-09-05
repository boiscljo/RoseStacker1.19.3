package dev.esophose.rosestacker.stack.settings.entity;

import dev.esophose.rosestacker.stack.StackedEntity;
import dev.esophose.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;

public class PigStackSettings extends EntityStackSettings {

    private boolean dontStackIfSaddled;

    public PigStackSettings(YamlConfiguration entitySettingsConfiguration) {
        super(entitySettingsConfiguration);

        this.dontStackIfSaddled = entitySettingsConfiguration.getBoolean("dont-stack-if-saddled");
    }

    @Override
    protected boolean canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        Pig pig1 = (Pig) stack1.getEntity();
        Pig pig2 = (Pig) stack2.getEntity();

        return !this.dontStackIfSaddled || (!pig1.hasSaddle() && !pig2.hasSaddle());
    }

    @Override
    protected void setDefaultsInternal() {
        this.setIfNotExists("dont-stack-if-saddled", false);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.PIG;
    }

}
