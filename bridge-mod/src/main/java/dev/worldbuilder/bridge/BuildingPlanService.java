package dev.worldbuilder.bridge;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class BuildingPlanService implements BuildingPlanExecutor {
    private static final Set<String> TERRAIN_MODES=Set.of("preserveTerrain","minimalTerraforming","flatten","terrace","followSlope","bridgeOverTerrain");
    private final WorldAccess world;private final SafetyPolicy safety;private final GeometryEngine geometry=new GeometryEngine();
    public BuildingPlanService(WorldAccess world,SafetyPolicy safety){this.world=world;this.safety=safety;}

    @Override public Validation validate(JsonObject plan){
        List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();
        try{
            String dimension=requiredString(plan,"dimension");Region bounds=region(dimension,requiredObject(plan,"boundingBox"));safety.validateRead(bounds,world.level(dimension));
            JsonObject dimensions=requiredObject(plan,"dimensions");int sx=requiredInt(dimensions,"x"),sy=requiredInt(dimensions,"y"),sz=requiredInt(dimensions,"z");
            if(sx<=0||sy<=0||sz<=0)errors.add("dimensions must be positive");
            if(sx!=bounds.max().getX()-bounds.min().getX()+1||sy!=bounds.max().getY()-bounds.min().getY()+1||sz!=bounds.max().getZ()-bounds.min().getZ()+1)errors.add("dimensions does not match boundingBox");
            if(requiredInt(plan,"floors")<=0)errors.add("floors must be positive");
            String terrain=requiredString(plan,"terrainAdaptation");if(!TERRAIN_MODES.contains(terrain))errors.add("Unknown terrainAdaptation: "+terrain);
            JsonObject palette=requiredObject(plan,"palette");for(String role:palette.keySet())parseState(palette.getAsJsonObject(role));
            for(JsonElement e:requiredArray(plan,"operations")){JsonObject op=e.getAsJsonObject();String role=requiredString(op,"paletteRole");if(!palette.has(role))errors.add("Unknown paletteRole: "+role);BlockPos from=pos(requiredObject(op,"from"));if(!bounds.contains(from))errors.add("Operation from is outside boundingBox");if(op.has("to")&&!bounds.contains(pos(op.getAsJsonObject("to"))))errors.add("Operation to is outside boundingBox");if(op.has("options")&&op.get("options").isJsonObject()&&op.getAsJsonObject("options").has("openings")&&op.getAsJsonObject("options").get("openings").isJsonArray())for(JsonElement opening:op.getAsJsonObject("options").getAsJsonArray("openings")){if(!opening.isJsonObject()||!opening.getAsJsonObject().has("from")||!opening.getAsJsonObject().has("to")){errors.add("Opening must contain from and to");continue;}if(!bounds.contains(pos(opening.getAsJsonObject().getAsJsonObject("from")))||!bounds.contains(pos(opening.getAsJsonObject().getAsJsonObject("to"))))errors.add("Opening is outside boundingBox");}}
            int estimate=placements(plan).size();if(estimate>BridgeConfig.MAX_BLOCKS_PER_BUILD.get())errors.add("Estimated changes exceed maxBlocksPerBuild");JsonObject constraints=requiredObject(plan,"constraints");if(constraints.has("maxChanges")&&estimate>constraints.get("maxChanges").getAsInt())errors.add("Estimated changes exceed plan constraints.maxChanges");if(estimate==0)warnings.add("Plan has no effective placements");
            if(Set.of("terrace","followSlope","bridgeOverTerrain").contains(terrain))warnings.add("Terrain adaptation must be represented explicitly by the plan operations after heightmap analysis");
        }catch(BridgeOperationException|JsonParseException|IllegalArgumentException e){errors.add(e.getMessage());}
        return new Validation(errors.isEmpty(),errors,warnings);
    }

    @Override public Estimate estimate(JsonObject plan){Region r=region(requiredString(plan,"dimension"),requiredObject(plan,"boundingBox"));return new Estimate(placements(plan).size(),WorldAccess.regionJson(r));}
    public JsonObject validationJson(JsonObject plan){Validation v=validate(plan);JsonObject out=new JsonObject();out.addProperty("valid",v.valid());out.add("errors",strings(v.errors()));out.add("warnings",strings(v.warnings()));if(v.valid()){out.addProperty("estimatedBlockChanges",placements(plan).size());out.add("boundingBox",requiredObject(plan,"boundingBox").deepCopy());}return out;}
    public JsonObject estimateJson(JsonObject plan){Estimate e=estimate(plan);JsonObject out=new JsonObject();out.addProperty("estimatedBlockChanges",e.blockChanges());out.add("boundingBox",e.boundingBox());return out;}
    public JsonObject preview(JsonObject plan){
        Validation validation=validate(plan);JsonObject out=validationJson(plan);if(!validation.valid())return out;ServerLevel level=world.level(requiredString(plan,"dimension"));int overwrite=0,blockEntities=0,air=0;Map<String,Integer> materials=new TreeMap<>();
        for(BuildQueue.Placement p:placements(plan)){BlockState current=level.getBlockState(p.position());if(current.isAir())air++;else if(!current.equals(p.state()))overwrite++;if(current.hasBlockEntity())blockEntities++;materials.merge(ForgeRegistries.BLOCKS.getKey(p.state().getBlock()).toString(),1,Integer::sum);}
        out.addProperty("airPlacements",air);out.addProperty("existingBlocksAffected",overwrite);out.addProperty("existingBlockEntitiesAffected",blockEntities);JsonObject materialJson=new JsonObject();materials.forEach(materialJson::addProperty);out.add("materials",materialJson);JsonArray warnings=out.getAsJsonArray("warnings");if(blockEntities>0)warnings.add("existing complex block entities detected; execution is blocked by default");if(overwrite>0)warnings.add("plan will overwrite existing non-air blocks");return out;
    }

    public List<BuildQueue.Placement> placements(JsonObject plan){
        String dimension=requiredString(plan,"dimension"),orientation=requiredString(plan,"orientation");JsonObject palette=requiredObject(plan,"palette");LinkedHashMap<BlockPos,BuildQueue.Placement> result=new LinkedHashMap<>();
        for(JsonElement e:requiredArray(plan,"operations")){
            JsonObject op=e.getAsJsonObject(),stateJson=palette.getAsJsonObject(requiredString(op,"paletteRole")).deepCopy();if(op.has("properties")){JsonObject properties=stateJson.has("properties")?stateJson.getAsJsonObject("properties"):new JsonObject();op.getAsJsonObject("properties").entrySet().forEach(v->properties.add(v.getKey(),v.getValue()));stateJson.add("properties",properties);}
            BlockState state=WorldAccess.orient(parseState(stateJson),orientation);BlockPos from=pos(requiredObject(op,"from")),to=op.has("to")?pos(op.getAsJsonObject("to")):from;String type=requiredString(op,"type");Set<BlockPos> points;
            if(type.equals("set_block"))points=Set.of(from);else points=geometry.generate(mapOperation(type),new Region(dimension,from,to));
            points = withoutOpenings(points, op);
            for(BlockPos p:points)result.put(p,new BuildQueue.Placement(p,state));
        }
        return List.copyOf(result.values());
    }

    public void requireSafePreview(JsonObject plan){JsonObject preview=preview(plan);if(!preview.get("valid").getAsBoolean())throw new BridgeOperationException("MALFORMED_REQUEST",preview.getAsJsonArray("errors").toString());boolean avoid=requiredObject(plan,"constraints").has("avoidExistingStructures")&&requiredObject(plan,"constraints").get("avoidExistingStructures").getAsBoolean();if(avoid&&preview.get("existingBlockEntitiesAffected").getAsInt()>0)throw new BridgeOperationException("PERMISSION_DENIED","Plan would overwrite existing complex BlockEntities");if(avoid&&preview.get("existingBlocksAffected").getAsInt()>0)throw new BridgeOperationException("PERMISSION_DENIED","Plan has avoidExistingStructures=true but would overwrite existing non-air blocks");}
    private static String mapOperation(String type){return switch(type){case "floor"->"build_floor";case "wall"->"build_wall";case "box"->"build_box";case "column"->"build_column";case "beam"->"build_beam";case "window"->"build_window";case "doorway"->"build_doorway";case "stairs"->"build_stairs";case "staircase"->"build_staircase";case "flat_roof"->"build_flat_roof";case "gable_roof"->"build_gable_roof";case "hip_roof"->"build_hip_roof";case "circle"->"build_circle";case "tower"->"build_tower";case "cylinder"->"build_cylinder";case "platform"->"build_platform";case "foundation"->"build_foundation";case "path"->"build_path";case "road"->"build_road";case "fence"->"build_fence";case "arch"->"build_arch";case "bridge"->"build_bridge";default->throw new BridgeOperationException("MALFORMED_REQUEST","Unknown BuildingPlan operation: "+type);};}
    static Set<BlockPos> withoutOpenings(Set<BlockPos> points,JsonObject operation){if(!operation.has("options")||!operation.get("options").isJsonObject())return points;JsonObject options=operation.getAsJsonObject("options");if(!options.has("openings")||!options.get("openings").isJsonArray())return points;List<Region> openings=new ArrayList<>();for(JsonElement element:options.getAsJsonArray("openings")){if(!element.isJsonObject())continue;JsonObject opening=element.getAsJsonObject();if(!opening.has("from")||!opening.has("to"))continue;openings.add(new Region("minecraft:overworld",pos(opening.getAsJsonObject("from")),pos(opening.getAsJsonObject("to"))));}if(openings.isEmpty())return points;return points.stream().filter(point->openings.stream().noneMatch(opening->opening.contains(point))).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));}
    private static BlockState parseState(JsonObject s){return WorldAccess.parseState(requiredString(s,"id"),s.has("properties")?s.getAsJsonObject("properties"):new JsonObject());}
    private static Region region(String dimension,JsonObject box){return new Region(dimension,pos(requiredObject(box,"min")),pos(requiredObject(box,"max")));}
    private static BlockPos pos(JsonObject j){return new BlockPos(requiredInt(j,"x"),requiredInt(j,"y"),requiredInt(j,"z"));}
    private static JsonArray strings(List<String> values){JsonArray a=new JsonArray();values.forEach(a::add);return a;}
    private static JsonObject requiredObject(JsonObject j,String n){if(!j.has(n)||!j.get(n).isJsonObject())throw new BridgeOperationException("MALFORMED_REQUEST","Missing object: "+n);return j.getAsJsonObject(n);}
    private static JsonArray requiredArray(JsonObject j,String n){if(!j.has(n)||!j.get(n).isJsonArray())throw new BridgeOperationException("MALFORMED_REQUEST","Missing array: "+n);return j.getAsJsonArray(n);}
    private static String requiredString(JsonObject j,String n){if(!j.has(n))throw new BridgeOperationException("MALFORMED_REQUEST","Missing string: "+n);return j.get(n).getAsString();}
    private static int requiredInt(JsonObject j,String n){if(!j.has(n))throw new BridgeOperationException("MALFORMED_REQUEST","Missing integer: "+n);return j.get(n).getAsInt();}
}
