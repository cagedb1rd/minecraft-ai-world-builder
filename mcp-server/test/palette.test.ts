import { describe, expect, it } from "vitest";
import { architecturalDesignSchema } from "../src/architectural.js";
import { PaletteResolver } from "../src/palette.js";

class RegistryBridge {
  async call(operation: string, args: Record<string, unknown> = {}): Promise<unknown> {
    if (operation === "get_world_info") return { minecraftVersion: "1.20.1" };
    if (operation === "list_mods") return [{ id: "minecraft", version: "1.20.1" }, { id: "decor", version: "1.0" }];
    if (operation === "get_mod_capabilities") return { protocolVersion: "1.0.0", capabilities: ["registry"] };
    if (operation === "search_blocks") {
      const query = String(args.query ?? "");
      if (query.includes("wood")) return [{ id: "minecraft:spruce_log", namespace: "minecraft", displayName: "Spruce Log", hasItem: true, tags: ["minecraft:logs"] }, { id: "decor:wood_frame", namespace: "decor", displayName: "Wood Frame", hasItem: true, tags: ["decor:frames"] }];
      return [{ id: "minecraft:stone", namespace: "minecraft", displayName: "Stone", hasItem: true, tags: ["minecraft:stone"] }];
    }
    if (operation === "get_block_info") {
      const id = String(args.id);
      return { id, namespace: id.split(":")[0], displayName: id, hasItem: true, tags: id.includes("spruce") ? ["minecraft:logs"] : ["minecraft:stone"], properties: {} };
    }
    if (operation === "get_block_states") {
      const id = String(args.id);
      return { id, properties: {} };
    }
    throw new Error(`Unexpected registry operation ${operation}`);
  }
}

describe("PaletteResolver", () => {
  it("selects deterministic candidates from the current Registry", async () => {
    const design = architecturalDesignSchema.parse({
      designId: "palette-test", name: "Palette", styleId: "test", dimension: "minecraft:overworld", anchor: { x: 0, y: 64, z: 0 }, orientation: "north", footprint: { x: 5, z: 5 }, floors: 1, floorHeights: [3],
      paletteIntents: { structuralWood: { requiredTags: ["minecraft:logs"], preferredKeywords: ["wood"] } },
      components: [{ type: "foundation_skirt", from: { x: 0, y: 0, z: 0 }, to: { x: 4, y: 0, z: 4 }, paletteRole: "structuralWood", openings: [], options: {} }]
    });
    const result = await new PaletteResolver(new RegistryBridge()).resolve(design);
    expect(result.palette.structuralWood.id).toBe("minecraft:spruce_log");
    expect(result.selections[0]?.reason).toContain("minecraft:spruce_log");
  });

  it("records an explicit fallback instead of silently inventing a block", async () => {
    const design = architecturalDesignSchema.parse({
      designId: "fallback-test", name: "Fallback", styleId: "test", dimension: "minecraft:overworld", anchor: { x: 0, y: 64, z: 0 }, orientation: "north", footprint: { x: 5, z: 5 }, floors: 1, floorHeights: [3],
      paletteIntents: { wallPrimary: { fallbackRoles: ["structuralWood"] }, structuralWood: { preferredIds: ["minecraft:spruce_log"] } },
      components: [{ type: "gable_infill", from: { x: 0, y: 0, z: 0 }, to: { x: 4, y: 2, z: 4 }, paletteRole: "wallPrimary", openings: [], options: {} }]
    });
    const result = await new PaletteResolver(new RegistryBridge()).resolve(design);
    expect(result.palette.wallPrimary.id).toBe("minecraft:spruce_log");
    expect(result.warnings.join(" ")).toContain("fallback");
  });

  it("validates BlockState properties against the current Registry", async () => {
    const bridge = new RegistryBridge();
    const resolver = new PaletteResolver(bridge);
    const result = await resolver.validate({ door: { id: "minecraft:oak_door", properties: { half: "middle" } } });
    expect(result.valid).toBe(false);
    expect(result.errors[0]).toContain("invalid half=middle");
  });
});
