package dev.worldbuilder.bridge;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionTest {
    @Test void normalizesBoundsAndIteratesEveryPosition(){Region r=new Region("minecraft:overworld",new BlockPos(2,2,2),new BlockPos(0,0,0));assertEquals(new BlockPos(0,0,0),r.min());assertEquals(new BlockPos(2,2,2),r.max());assertEquals(27,r.volume());int count=0;for(BlockPos ignored:r)count++;assertEquals(27,count);}
    @Test void detectsIntersection(){Region a=new Region("d",BlockPos.ZERO,new BlockPos(2,2,2));assertTrue(a.intersects(new Region("d",new BlockPos(2,2,2),new BlockPos(3,3,3))));assertFalse(a.intersects(new Region("other",BlockPos.ZERO,BlockPos.ZERO)));}
}
