package dev.worldbuilder.bridge;

import net.minecraft.core.BlockPos;
import java.util.LinkedHashSet;
import java.util.Set;

public interface BuildingEngine {
    Set<BlockPos> floor(BlockPos from, BlockPos to);
    Set<BlockPos> wall(BlockPos from, BlockPos to);
    Set<BlockPos> box(BlockPos from, BlockPos to, boolean hollow);

    final class Deterministic implements BuildingEngine {
        public Set<BlockPos> floor(BlockPos from, BlockPos to) { Set<BlockPos> out = new LinkedHashSet<>(); bounds(from, to, (x,y,z) -> out.add(new BlockPos(x, from.getY(), z))); return out; }
        public Set<BlockPos> wall(BlockPos from, BlockPos to) { Set<BlockPos> out = new LinkedHashSet<>(); bounds(from, to, (x,y,z) -> { if (x == Math.min(from.getX(),to.getX()) || x == Math.max(from.getX(),to.getX()) || z == Math.min(from.getZ(),to.getZ()) || z == Math.max(from.getZ(),to.getZ())) out.add(new BlockPos(x,y,z)); }); return out; }
        public Set<BlockPos> box(BlockPos from, BlockPos to, boolean hollow) { Set<BlockPos> out = new LinkedHashSet<>(); bounds(from, to, (x,y,z) -> { boolean edge = x == Math.min(from.getX(),to.getX()) || x == Math.max(from.getX(),to.getX()) || y == Math.min(from.getY(),to.getY()) || y == Math.max(from.getY(),to.getY()) || z == Math.min(from.getZ(),to.getZ()) || z == Math.max(from.getZ(),to.getZ()); if (!hollow || edge) out.add(new BlockPos(x,y,z)); }); return out; }
        private void bounds(BlockPos a, BlockPos b, PointConsumer c) { for (int x=Math.min(a.getX(),b.getX());x<=Math.max(a.getX(),b.getX());x++) for(int y=Math.min(a.getY(),b.getY());y<=Math.max(a.getY(),b.getY());y++) for(int z=Math.min(a.getZ(),b.getZ());z<=Math.max(a.getZ(),b.getZ());z++) c.accept(x,y,z); }
        @FunctionalInterface private interface PointConsumer { void accept(int x,int y,int z); }
    }
}
