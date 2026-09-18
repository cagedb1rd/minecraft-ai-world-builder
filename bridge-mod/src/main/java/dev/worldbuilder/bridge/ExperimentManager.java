package dev.worldbuilder.bridge;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import java.time.Instant;
import java.util.*;

public final class ExperimentManager {
    private record Workspace(UUID id,Region region,List<TransactionManager.Snapshot> baseline,Instant created){}
    private final Map<UUID,Workspace> workspaces=new LinkedHashMap<>();private final TransactionManager transactions;
    public ExperimentManager(TransactionManager transactions){this.transactions=transactions;}
    public JsonObject create(ServerLevel level,Region region){List<TransactionManager.Snapshot> baseline=new ArrayList<>();for(var p:region)baseline.add(transactions.capture(level,p));Workspace w=new Workspace(UUID.randomUUID(),region,List.copyOf(baseline),Instant.now());workspaces.put(w.id,w);return json(w);}
    public JsonObject inspect(UUID id,ServerLevel level){Workspace w=require(id);JsonObject out=json(w);int changed=0;for(var s:w.baseline)if(!level.getBlockState(s.position()).equals(s.state()))changed++;out.addProperty("changedBlocks",changed);return out;}
    public List<BuildQueue.Placement> reset(UUID id){Workspace w=require(id);return w.baseline.stream().map(s->new BuildQueue.Placement(s.position(),s.state(),s.blockEntityData())).toList();}
    public Region region(UUID id){return require(id).region;}
    private Workspace require(UUID id){Workspace w=workspaces.get(id);if(w==null)throw new BridgeOperationException("NOT_FOUND","Experiment workspace not found: "+id);return w;}
    private JsonObject json(Workspace w){JsonObject out=new JsonObject();out.addProperty("experimentId",w.id.toString());out.add("bounds",WorldAccess.regionJson(w.region));out.addProperty("createdAt",w.created.toString());return out;}
}
