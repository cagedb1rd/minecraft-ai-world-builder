package dev.worldbuilder.bridge;

import net.minecraft.core.BlockPos;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GeometryEngine {
    private final BuildingEngine basic=new BuildingEngine.Deterministic();
    public Set<BlockPos> generate(String operation,Region r){return switch(operation){
        case "build_floor"->basic.floor(r.min(),r.max());case "build_wall"->basic.wall(r.min(),r.max());case "build_box"->basic.box(r.min(),r.max(),true);
        case "build_column"->column(r);case "build_beam"->line(r.min(),r.max());case "build_window"->window(r);case "build_doorway"->doorway(r);case "build_stairs","build_staircase"->stairs(r);case "build_roof","build_gable_roof"->gableRoof(r);case "build_hip_roof"->hipRoof(r);case "build_flat_roof","build_platform"->basic.floor(new BlockPos(r.min().getX(),r.max().getY(),r.min().getZ()),r.max());case "build_foundation"->basic.floor(r.min(),r.max());case "build_circle"->circle(r,r.min().getY());
        case "build_cylinder","build_tower"->tower(r);case "build_path"->path(r,3);case "build_road"->path(r,5);case "build_fence"->fence(r);case "build_arch"->arch(r);case "build_bridge"->bridge(r);default->throw new BridgeOperationException("UNSUPPORTED_OPERATION","Unknown geometry operation: "+operation);};}
    private Set<BlockPos> column(Region r){Set<BlockPos> out=new LinkedHashSet<>();for(int y=r.min().getY();y<=r.max().getY();y++)out.add(new BlockPos(r.min().getX(),y,r.min().getZ()));return out;}
    private Set<BlockPos> line(BlockPos a,BlockPos b){Set<BlockPos> out=new LinkedHashSet<>();int n=Math.max(Math.max(Math.abs(b.getX()-a.getX()),Math.abs(b.getY()-a.getY())),Math.abs(b.getZ()-a.getZ()));if(n==0){out.add(a);return out;}for(int i=0;i<=n;i++){double t=(double)i/n;out.add(new BlockPos((int)Math.round(a.getX()+(b.getX()-a.getX())*t),(int)Math.round(a.getY()+(b.getY()-a.getY())*t),(int)Math.round(a.getZ()+(b.getZ()-a.getZ())*t)));}return out;}
    private Set<BlockPos> stairs(Region r){Set<BlockPos> out=new LinkedHashSet<>();int dx=r.max().getX()-r.min().getX(),dz=r.max().getZ()-r.min().getZ();int length=Math.max(Math.abs(dx),Math.abs(dz));int rise=Math.max(0,r.max().getY()-r.min().getY());for(int i=0;i<=length;i++){int x=r.min().getX()+(length==0?0:(int)Math.round((double)dx*i/length));int z=r.min().getZ()+(length==0?0:(int)Math.round((double)dz*i/length));int y=r.min().getY()+(length==0?0:(int)Math.floor((double)rise*i/length));out.add(new BlockPos(x,y,z));}return out;}
    private Set<BlockPos> gableRoof(Region r){Set<BlockPos> out=new LinkedHashSet<>();int layers=Math.min((r.max().getX()-r.min().getX())/2,r.max().getY()-r.min().getY());for(int l=0;l<=layers;l++)for(int x=r.min().getX()+l;x<=r.max().getX()-l;x++)for(int z=r.min().getZ();z<=r.max().getZ();z++)if(x==r.min().getX()+l||x==r.max().getX()-l)out.add(new BlockPos(x,r.min().getY()+l,z));return out;}
    private Set<BlockPos> hipRoof(Region r){Set<BlockPos> out=new LinkedHashSet<>();int layers=Math.min(Math.min((r.max().getX()-r.min().getX())/2,(r.max().getZ()-r.min().getZ())/2),r.max().getY()-r.min().getY());for(int l=0;l<=layers;l++)out.addAll(new BuildingEngine.Deterministic().wall(new BlockPos(r.min().getX()+l,r.min().getY()+l,r.min().getZ()+l),new BlockPos(r.max().getX()-l,r.min().getY()+l,r.max().getZ()-l)));return out;}
    private Set<BlockPos> circle(Region r,int y){Set<BlockPos> out=new LinkedHashSet<>();double cx=(r.min().getX()+r.max().getX())/2.0,cz=(r.min().getZ()+r.max().getZ())/2.0,rx=Math.max(1,(r.max().getX()-r.min().getX())/2.0),rz=Math.max(1,(r.max().getZ()-r.min().getZ())/2.0);for(int x=r.min().getX();x<=r.max().getX();x++)for(int z=r.min().getZ();z<=r.max().getZ();z++){double d=Math.pow((x-cx)/rx,2)+Math.pow((z-cz)/rz,2);if(d>=0.65&&d<=1.35)out.add(new BlockPos(x,y,z));}return out;}
    private Set<BlockPos> tower(Region r){Set<BlockPos> out=new LinkedHashSet<>();double cx=(r.min().getX()+r.max().getX())/2.0,cz=(r.min().getZ()+r.max().getZ())/2.0,rx=Math.max(1,(r.max().getX()-r.min().getX())/2.0),rz=Math.max(1,(r.max().getZ()-r.min().getZ())/2.0);for(int y=r.min().getY();y<=r.max().getY();y++)for(int x=r.min().getX();x<=r.max().getX();x++)for(int z=r.min().getZ();z<=r.max().getZ();z++){double d=Math.pow((x-cx)/rx,2)+Math.pow((z-cz)/rz,2);if(d>=0.65&&d<=1.35)out.add(new BlockPos(x,y,z));}return out;}
    private Set<BlockPos> path(Region r,int width){Set<BlockPos> center=line(new BlockPos(r.min().getX(),r.min().getY(),r.min().getZ()),new BlockPos(r.max().getX(),r.max().getY(),r.max().getZ()));Set<BlockPos> out=new LinkedHashSet<>();int half=width/2;boolean alongX=Math.abs(r.max().getX()-r.min().getX())>=Math.abs(r.max().getZ()-r.min().getZ());for(BlockPos p:center)for(int o=-half;o<=half;o++)out.add(alongX?p.offset(0,0,o):p.offset(o,0,0));return out;}
    private Set<BlockPos> bridge(Region r){Set<BlockPos> out=path(r,3);for(BlockPos p:path(r,3))if((p.getX()+p.getZ())%5==0)for(int y=r.min().getY();y>=r.min().getY()-8;y--)out.add(new BlockPos(p.getX(),y,p.getZ()));return out;}
    /**
     * A window is a panel on one face, not a filled wall volume.  The old
     * implementation delegated to wall(), which made a semantic window
     * indistinguishable from a solid wall and could never leave an opening.
     * The dominant horizontal axis selects the face; callers can place glass
     * or panes with the operation's palette role while the surrounding wall
     * remains untouched.
     */
    private Set<BlockPos> window(Region r){
        Set<BlockPos> out=new LinkedHashSet<>();
        int xSpan=Math.abs(r.max().getX()-r.min().getX()), zSpan=Math.abs(r.max().getZ()-r.min().getZ());
        if(xSpan>=zSpan){
            int z=r.min().getZ();
            for(int x=r.min().getX();x<=r.max().getX();x++)for(int y=r.min().getY();y<=r.max().getY();y++)out.add(new BlockPos(x,y,z));
        }else{
            int x=r.min().getX();
            for(int z=r.min().getZ();z<=r.max().getZ();z++)for(int y=r.min().getY();y<=r.max().getY();y++)out.add(new BlockPos(x,y,z));
        }
        return out;
    }
    private Set<BlockPos> doorway(Region r){Set<BlockPos> out=new LinkedHashSet<>(basic.wall(r.min(),r.max()));int cx=(r.min().getX()+r.max().getX())/2,cz=(r.min().getZ()+r.max().getZ())/2;for(int y=r.min().getY();y<=Math.min(r.max().getY(),r.min().getY()+1);y++){out.remove(new BlockPos(cx,y,r.min().getZ()));out.remove(new BlockPos(cx,y,cz));}return out;}
    private Set<BlockPos> fence(Region r){Set<BlockPos> out=new LinkedHashSet<>();for(BlockPos p:basic.wall(new BlockPos(r.min().getX(),r.min().getY(),r.min().getZ()),new BlockPos(r.max().getX(),r.min().getY(),r.max().getZ())))out.add(p);return out;}
    private Set<BlockPos> arch(Region r){Set<BlockPos> out=new LinkedHashSet<>();for(int y=r.min().getY();y<=r.max().getY();y++){out.add(new BlockPos(r.min().getX(),y,r.min().getZ()));out.add(new BlockPos(r.max().getX(),y,r.max().getZ()));}out.addAll(line(new BlockPos(r.min().getX(),r.max().getY(),r.min().getZ()),new BlockPos(r.max().getX(),r.max().getY(),r.max().getZ())));return out;}
}
