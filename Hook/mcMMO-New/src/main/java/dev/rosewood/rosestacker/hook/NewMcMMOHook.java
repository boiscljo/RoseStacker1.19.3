package dev.rosewood.rosestacker.hook;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.metadata.MobMetaFlagType;
import org.bukkit.entity.LivingEntity;

public class NewMcMMOHook implements McMMOHook {

    @Override
    public void flagSpawnerMetadata(org.bukkit.entity.Entity entity) {
        if (entity instanceof LivingEntity living)
            mcMMO.getMetadataService().getMobMetadataService().flagMetadata(MobMetaFlagType.MOB_SPAWNER_MOB, living);
    }

}
