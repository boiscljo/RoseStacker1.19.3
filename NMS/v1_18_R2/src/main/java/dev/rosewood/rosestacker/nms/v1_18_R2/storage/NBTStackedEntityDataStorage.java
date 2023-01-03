package dev.rosewood.rosestacker.nms.v1_18_R2.storage;

import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataEntry;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataIOException;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataStorage;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataStorageType;
import dev.rosewood.rosestacker.nms.v1_18_R2.NMSHandlerImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.bukkit.entity.LivingEntity;

public class NBTStackedEntityDataStorage extends StackedEntityDataStorage {

    private final CompoundTag base;
    private final List<CompoundTag> data;

    public NBTStackedEntityDataStorage(org.bukkit.entity.Entity livingEntity) {
        super(StackedEntityDataStorageType.NBT, livingEntity);
        this.base = new CompoundTag();

        ((NMSHandlerImpl) NMSAdapter.getHandler()).saveEntityToTag(livingEntity, this.base);
        this.stripUnneeded(this.base);
        this.stripAttributeUuids(this.base);

        this.data = Collections.synchronizedList(new LinkedList<>());
    }

    public NBTStackedEntityDataStorage(org.bukkit.entity.Entity livingEntity, byte[] data) {
        super(StackedEntityDataStorageType.NBT, livingEntity);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

            this.base = NbtIo.read(dataInput);
            int length = dataInput.readInt();
            List<CompoundTag> tags = new LinkedList<>();
            for (int i = 0; i < length; i++)
                tags.add(NbtIo.read(dataInput));
            this.data = Collections.synchronizedList(tags);
        } catch (Exception e) {
            throw new StackedEntityDataIOException(e);
        }
    }

    @Override
    public void addFirst(org.bukkit.entity.Entity entity) {
        this.addAt(0, entity);
    }

    @Override
    public void addLast(org.bukkit.entity.Entity entity) {
        this.addAt(this.data.size(), entity);
    }

    @Override
    public void addAllFirst(List<StackedEntityDataEntry<?>> stackedEntityDataEntry) {
        stackedEntityDataEntry.forEach(x -> this.addAt(0, x));
    }

    @Override
    public void addAllLast(List<StackedEntityDataEntry<?>> stackedEntityDataEntry) {
        stackedEntityDataEntry.forEach(x -> this.addAt(this.data.size(), x));
    }

    @Override
    public void addClones(int amount) {
        for (int i = 0; i < amount; i++)
            this.data.add(this.base.copy());
    }

    @Override
    public NBTStackedEntityDataEntry peek() {
        return new NBTStackedEntityDataEntry(this.rebuild(this.data.get(0)));
    }

    @Override
    public NBTStackedEntityDataEntry pop() {
        return new NBTStackedEntityDataEntry(this.rebuild(this.data.remove(0)));
    }

    @Override
    public List<StackedEntityDataEntry<?>> pop(int amount) {
        amount = Math.min(amount, this.data.size());

        List<StackedEntityDataEntry<?>> popped = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++)
            popped.add(new NBTStackedEntityDataEntry(this.rebuild(this.data.remove(0))));
        return popped;
    }

    @Override
    public int size() {
        return this.data.size();
    }

    @Override
    public boolean isEmpty() {
        return this.data.isEmpty();
    }

    @Override
    public List<StackedEntityDataEntry<?>> getAll() {
        List<StackedEntityDataEntry<?>> wrapped = new ArrayList<>(this.data.size());
        for (CompoundTag compoundTag : new ArrayList<>(this.data))
            wrapped.add(new NBTStackedEntityDataEntry(this.rebuild(compoundTag)));
        return wrapped;
    }

    @Override
    public byte[] serialize(int maxAmount) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream)) {

            int targetAmount = Math.min(maxAmount, this.data.size());
            List<CompoundTag> tagsToSave = new ArrayList<>(targetAmount);
            Iterator<CompoundTag> iterator = this.data.iterator();
            for (int i = 0; i < targetAmount; i++)
                tagsToSave.add(iterator.next());

            NbtIo.write(this.base, dataOutput);
            dataOutput.writeInt(tagsToSave.size());
            for (CompoundTag compoundTag : tagsToSave)
                NbtIo.write(compoundTag, dataOutput);

            dataOutput.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new StackedEntityDataIOException(e);
        }
    }

    @Override
    public void forEach(Consumer<org.bukkit.entity.Entity> consumer) {
        this.forEachCapped(Integer.MAX_VALUE, consumer);
    }

    @Override
    public void forEachCapped(int count, Consumer<org.bukkit.entity.Entity> consumer) {
        if (count > this.data.size())
            count = this.data.size();

        NMSHandler nmsHandler = NMSAdapter.getHandler();
        org.bukkit.entity.Entity thisEntity = this.entity.get();
        if (thisEntity == null)
            return;

        Iterator<CompoundTag> iterator = this.data.iterator();
        for (int i = 0; i < count; i++) {
            CompoundTag compoundTag = iterator.next();
            org.bukkit.entity.Entity entity = nmsHandler.createEntityFromNBT(new NBTStackedEntityDataEntry(this.rebuild(compoundTag)), thisEntity.getLocation(), false, thisEntity.getType());
            consumer.accept(entity);
        }
    }

    @Override
    public List<org.bukkit.entity.Entity> removeIf(Function<org.bukkit.entity.Entity, Boolean> function) {
        List<org.bukkit.entity.Entity> removedEntries = new ArrayList<>(this.data.size());
        org.bukkit.entity.Entity thisEntity = this.entity.get();
        if (thisEntity == null)
            return removedEntries;

        NMSHandler nmsHandler = NMSAdapter.getHandler();
        this.data.removeIf(x -> {
            org.bukkit.entity.Entity entity = nmsHandler.createEntityFromNBT(new NBTStackedEntityDataEntry(this.rebuild(x)), thisEntity.getLocation(), false, thisEntity.getType());
            boolean removed = function.apply(entity);
            if (removed) removedEntries.add(entity);
            return removed;
        });
        return removedEntries;
    }

    private void addAt(int index, org.bukkit.entity.Entity livingEntity) {
        CompoundTag compoundTag = new CompoundTag();
        ((NMSHandlerImpl) NMSAdapter.getHandler()).saveEntityToTag(livingEntity, compoundTag);
        this.stripUnneeded(compoundTag);
        this.stripAttributeUuids(compoundTag);
        this.removeDuplicates(compoundTag);
        this.data.add(index, compoundTag);
    }

    private void addAt(int index, StackedEntityDataEntry<?> stackedEntityDataEntry) {
        CompoundTag compoundTag = (CompoundTag) stackedEntityDataEntry.get();
        this.stripUnneeded(compoundTag);
        this.stripAttributeUuids(compoundTag);
        this.removeDuplicates(compoundTag);
        this.data.add(index, compoundTag);
    }

    private void removeDuplicates(CompoundTag compoundTag) {
        for (String key : new ArrayList<>(compoundTag.getAllKeys())) {
            Tag baseValue = this.base.get(key);
            Tag thisValue = compoundTag.get(key);
            if (baseValue != null && baseValue.equals(thisValue))
                compoundTag.remove(key);
        }
    }

    private CompoundTag rebuild(CompoundTag compoundTag) {
        CompoundTag merged = new CompoundTag();
        merged.merge(this.base);
        merged.merge(compoundTag);
        this.fillAttributeUuids(merged);
        return merged;
    }

    private void stripUnneeded(CompoundTag compoundTag) {
        compoundTag.remove("UUID");
        compoundTag.remove("Pos");
        compoundTag.remove("Rotation");
        compoundTag.remove("WorldUUIDMost");
        compoundTag.remove("WorldUUIDLeast");
        compoundTag.remove("Motion");
        compoundTag.remove("OnGround");
        compoundTag.remove("FallDistance");
        compoundTag.remove("Leash");
        compoundTag.remove("Spigot.ticksLived");
        compoundTag.remove("Paper.OriginWorld");
        compoundTag.remove("Paper.Origin");
        CompoundTag bukkitValues = compoundTag.getCompound("BukkitValues");
        bukkitValues.remove("rosestacker:stacked_entity_data");
    }

    private void stripAttributeUuids(CompoundTag compoundTag) {
        ListTag attributes = compoundTag.getList("Attributes", Tag.TAG_COMPOUND);
        for (int i = 0; i < attributes.size(); i++) {
            CompoundTag attribute = attributes.getCompound(i);
            attribute.remove("UUID");
            ListTag modifiers = attribute.getList("Modifiers", Tag.TAG_COMPOUND);
            for (int j = 0; j < modifiers.size(); j++) {
                CompoundTag modifier = modifiers.getCompound(j);
                if (modifier.getString("Name").equals("Random spawn bonus")) {
                    modifiers.remove(j);
                    j--;
                } else {
                    modifier.remove("UUID");
                }
            }
        }
    }

    private void fillAttributeUuids(CompoundTag compoundTag) {
        ListTag attributes = compoundTag.getList("Attributes", Tag.TAG_COMPOUND);
        for (int i = 0; i < attributes.size(); i++) {
            CompoundTag attribute = attributes.getCompound(i);
            attribute.putUUID("UUID", UUID.randomUUID());
            ListTag modifiers = attribute.getList("Modifiers", Tag.TAG_COMPOUND);
            for (int j = 0; j < modifiers.size(); j++) {
                CompoundTag modifier = modifiers.getCompound(j);
                modifier.putUUID("UUID", UUID.randomUUID());
            }
            if (modifiers.size() == 0)
                attribute.remove("Modifiers");
        }
    }

}
