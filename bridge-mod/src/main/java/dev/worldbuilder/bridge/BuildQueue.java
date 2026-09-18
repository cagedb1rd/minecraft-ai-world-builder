package dev.worldbuilder.bridge;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class BuildQueue {
    public record Placement(BlockPos position,BlockState state,CompoundTag blockEntityData){public Placement(BlockPos p,BlockState s){this(p,s,null);}}
    public enum Status{QUEUED,RUNNING,COMPLETE,FAILED,CANCELLED}
    private static final class Job{
        final UUID id;final String dimension,summary,clientId;final List<Placement> placements;final TransactionManager.Transaction tx;final boolean persistTransaction;final UUID outputTransactionId;final Runnable onSuccess;int cursor;Status status=Status.QUEUED;String error;
        Job(UUID id,String d,String s,String c,List<Placement> p,TransactionManager.Transaction tx,boolean persist,UUID outputId,Runnable success){this.id=id;dimension=d;summary=s;clientId=c;placements=p.stream().sorted(Comparator.comparingInt((Placement v)->v.position().getX()>>4).thenComparingInt(v->v.position().getZ()>>4).thenComparingInt(v->v.position().getY())).toList();this.tx=tx;persistTransaction=persist;outputTransactionId=outputId;onSuccess=success;}
    }
    private final WorldAccess world;private final TransactionManager transactions;private final LinkedHashMap<UUID,Job> jobs=new LinkedHashMap<>();private final ArrayDeque<Job> pending=new ArrayDeque<>();private Job active;
    public BuildQueue(WorldAccess world,TransactionManager transactions){this.world=world;this.transactions=transactions;}
    public JsonObject enqueue(String dimension,String summary,String clientId,String referencePlayer,List<Placement> placements){if(placements.isEmpty())throw new BridgeOperationException("MALFORMED_REQUEST","Build has no block changes");UUID buildId=UUID.randomUUID();var tx=transactions.begin(dimension,summary,clientId,buildId,referencePlayer);return add(new Job(buildId,dimension,summary,clientId,List.copyOf(placements),tx,true,tx.id(),()->{}));}
    public JsonObject enqueueRestore(TransactionManager.RestoreAction action,String clientId){if(action.placements().isEmpty())throw new BridgeOperationException("TRANSACTION_FAILED","Transaction has no changes");UUID buildId=UUID.randomUUID();var temporary=transactions.begin(action.dimension(),action.summary(),clientId,buildId,null);return add(new Job(buildId,action.dimension(),action.summary(),clientId,action.placements(),temporary,false,action.transactionId(),()->transactions.completeRestore(action)));}
    private JsonObject add(Job job){jobs.put(job.id,job);pending.add(job);trim();AiWorldBuilderBridge.LOGGER.info("Build queued {} blocks={} client={}",job.id,job.placements.size(),job.clientId);return json(job);}
    public void tick(){if(active==null){active=pending.poll();if(active==null)return;active.status=Status.RUNNING;AiWorldBuilderBridge.LOGGER.info("Build started {}",active.id);}Job job=active;ServerLevel level=world.level(job.dimension);int budget=BridgeConfig.BLOCKS_PER_TICK.get();try{while(budget-->0&&job.cursor<job.placements.size()){Placement p=job.placements.get(job.cursor++);transactions.apply(job.tx,level,p.position,p.state,p.blockEntityData);}if(job.persistTransaction)transactions.checkpoint(job.tx);if(job.cursor>=job.placements.size()){if(job.persistTransaction)transactions.commit(job.tx);job.onSuccess.run();job.status=Status.COMPLETE;AiWorldBuilderBridge.LOGGER.info("Build completed {} blocks={}",job.id,job.cursor);active=null;}}catch(RuntimeException e){transactions.rollback(job.tx,level);if(job.persistTransaction)transactions.discard(job.tx);job.status=Status.FAILED;job.error=e.getMessage();AiWorldBuilderBridge.LOGGER.error("Build failed {}",job.id,e);active=null;}}
    public JsonObject status(UUID id){Job j=jobs.get(id);if(j==null)throw new BridgeOperationException("NOT_FOUND","Build not found: "+id);return json(j);}
    public JsonObject cancel(UUID id){Job j=jobs.get(id);if(j==null)throw new BridgeOperationException("NOT_FOUND","Build not found: "+id);if(!j.persistTransaction)throw new BridgeOperationException("PERMISSION_DENIED","Undo/redo restoration cannot be cancelled");if(j.status==Status.COMPLETE||j.status==Status.FAILED||j.status==Status.CANCELLED)return json(j);pending.remove(j);if(j==active)active=null;transactions.cancel(j.tx);j.status=Status.CANCELLED;AiWorldBuilderBridge.LOGGER.info("Build cancelled {} applied={}",j.id,j.cursor);return json(j);}
    private JsonObject json(Job j){JsonObject out=new JsonObject();out.addProperty("buildId",j.id.toString());out.addProperty("transactionId",j.outputTransactionId.toString());out.addProperty("status",j.status.name().toLowerCase(Locale.ROOT));out.addProperty("completedBlocks",j.cursor);out.addProperty("totalBlocks",j.placements.size());out.addProperty("progress",j.placements.isEmpty()?1.0:(double)j.cursor/j.placements.size());out.addProperty("summary",j.summary);if(j.error!=null)out.addProperty("error",j.error);return out;}
    private void trim(){while(jobs.size()>BridgeConfig.HISTORY_LIMIT.get()*2){UUID first=jobs.keySet().iterator().next();Job j=jobs.get(first);if(j==active||pending.contains(j))break;jobs.remove(first);}}
}
