package dev.worldbuilder.bridge;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingEngineTest {
    private final BuildingEngine engine = new BuildingEngine.Deterministic();
    @Test void floorIsInclusiveAndOneLayer() { var blocks = engine.floor(new BlockPos(0, 5, 0), new BlockPos(2, 5, 1)); assertEquals(6, blocks.size()); assertTrue(blocks.stream().allMatch(p -> p.getY() == 5)); }
    @Test void hollowBoxExcludesInterior() { var blocks = engine.box(new BlockPos(0,0,0), new BlockPos(2,2,2), true); assertEquals(26, blocks.size()); assertFalse(blocks.contains(new BlockPos(1,1,1))); }
    @Test void everyExposedGeometryToolBuildsANonEmptyShape(){GeometryEngine geometry=new GeometryEngine();Region r=new Region("minecraft:overworld",new BlockPos(0,64,0),new BlockPos(6,70,8));for(String op:new String[]{"build_floor","build_wall","build_box","build_column","build_beam","build_window","build_doorway","build_stairs","build_staircase","build_roof","build_gable_roof","build_hip_roof","build_flat_roof","build_circle","build_cylinder","build_tower","build_platform","build_foundation","build_path","build_road","build_fence","build_arch","build_bridge"})assertFalse(geometry.generate(op,r).isEmpty(),op);}
    @Test void windowIsAPlanarPanelNotASolidWall(){GeometryEngine geometry=new GeometryEngine();Region r=new Region("minecraft:overworld",new BlockPos(0,64,0),new BlockPos(8,70,6));var window=geometry.generate("build_window",r);assertEquals(9*7,window.size());assertTrue(window.stream().allMatch(p->p.getZ()==0));}
}
