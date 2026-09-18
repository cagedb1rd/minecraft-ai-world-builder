package dev.worldbuilder.bridge;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

public final class ClipboardManager {
    private record RelativeSnapshot(BlockPos relative, TransactionManager.Snapshot snapshot) {}
    private record Clipboard(UUID id,Region source,List<RelativeSnapshot> snapshots) {}
    private final Map<UUID,Clipboard> clips=new LinkedHashMap<>();private final TransactionManager transactions;
    public ClipboardManager(TransactionManager transactions){this.transactions=transactions;}
    public JsonObject copy(ServerLevel level,Region region){List<RelativeSnapshot> values=new ArrayList<>();for(BlockPos p:region){TransactionManager.Snapshot snapshot=transactions.capture(level,p);if(snapshot.blockEntityData()!=null)throw new BridgeOperationException("PERMISSION_DENIED","Clipboard refuses to duplicate BlockEntity data at "+p.toShortString());values.add(new RelativeSnapshot(p.subtract(region.min()),snapshot));}Clipboard c=new Clipboard(UUID.randomUUID(),region,List.copyOf(values));clips.put(c.id,c);while(clips.size()>20)clips.remove(clips.keySet().iterator().next());JsonObject out=new JsonObject();out.addProperty("clipboardId",c.id.toString());out.addProperty("blocks",values.size());out.add("source",WorldAccess.regionJson(region));return out;}
    public List<BuildQueue.Placement> paste(UUID id,BlockPos target,String rotationName,String mirrorName){Clipboard c=clips.get(id);if(c==null)throw new BridgeOperationException("NOT_FOUND","Clipboard not found: "+id);Rotation rotation=rotation(rotationName);Mirror mirror=mirror(mirrorName);List<BlockPos> transformed=c.snapshots.stream().map(s->transform(s.relative,rotation,mirror)).toList();int minX=transformed.stream().mapToInt(BlockPos::getX).min().orElse(0),minZ=transformed.stream().mapToInt(BlockPos::getZ).min().orElse(0);List<BuildQueue.Placement> out=new ArrayList<>();for(int i=0;i<c.snapshots.size();i++){RelativeSnapshot s=c.snapshots.get(i);BlockPos rel=transformed.get(i).offset(-minX,0,-minZ);var state=s.snapshot.state().mirror(mirror).rotate(rotation);out.add(new BuildQueue.Placement(target.offset(rel),state,s.snapshot.blockEntityData()));}return out;}
    public List<BuildQueue.Placement> transformInPlace(ServerLevel level,Region region,String rotation,String mirror){JsonObject copied=copy(level,region);UUID id=UUID.fromString(copied.get("clipboardId").getAsString());List<BuildQueue.Placement> out=new ArrayList<>();for(BlockPos p:region)out.add(new BuildQueue.Placement(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));out.addAll(paste(id,region.min(),rotation,mirror));return out;}
    private BlockPos transform(BlockPos p,Rotation r,Mirror m){int x=p.getX(),z=p.getZ();if(m==Mirror.LEFT_RIGHT)z=-z;if(m==Mirror.FRONT_BACK)x=-x;return switch(r){case CLOCKWISE_90->new BlockPos(-z,p.getY(),x);case CLOCKWISE_180->new BlockPos(-x,p.getY(),-z);case COUNTERCLOCKWISE_90->new BlockPos(z,p.getY(),-x);default->new BlockPos(x,p.getY(),z);};}
    private Rotation rotation(String s){return switch(s){case "clockwise_90"->Rotation.CLOCKWISE_90;case "clockwise_180"->Rotation.CLOCKWISE_180;case "counterclockwise_90"->Rotation.COUNTERCLOCKWISE_90;default->Rotation.NONE;};}
    private Mirror mirror(String s){return switch(s){case "left_right"->Mirror.LEFT_RIGHT;case "front_back"->Mirror.FRONT_BACK;default->Mirror.NONE;};}
}
