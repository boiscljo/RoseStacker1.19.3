package dev.rosewood.rosestacker.stack.settings.entity;

import com.google.gson.JsonObject;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosestacker.stack.EntityStackComparisonResult;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.json.simple.JSONObject;

public class BaseStackSettings extends EntityStackSettings {

    private EntityType entityType;

    public BaseStackSettings(CommentedFileConfiguration entitySettingsFileConfiguration, JsonObject j, EntityType t) {
        super(entitySettingsFileConfiguration,j , t);
        this.entityType = t;
    }

    @Override
    protected EntityStackComparisonResult canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        return EntityStackComparisonResult.CAN_STACK;
    }

    @Override
    protected void setDefaultsInternal() {
        
    }

    @Override
    public EntityType getEntityType() {
        return entityType;
    }

}
