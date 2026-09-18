package dev.worldbuilder.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.Direction;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class WorldAccess {
    private final MinecraftServer server;
    public WorldAccess(MinecraftServer server) { this.server = server; }
    public MinecraftServer server(){return server;}
    public JsonObject worldInfo() {
        JsonObject out = new JsonObject(); out.addProperty("activeHost", true); out.addProperty("dedicated", server.isDedicatedServer());
        out.addProperty("playerCount", server.getPlayerCount()); out.addProperty("worldName", server.getWorldData().getLevelName());
        out.addProperty("minecraftVersion", net.minecraft.SharedConstants.getCurrentVersion().getName());
        JsonArray dimensions = new JsonArray(); server.getAllLevels().forEach(l -> dimensions.add(l.dimension().location().toString())); out.add("dimensions", dimensions); return out;
    }
    public JsonArray players() { return players(List.of("*")); }
    public JsonArray players(List<? extends String> allowed) {
        JsonArray out = new JsonArray(); for (ServerPlayer p : server.getPlayerList().getPlayers()) { if(!allowed.contains("*")&&!allowed.contains(p.getUUID().toString())&&!allowed.contains(p.getGameProfile().getName()))continue;JsonObject j = new JsonObject();
            j.addProperty("uuid", p.getUUID().toString()); j.addProperty("name", p.getGameProfile().getName()); j.addProperty("x", p.getX()); j.addProperty("y", p.getY()); j.addProperty("z", p.getZ());
            j.addProperty("yaw", p.getYRot()); j.addProperty("pitch", p.getXRot()); j.addProperty("facing", p.getDirection().getName()); j.addProperty("dimension", p.level().dimension().location().toString()); out.add(j); } return out;
    }
    public JsonObject dimensionInfo(String id){ServerLevel level=level(id);JsonObject out=new JsonObject();out.addProperty("id",id);out.addProperty("minBuildHeight",level.getMinBuildHeight());out.addProperty("maxBuildHeight",level.getMaxBuildHeight());out.addProperty("dayTime",level.getDayTime());out.addProperty("raining",level.isRaining());out.addProperty("thundering",level.isThundering());out.addProperty("difficulty",server.getWorldData().getDifficulty().getKey());return out;}
    public JsonObject playerReference(String selector) {
        Optional<ServerPlayer> player = server.getPlayerList().getPlayers().stream().filter(p -> selector == null || selector.isBlank() || p.getUUID().toString().equals(selector) || p.getGameProfile().getName().equalsIgnoreCase(selector)).findFirst();
        if (player.isEmpty()) throw new BridgeOperationException("NOT_FOUND", "Reference player not found");
        ServerPlayer p = player.get(); JsonObject j = new JsonObject(); j.addProperty("uuid", p.getUUID().toString()); j.addProperty("name", p.getGameProfile().getName());
        j.addProperty("x", p.getX()); j.addProperty("y", p.getY()); j.addProperty("z", p.getZ()); j.addProperty("facing", p.getDirection().getName()); j.addProperty("dimension", p.level().dimension().location().toString()); return j;
    }
    public ServerLevel level(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id); if (location == null) throw new BridgeOperationException("MALFORMED_REQUEST", "Invalid dimension id: " + id);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, location)); if (level == null) throw new BridgeOperationException("WORLD_UNAVAILABLE", "Dimension is not loaded: " + id); return level;
    }
    public JsonObject getBlock(String dimension, BlockPos pos) { return stateJson(level(dimension).getBlockState(pos), pos); }
    public JsonObject scan(Region region) { ServerLevel level=level(region.dimension()); LinkedHashMap<String,Integer> indices=new LinkedHashMap<>(); JsonArray palette=new JsonArray(); JsonArray runs=new JsonArray(); int last=-1,count=0;
        for(BlockPos pos:region){BlockState state=level.getBlockState(pos);String key=state.toString();Integer index=indices.get(key);if(index==null){index=indices.size();indices.put(key,index);palette.add(stateJson(state,BlockPos.ZERO));}
            if(index==last)count++;else{if(count>0)addRun(runs,last,count);last=index;count=1;}} if(count>0)addRun(runs,last,count); JsonObject out=new JsonObject();out.add("bounds",regionJson(region));out.addProperty("volume",region.volume());out.add("palette",palette);out.add("runs",runs);out.addProperty("order","y,z,x");return out; }
    private void addRun(JsonArray runs,int paletteIndex,int count){JsonObject r=new JsonObject();r.addProperty("paletteIndex",paletteIndex);r.addProperty("count",count);runs.add(r);}
    public JsonObject heightmap(String dimension,int minX,int minZ,int maxX,int maxZ){ServerLevel level=level(dimension);JsonArray rows=new JsonArray();for(int z=Math.min(minZ,maxZ);z<=Math.max(minZ,maxZ);z++){JsonArray row=new JsonArray();for(int x=Math.min(minX,maxX);x<=Math.max(minX,maxX);x++)row.add(level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));rows.add(row);}JsonObject out=new JsonObject();out.addProperty("minX",Math.min(minX,maxX));out.addProperty("minZ",Math.min(minZ,maxZ));out.add("heights",rows);return out;}
    public JsonObject biome(String dimension,BlockPos pos){var holder=level(dimension).getBiome(pos);JsonObject out=new JsonObject();out.addProperty("id",holder.unwrapKey().map(k->k.location().toString()).orElse("unknown"));out.addProperty("x",pos.getX());out.addProperty("y",pos.getY());out.addProperty("z",pos.getZ());return out;}
    public JsonObject blockEntityInfo(String dimension,BlockPos pos){BlockEntity entity=level(dimension).getBlockEntity(pos);if(entity==null)throw new BridgeOperationException("NOT_FOUND","No BlockEntity at "+pos.toShortString());CompoundTag tag=entity.saveWithFullMetadata();JsonObject out=new JsonObject();out.addProperty("type",String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType())));JsonArray keys=new JsonArray();tag.getAllKeys().stream().filter(k->!Set.of("Items","RecordItem","LootTable","Command","CustomName").contains(k)).sorted().forEach(keys::add);out.add("safeNbtKeys",keys);out.addProperty("dataRedacted",true);return out;}
    public JsonObject regionSummary(Region region){ServerLevel level=level(region.dimension());Map<String,Integer> histogram=new TreeMap<>();JsonArray entities=new JsonArray();int fluids=0,minSurface=Integer.MAX_VALUE,maxSurface=Integer.MIN_VALUE;for(BlockPos pos:region){BlockState state=level.getBlockState(pos);String id=ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();histogram.merge(id,1,Integer::sum);if(!state.getFluidState().isEmpty())fluids++;if(state.hasBlockEntity()&&entities.size()<100){JsonObject e=new JsonObject();e.addProperty("x",pos.getX());e.addProperty("y",pos.getY());e.addProperty("z",pos.getZ());e.addProperty("block",id);entities.add(e);}}for(int x=region.min().getX();x<=region.max().getX();x++)for(int z=region.min().getZ();z<=region.max().getZ();z++){int h=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);minSurface=Math.min(minSurface,h);maxSurface=Math.max(maxSurface,h);}JsonObject counts=new JsonObject();histogram.forEach(counts::addProperty);JsonObject out=new JsonObject();out.add("bounds",regionJson(region));out.add("histogram",counts);out.addProperty("fluidBlocks",fluids);out.add("blockEntities",entities);out.addProperty("blockEntitiesTruncated",entities.size()>=100);out.addProperty("minSurfaceY",minSurface);out.addProperty("maxSurfaceY",maxSurface);out.addProperty("surfaceRelief",maxSurface-minSurface);out.addProperty("slopeDetected",maxSurface-minSurface>2);out.addProperty("waterDetected",fluids>0);return out;}
    public JsonArray searchRecipes(String query,int limit){
        String needle=query==null?"":query.toLowerCase(Locale.ROOT);JsonArray out=new JsonArray();
        for(var recipe:server.getRecipeManager().getRecipes()){
            String id=recipe.getId().toString();ItemStack result=recipe.getResultItem(server.registryAccess());ResourceLocation resultKey=ForgeRegistries.ITEMS.getKey(result.getItem());String resultId=resultKey==null?"minecraft:air":resultKey.toString();
            boolean matches=needle.isBlank()||id.toLowerCase(Locale.ROOT).contains(needle)||resultId.toLowerCase(Locale.ROOT).contains(needle);JsonArray ingredients=new JsonArray();
            for(Ingredient ingredient:recipe.getIngredients()){JsonArray alternatives=new JsonArray();for(ItemStack stack:ingredient.getItems()){ResourceLocation key=ForgeRegistries.ITEMS.getKey(stack.getItem());if(key!=null){String itemId=key.toString();alternatives.add(itemId);matches|=itemId.toLowerCase(Locale.ROOT).contains(needle);}if(alternatives.size()>=32)break;}ingredients.add(alternatives);}
            if(!matches)continue;JsonObject value=new JsonObject();value.addProperty("id",id);ResourceLocation type=net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());value.addProperty("type",type==null?"unknown":type.toString());value.addProperty("result",resultId);value.addProperty("resultCount",result.getCount());value.add("ingredients",ingredients);out.add(value);if(out.size()>=limit)break;
        }return out;
    }
    public static JsonObject regionJson(Region r){JsonObject out=new JsonObject();out.addProperty("dimension",r.dimension());out.add("min",posJson(r.min()));out.add("max",posJson(r.max()));return out;}
    public static JsonObject posJson(BlockPos p){JsonObject o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
    public static JsonObject stateJson(BlockState state, BlockPos pos) { JsonObject out = new JsonObject(); out.addProperty("id", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
        JsonObject props = new JsonObject(); state.getValues().forEach((p, v) -> props.addProperty(p.getName(), valueName(p, v))); out.add("properties", props);
        out.addProperty("x", pos.getX()); out.addProperty("y", pos.getY()); out.addProperty("z", pos.getZ()); return out; }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static String valueName(Property property, Comparable value) { return property.getName(value); }
    public static BlockState parseState(String id, JsonObject properties) { ResourceLocation key = ResourceLocation.tryParse(id); Block block = key == null || !ForgeRegistries.BLOCKS.containsKey(key) ? null : ForgeRegistries.BLOCKS.getValue(key);
        if (block == null) throw new BridgeOperationException("INVALID_BLOCK_STATE", "Unknown block: " + id); BlockState state = block.defaultBlockState();
        for (String name : properties.keySet()) { Property<?> property = state.getProperties().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow(() -> new BridgeOperationException("INVALID_BLOCK_STATE", "Unknown property " + name + " for " + id)); state = setValue(state, property, properties.get(name).getAsString()); } return state; }
    private static <T extends Comparable<T>> BlockState setValue(BlockState state, Property<T> property, String value) { T parsed = property.getValue(value).orElseThrow(() -> new BridgeOperationException("INVALID_BLOCK_STATE", "Invalid " + property.getName() + " value: " + value)); return state.setValue(property, parsed); }
    public static BlockState orient(BlockState state,String orientation){
        if(orientation==null)return state;
        Direction direction=switch(orientation){case "north"->Direction.NORTH;case "south"->Direction.SOUTH;case "east"->Direction.EAST;case "west"->Direction.WEST;default->throw new BridgeOperationException("MALFORMED_REQUEST","Invalid orientation: "+orientation);};
        BlockState oriented=state;
        for(Property<?> property:state.getProperties()){
            if(property instanceof DirectionProperty facing&&facing.getName().equals("facing")&&facing.getPossibleValues().contains(direction))oriented=oriented.setValue(facing,direction);
            else if(property.getName().equals("axis"))oriented=setPropertyIfPresent(oriented,property,(direction.getAxis()==Direction.Axis.X?"x":"z"));
        }
        return oriented;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState setPropertyIfPresent(BlockState state,Property<?> property,String value){
        Property raw=property;Optional parsed=raw.getValue(value);return parsed.isPresent()?state.setValue(raw,(Comparable)parsed.get()):state;
    }
}
