package dev.rosewood.rosestacker.stack.settings.entity;

import com.google.gson.JsonObject;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosestacker.stack.EntityStackComparisonResult;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Piglin;

public class PiglinStackSettings extends EntityStackSettings {

    private final boolean dontStackIfConverting;
    private final boolean dontStackIfUnableToHunt;
    private final boolean dontStackIfImmuneToZombification;

    public PiglinStackSettings(CommentedFileConfiguration entitySettingsFileConfiguration, JsonObject jsonObject) {
        super(entitySettingsFileConfiguration, jsonObject);

        this.dontStackIfConverting = this.settingsConfiguration.getBoolean("dont-stack-if-converting");
        this.dontStackIfUnableToHunt = this.settingsConfiguration.getBoolean("dont-stack-if-unable-to-hunt");
        this.dontStackIfImmuneToZombification = this.settingsConfiguration.getBoolean("dont-stack-if-immune-to-zombification");
    }

    @Override
    protected EntityStackComparisonResult canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        Piglin piglin1 = (Piglin) stack1.getEntity();
        Piglin piglin2 = (Piglin) stack2.getEntity();

        if (this.dontStackIfConverting && (piglin1.isConverting() || piglin2.isConverting()))
            return EntityStackComparisonResult.CONVERTING;

        if (this.dontStackIfUnableToHunt && (!piglin1.isAbleToHunt() || !piglin2.isAbleToHunt()))
            return EntityStackComparisonResult.UNABLE_TO_HUNT;

        if (this.dontStackIfImmuneToZombification && (piglin1.isImmuneToZombification() || piglin2.isImmuneToZombification()))
            return EntityStackComparisonResult.IMMUNE_TO_ZOMBIFICATION;

        return EntityStackComparisonResult.CAN_STACK;
    }

    @Override
    protected void setDefaultsInternal() {
        this.setIfNotExists("dont-stack-if-converting", false);
        this.setIfNotExists("dont-stack-if-unable-to-hunt", false);
        this.setIfNotExists("dont-stack-if-immune-to-zombification", false);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.PIGLIN;
    }

}
