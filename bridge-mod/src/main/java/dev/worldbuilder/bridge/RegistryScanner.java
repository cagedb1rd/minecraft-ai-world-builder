package dev.worldbuilder.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Comparator;
import java.util.Locale;

public final class RegistryScanner {
    public JsonArray listMods() { JsonArray out = new JsonArray(); ModList.get().getMods().stream().sorted(Comparator.comparing(m -> m.getModId())).forEach(m -> { JsonObject j = new JsonObject();
        j.addProperty("id", m.getModId()); j.addProperty("displayName", m.getDisplayName()); j.addProperty("version", m.getVersion().toString()); out.add(j); }); return out; }
    public JsonObject listBlocks(int offset, int limit) { var entries = ForgeRegistries.BLOCKS.getEntries().stream().sorted(Comparator.comparing(e -> e.getKey().location().toString())).skip(offset).limit(limit).toList();
        JsonArray items = new JsonArray(); entries.forEach(e -> items.add(summary(e.getValue()))); JsonObject out = new JsonObject(); out.addProperty("total", ForgeRegistries.BLOCKS.getKeys().size()); out.addProperty("offset", offset); out.add("blocks", items); return out; }
    public JsonArray searchBlocks(String query, int limit) { String q = query.toLowerCase(Locale.ROOT); JsonArray out = new JsonArray(); ForgeRegistries.BLOCKS.getEntries().stream()
        .filter(e -> matches(e.getKey().location().toString(),e.getValue().getName().getString(),q)).limit(limit).forEach(e -> out.add(summary(e.getValue()))); return out; }
    public JsonObject blockInfo(String id) { var key = net.minecraft.resources.ResourceLocation.tryParse(id); Block block = key == null ? null : ForgeRegistries.BLOCKS.getValue(key); if (block == null) throw new BridgeOperationException("NOT_FOUND", "Block not found: " + id);
        JsonObject out = summary(block); JsonArray tags = new JsonArray(); block.builtInRegistryHolder().tags().forEach(t -> tags.add(t.location().toString())); out.add("tags", tags);
        JsonObject properties = new JsonObject(); BlockState state = block.defaultBlockState(); for (Property<?> p : state.getProperties()) { JsonArray values = new JsonArray(); p.getPossibleValues().forEach(v -> values.add(valueName(p, v))); properties.add(p.getName(), values); } out.add("properties", properties); return out; }
    public JsonObject listItems(int offset,int limit){var entries=ForgeRegistries.ITEMS.getEntries().stream().sorted(Comparator.comparing(e->e.getKey().location().toString())).skip(offset).limit(limit).toList();JsonArray items=new JsonArray();entries.forEach(e->items.add(itemSummary(e.getValue())));JsonObject out=new JsonObject();out.addProperty("total",ForgeRegistries.ITEMS.getKeys().size());out.addProperty("offset",offset);out.add("items",items);return out;}
    public JsonArray searchItems(String query,int limit){String q=query.toLowerCase(Locale.ROOT);JsonArray out=new JsonArray();ForgeRegistries.ITEMS.getEntries().stream().filter(e->matches(e.getKey().location().toString(),e.getValue().getDescription().getString(),q)).limit(limit).forEach(e->out.add(itemSummary(e.getValue())));return out;}
    static boolean matches(String id,String displayName,String query){String q=query.toLowerCase(Locale.ROOT);return id.toLowerCase(Locale.ROOT).contains(q)||displayName.toLowerCase(Locale.ROOT).contains(q);}
    public JsonObject itemInfo(String id){var key=net.minecraft.resources.ResourceLocation.tryParse(id);Item item=key==null||!ForgeRegistries.ITEMS.containsKey(key)?null:ForgeRegistries.ITEMS.getValue(key);if(item==null)throw new BridgeOperationException("NOT_FOUND","Item not found: "+id);JsonObject out=itemSummary(item);JsonArray tags=new JsonArray();item.builtInRegistryHolder().tags().forEach(t->tags.add(t.location().toString()));out.add("tags",tags);out.addProperty("maxStackSize",item.getMaxStackSize());out.addProperty("durability",item.getMaxDamage());return out;}
    public JsonArray tags(String registry,String namespace){JsonArray out=new JsonArray();var stream=registry.equals("block")?ForgeRegistries.BLOCKS.tags().getTagNames().map(t->t.location()):ForgeRegistries.ITEMS.tags().getTagNames().map(t->t.location());stream.filter(id->namespace==null||id.getNamespace().equals(namespace)).sorted().forEach(id->out.add(id.toString()));return out;}
    private JsonObject summary(Block block) { JsonObject out = new JsonObject(); var id = ForgeRegistries.BLOCKS.getKey(block); out.addProperty("id", id.toString()); out.addProperty("namespace", id.getNamespace());
        out.addProperty("displayName", block.getName().getString()); out.addProperty("modName",modName(id.getNamespace()));out.addProperty("hasItem", block.asItem() != Items.AIR); out.addProperty("hasBlockEntity", block.defaultBlockState().hasBlockEntity()); return out; }
    private JsonObject itemSummary(Item item){JsonObject out=new JsonObject();var id=ForgeRegistries.ITEMS.getKey(item);out.addProperty("id",id.toString());out.addProperty("namespace",id.getNamespace());out.addProperty("displayName",item.getDescription().getString());out.addProperty("modName",modName(id.getNamespace()));return out;}
    private String modName(String id){return ModList.get().getMods().stream().filter(m->m.getModId().equals(id)).map(m->m.getDisplayName()).findFirst().orElse(id.equals("minecraft")?"Minecraft":id);}
    @SuppressWarnings({"rawtypes", "unchecked"}) private static String valueName(Property property, Comparable value) { return property.getName(value); }
}
