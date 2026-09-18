package dev.worldbuilder.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

public final class SafetyPolicy {
    public void validateRead(Region region,ServerLevel level){validateDimension(region.dimension());if(region.volume()>BridgeConfig.MAX_REGION_VOLUME.get())limit("Region volume",region.volume(),BridgeConfig.MAX_REGION_VOLUME.get());if(region.min().getY()<level.getMinBuildHeight()||region.max().getY()>=level.getMaxBuildHeight())throw new BridgeOperationException("LIMIT_EXCEEDED","Region is outside dimension build height");for(int cx=region.min().getX()>>4;cx<=region.max().getX()>>4;cx++)for(int cz=region.min().getZ()>>4;cz<=region.max().getZ()>>4;cz++)if(!level.hasChunkAt(new BlockPos(cx<<4,Math.max(level.getMinBuildHeight(),region.min().getY()),cz<<4)))throw new BridgeOperationException("WORLD_UNAVAILABLE","Requested region contains an unloaded chunk; move closer or load it in Minecraft first");}
    public void validateScan(Region region,ServerLevel level){validateRead(region,level);int max=BridgeConfig.MAX_SCAN_RADIUS.get()*2+1;if(region.max().getX()-region.min().getX()+1>max||region.max().getY()-region.min().getY()+1>max||region.max().getZ()-region.min().getZ()+1>max)throw new BridgeOperationException("LIMIT_EXCEEDED","Scan exceeds maxScanRadius");}
    public void validateWrite(Region region, int changes, ServerLevel level) { validateRead(region,level); if (changes > BridgeConfig.MAX_BLOCKS_PER_BUILD.get()) limit("Block changes", changes, BridgeConfig.MAX_BLOCKS_PER_BUILD.get());
        for (Region protectedRegion : protectedRegions()) if (region.intersects(protectedRegion)) throw new BridgeOperationException("PERMISSION_DENIED", "Build intersects protected region");
        if(!BridgeConfig.ALLOW_BLOCK_ENTITY_OVERWRITE.get())for(BlockPos p:region)if(level.getBlockEntity(p)!=null)throw new BridgeOperationException("PERMISSION_DENIED","Build would overwrite a BlockEntity at "+p.toShortString());
        if (level.players().isEmpty()) throw new BridgeOperationException("PERMISSION_DENIED", "At least one player must be online for world modification");
        long radiusSq=(long)BridgeConfig.MAX_BUILD_RADIUS.get()*BridgeConfig.MAX_BUILD_RADIUS.get(); BlockPos center=new BlockPos((region.min().getX()+region.max().getX())/2,(region.min().getY()+region.max().getY())/2,(region.min().getZ()+region.max().getZ())/2);
        boolean near=level.players().stream().anyMatch(p->p.blockPosition().distSqr(center)<=radiusSq); if(!near) throw new BridgeOperationException("LIMIT_EXCEEDED", "Build is outside maxBuildRadius of every online player"); }
    public void validateDimension(String dimension) { if (!BridgeConfig.ALLOWED_DIMENSIONS.get().contains(dimension)) throw new BridgeOperationException("PERMISSION_DENIED", "Dimension is not allowed: "+dimension); }
    private List<Region> protectedRegions() { List<Region> result=new ArrayList<>(); for(String rule:BridgeConfig.PROTECTED_REGIONS.get()) { String[] p=rule.split("\\|"); if(p.length!=3) throw new BridgeOperationException("INTERNAL_ERROR", "Invalid protectedRegions rule: "+rule); result.add(new Region(p[0], parse(p[1]), parse(p[2]))); } return result; }
    private BlockPos parse(String s) { String[] v=s.split(","); if(v.length!=3) throw new BridgeOperationException("INTERNAL_ERROR", "Invalid protected region coordinate: "+s); try{return new BlockPos(Integer.parseInt(v[0].trim()),Integer.parseInt(v[1].trim()),Integer.parseInt(v[2].trim()));}catch(NumberFormatException e){throw new BridgeOperationException("INTERNAL_ERROR", "Invalid protected region coordinate: "+s);} }
    private void limit(String label,long actual,long max){throw new BridgeOperationException("LIMIT_EXCEEDED",label+" "+actual+" exceeds "+max);}
}
