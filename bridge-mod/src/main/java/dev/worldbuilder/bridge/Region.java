package dev.worldbuilder.bridge;

import net.minecraft.core.BlockPos;
import java.util.Iterator;
import java.util.NoSuchElementException;

public record Region(String dimension, BlockPos min, BlockPos max) implements Iterable<BlockPos> {
    public Region { BlockPos a=min, b=max; min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())); }
    public long volume() { return (long)(max.getX()-min.getX()+1)*(max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1); }
    public boolean intersects(Region other) { return dimension.equals(other.dimension) && min.getX()<=other.max.getX() && max.getX()>=other.min.getX() && min.getY()<=other.max.getY() && max.getY()>=other.min.getY() && min.getZ()<=other.max.getZ() && max.getZ()>=other.min.getZ(); }
    public boolean contains(BlockPos p) { return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ(); }
    @Override public Iterator<BlockPos> iterator() { return new Iterator<>() { int x=min.getX(), y=min.getY(), z=min.getZ(); boolean done=false;
        public boolean hasNext(){return !done;} public BlockPos next(){ if(done)throw new NoSuchElementException(); BlockPos p=new BlockPos(x,y,z); if(++x>max.getX()){x=min.getX();if(++z>max.getZ()){z=min.getZ();if(++y>max.getY())done=true;}} return p; } }; }
}
