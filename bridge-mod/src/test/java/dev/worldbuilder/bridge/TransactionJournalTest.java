package dev.worldbuilder.bridge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionJournalTest {
    @Test void roundTripsOwnedSavedData(){TransactionJournal journal=new TransactionJournal();ListTag list=new ListTag();CompoundTag tx=new CompoundTag();tx.putString("id","test");list.add(tx);journal.replace(list);CompoundTag saved=journal.save(new CompoundTag());TransactionJournal loaded=new TransactionJournal(saved);assertEquals(1,loaded.entries().size());assertEquals("test",loaded.entries().getCompound(0).getString("id"));}
}
