package dev.worldbuilder.bridge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

public final class TransactionJournal extends SavedData {
    private ListTag entries=new ListTag();
    public TransactionJournal(){}
    public TransactionJournal(CompoundTag tag){entries=tag.getList("transactions",Tag.TAG_COMPOUND).copy();}
    public ListTag entries(){return entries.copy();}
    public void replace(ListTag value){entries=value.copy();setDirty();}
    @Override public CompoundTag save(CompoundTag tag){tag.put("transactions",entries.copy());return tag;}
}
