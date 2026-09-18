package dev.worldbuilder.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.time.Instant;
import java.util.*;

public final class TransactionManager {
    public enum Status { IN_PROGRESS, COMMITTED, CANCELLED, UNDONE }
    public record Snapshot(BlockPos position, BlockState state, CompoundTag blockEntityData) {}
    public record Change(Snapshot before, Snapshot after) {}
    public record RestoreAction(UUID transactionId,String dimension,String summary,List<BuildQueue.Placement> placements,Status targetStatus) {}
    public static final class Transaction {
        private final UUID id; private final UUID buildId; private final String dimension; private final String summary; private final String clientId; private final String referencePlayer; private final Instant timestamp; private Instant completedAt;
        private final List<Change> changes = new ArrayList<>(); private Status status = Status.IN_PROGRESS;
        Transaction(String dimension,String summary,String clientId,UUID buildId,String referencePlayer){this(UUID.randomUUID(),buildId,dimension,summary,clientId,referencePlayer,Instant.now(),null,Status.IN_PROGRESS);}
        Transaction(UUID id,UUID buildId,String dimension,String summary,String clientId,String referencePlayer,Instant timestamp,Instant completedAt,Status status){this.id=id;this.buildId=buildId;this.dimension=dimension;this.summary=summary;this.clientId=clientId;this.referencePlayer=referencePlayer;this.timestamp=timestamp;this.completedAt=completedAt;this.status=status;}
        public UUID id() { return id; } public String dimension() { return dimension; } public String summary() { return summary; }
        public Instant timestamp() { return timestamp; } public List<Change> changes() { return List.copyOf(changes); } public Status status() { return status; }
    }
    private final int historyLimit; private final LinkedHashMap<UUID, Transaction> history = new LinkedHashMap<>();private final MinecraftServer server;private final TransactionJournal journal;
    public TransactionManager(MinecraftServer server,int historyLimit) { this.server=server;this.historyLimit = historyLimit;this.journal=server.overworld().getDataStorage().computeIfAbsent(TransactionJournal::new,TransactionJournal::new,"ai_world_builder_transactions");load(); }
    public Transaction setBlock(ServerLevel level,BlockPos pos,BlockState state,String summary,String clientId,String referencePlayer){
        Transaction tx=begin(level.dimension().location().toString(),summary,clientId,UUID.randomUUID(),referencePlayer);apply(tx,level,pos,state,null);commit(tx);return tx;
    }
    public Transaction begin(String dimension,String summary,String clientId){return begin(dimension,summary,clientId,UUID.randomUUID(),null);}
    public Transaction begin(String dimension,String summary,String clientId,UUID buildId,String referencePlayer){return new Transaction(dimension,summary,clientId,buildId,referencePlayer);}
    public void apply(Transaction tx,ServerLevel level,BlockPos pos,BlockState state,CompoundTag blockEntityData){if(tx.status!=Status.IN_PROGRESS)throw new BridgeOperationException("TRANSACTION_FAILED","Transaction is not active");Snapshot before=capture(level,pos);boolean stateChanged=!before.state.equals(state);if(!stateChanged&&blockEntityData==null)return;if(stateChanged&&!level.setBlock(pos,state,Block.UPDATE_ALL))throw new BridgeOperationException("TRANSACTION_FAILED","Minecraft rejected block change at "+pos.toShortString());if(blockEntityData!=null){BlockEntity entity=level.getBlockEntity(pos);if(entity!=null){CompoundTag moved=blockEntityData.copy();moved.putInt("x",pos.getX());moved.putInt("y",pos.getY());moved.putInt("z",pos.getZ());entity.load(moved);entity.setChanged();}}tx.changes.add(new Change(before,capture(level,pos)));}
    public void commit(Transaction tx) { tx.status=Status.COMMITTED; tx.completedAt=Instant.now(); store(tx); }
    public void cancel(Transaction tx){tx.status=Status.CANCELLED; tx.completedAt=Instant.now(); store(tx);}
    public void checkpoint(Transaction tx){history.put(tx.id,tx);persist();}
    public void discard(Transaction tx){history.remove(tx.id);persist();}
    public void rollback(Transaction tx,ServerLevel level){List<Change> reversed=new ArrayList<>(tx.changes);Collections.reverse(reversed);reversed.forEach(c->restore(level,c.before));}
    private void store(Transaction tx){history.put(tx.id,tx);while(history.size()>historyLimit)history.remove(history.keySet().iterator().next());persist();}
    public Transaction undo(UUID id, WorldAccess world) { Transaction tx = require(id); Status target = TransactionLifecycle.afterUndo(tx.status);
        ServerLevel level = world.level(tx.dimension); List<Change> reversed = new ArrayList<>(tx.changes); Collections.reverse(reversed); reversed.forEach(c -> restore(level, c.before)); tx.status = target;persist(); return tx; }
    public Transaction redo(UUID id, WorldAccess world) { Transaction tx = require(id); Status target = TransactionLifecycle.afterRedo(tx.status);
        ServerLevel level = world.level(tx.dimension); tx.changes.forEach(c -> restore(level, c.after)); tx.status = target;persist(); return tx; }
    public RestoreAction prepareUndo(UUID id){Transaction tx=require(id);Status target=TransactionLifecycle.afterUndo(tx.status);List<Change> reversed=new ArrayList<>(tx.changes);Collections.reverse(reversed);return new RestoreAction(id,tx.dimension,"Undo "+tx.summary,reversed.stream().map(c->new BuildQueue.Placement(c.before.position,c.before.state,c.before.blockEntityData)).toList(),target);}
    public RestoreAction prepareRedo(UUID id){Transaction tx=require(id);Status target=TransactionLifecycle.afterRedo(tx.status);return new RestoreAction(id,tx.dimension,"Redo "+tx.summary,tx.changes.stream().map(c->new BuildQueue.Placement(c.after.position,c.after.state,c.after.blockEntityData)).toList(),target);}
    public void completeRestore(RestoreAction action){Transaction tx=require(action.transactionId);tx.status=action.targetStatus;tx.completedAt=Instant.now();persist();}
    private Transaction require(UUID id) { Transaction tx = history.get(id); if (tx == null) throw new BridgeOperationException("NOT_FOUND", "Transaction not found: " + id); return tx; }
    Snapshot capture(ServerLevel level, BlockPos pos) { BlockEntity entity = level.getBlockEntity(pos); return new Snapshot(pos.immutable(), level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata().copy()); }
    void restore(ServerLevel level, Snapshot snapshot) { if(!level.getBlockState(snapshot.position).equals(snapshot.state)&&!level.setBlock(snapshot.position, snapshot.state, Block.UPDATE_ALL))throw new BridgeOperationException("TRANSACTION_FAILED","Minecraft rejected restoration at "+snapshot.position.toShortString()); if (snapshot.blockEntityData != null) { BlockEntity entity = level.getBlockEntity(snapshot.position); if (entity != null) { entity.load(snapshot.blockEntityData.copy()); entity.setChanged(); } } }
    public JsonObject json(Transaction tx) { JsonObject out = new JsonObject(); out.addProperty("buildId",tx.buildId.toString());out.addProperty("transactionId", tx.id.toString()); out.addProperty("dimension", tx.dimension); out.addProperty("summary", tx.summary); out.addProperty("initiatingClient",tx.clientId);if(tx.referencePlayer!=null)out.addProperty("referencePlayer",tx.referencePlayer);
        out.addProperty("timestamp", tx.timestamp.toString()); out.addProperty("durationMs",java.time.Duration.between(tx.timestamp,tx.completedAt==null?Instant.now():tx.completedAt).toMillis()); out.addProperty("blockCount", tx.changes.size()); out.addProperty("status", tx.status.name().toLowerCase(Locale.ROOT));
        if(!tx.changes.isEmpty()){List<BlockPos> ps=tx.changes.stream().map(c->c.after.position).toList();Region box=new Region(tx.dimension,new BlockPos(ps.stream().mapToInt(BlockPos::getX).min().orElse(0),ps.stream().mapToInt(BlockPos::getY).min().orElse(0),ps.stream().mapToInt(BlockPos::getZ).min().orElse(0)),new BlockPos(ps.stream().mapToInt(BlockPos::getX).max().orElse(0),ps.stream().mapToInt(BlockPos::getY).max().orElse(0),ps.stream().mapToInt(BlockPos::getZ).max().orElse(0)));out.add("boundingBox",WorldAccess.regionJson(box));JsonObject materials=new JsonObject();Map<String,Integer> counts=new TreeMap<>();tx.changes.forEach(c->counts.merge(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(c.after.state.getBlock()).toString(),1,Integer::sum));counts.forEach(materials::addProperty);out.add("materials",materials);} return out; }
    public JsonArray historyJson() { JsonArray out = new JsonArray(); history.values().forEach(tx -> out.add(json(tx))); return out; }
    private void persist(){ListTag list=new ListTag();history.values().forEach(tx->list.add(write(tx)));journal.replace(list);}
    private CompoundTag write(Transaction tx){CompoundTag tag=new CompoundTag();tag.putString("id",tx.id.toString());tag.putString("buildId",tx.buildId.toString());tag.putString("dimension",tx.dimension);tag.putString("summary",tx.summary);tag.putString("clientId",tx.clientId);if(tx.referencePlayer!=null)tag.putString("referencePlayer",tx.referencePlayer);tag.putLong("timestamp",tx.timestamp.toEpochMilli());if(tx.completedAt!=null)tag.putLong("completedAt",tx.completedAt.toEpochMilli());tag.putString("status",tx.status.name());ListTag changes=new ListTag();for(Change c:tx.changes){CompoundTag v=new CompoundTag();v.put("position",NbtUtils.writeBlockPos(c.before.position));v.put("beforeState",NbtUtils.writeBlockState(c.before.state));v.put("afterState",NbtUtils.writeBlockState(c.after.state));if(c.before.blockEntityData!=null)v.put("beforeBlockEntity",c.before.blockEntityData.copy());if(c.after.blockEntityData!=null)v.put("afterBlockEntity",c.after.blockEntityData.copy());changes.add(v);}tag.put("changes",changes);return tag;}
    private void load(){var blocks=server.registryAccess().lookupOrThrow(Registries.BLOCK);for(Tag raw:journal.entries()){try{CompoundTag tag=(CompoundTag)raw;UUID id=UUID.fromString(tag.getString("id"));UUID buildId=tag.contains("buildId")?UUID.fromString(tag.getString("buildId")):id;Transaction tx=new Transaction(id,buildId,tag.getString("dimension"),tag.getString("summary"),tag.getString("clientId"),tag.contains("referencePlayer")?tag.getString("referencePlayer"):null,Instant.ofEpochMilli(tag.getLong("timestamp")),tag.contains("completedAt")?Instant.ofEpochMilli(tag.getLong("completedAt")):null,Status.valueOf(tag.getString("status")));ListTag changes=tag.getList("changes",Tag.TAG_COMPOUND);for(Tag cRaw:changes){CompoundTag c=(CompoundTag)cRaw;BlockPos p=NbtUtils.readBlockPos(c.getCompound("position"));Snapshot before=new Snapshot(p,NbtUtils.readBlockState(blocks,c.getCompound("beforeState")),c.contains("beforeBlockEntity")?c.getCompound("beforeBlockEntity"):null);Snapshot after=new Snapshot(p,NbtUtils.readBlockState(blocks,c.getCompound("afterState")),c.contains("afterBlockEntity")?c.getCompound("afterBlockEntity"):null);tx.changes.add(new Change(before,after));}if(tx.status==Status.IN_PROGRESS){tx.status=Status.CANCELLED;tx.completedAt=Instant.now();}history.put(tx.id,tx);}catch(RuntimeException e){AiWorldBuilderBridge.LOGGER.warn("Skipping invalid persisted transaction",e);}}persist();}
}
