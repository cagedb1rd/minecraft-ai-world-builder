import { describe, expect, it } from "vitest";
import { buildingPlanSchema } from "@minecraft-ai-builder/shared-protocol";
import { architecturalDesignSchema, architecturalDesignExample, compileArchitecturalDesign, localToWorld, validateArchitecturalDesign } from "../src/architectural.js";

const palette = {
  foundation: { id: "minecraft:stone", properties: {} },
  structuralWood: { id: "minecraft:spruce_log", properties: {} },
  wallPrimary: { id: "minecraft:white_concrete", properties: {} },
  floorPrimary: { id: "minecraft:spruce_planks", properties: {} },
  roofPrimary: { id: "minecraft:deepslate_tile", properties: {} },
  roofTrim: { id: "minecraft:spruce_slab", properties: {} },
  railing: { id: "minecraft:spruce_fence", properties: {} },
  window: { id: "minecraft:glass_pane", properties: {} }
};

describe("ArchitecturalDesign", () => {
  it("rotates local coordinates deterministically", () => {
    expect(localToWorld({ x: 10, y: 64, z: 20 }, { x: 2, y: 3, z: 4 }, "north")).toEqual({ x: 12, y: 67, z: 24 });
    expect(localToWorld({ x: 10, y: 64, z: 20 }, { x: 2, y: 3, z: 4 }, "east")).toEqual({ x: 14, y: 67, z: 18 });
    expect(localToWorld({ x: 10, y: 64, z: 20 }, { x: 2, y: 3, z: 4 }, "west")).toEqual({ x: 6, y: 67, z: 22 });
  });

  it("rejects a floor height mismatch and reports semantic issues", () => {
    const design = architecturalDesignExample();
    const result = validateArchitecturalDesign({ ...design, floors: 3 });
    expect(result.valid).toBe(false);
    expect(result.errors.some(error => error.code === "FLOOR_HEIGHT_MISMATCH")).toBe(true);
  });

  it("compiles semantic components into a valid deterministic BuildingPlan", () => {
    const design = architecturalDesignSchema.parse(architecturalDesignExample());
    const first = compileArchitecturalDesign(design, palette);
    const second = compileArchitecturalDesign(design, palette);
    expect(first).toEqual(second);
    expect(buildingPlanSchema.safeParse(first).success).toBe(true);
    expect(first.operations.some(operation => operation.type === "gable_roof")).toBe(true);
    expect(first.operations.some(operation => operation.type === "column")).toBe(true);
    expect(first.operations.some(operation => operation.type === "fence")).toBe(true);
    expect(first.boundingBox.min.x).toBeLessThanOrEqual(0);
  });

  it("uses styleId as a deterministic compiler rule when roof type is omitted", () => {
    const base = architecturalDesignSchema.parse({
      designId: "style-test", name: "Style", styleId: "japanese_minka", dimension: "minecraft:overworld", anchor: { x: 0, y: 64, z: 0 }, orientation: "north", footprint: { x: 9, z: 9 }, floors: 1, floorHeights: [4], paletteIntents: {},
      components: [{ type: "layered_roof", from: { x: 0, y: 4, z: 0 }, to: { x: 8, y: 5, z: 8 }, paletteRole: "roof", openings: [], options: { layers: 1 } }]
    });
    const japanese = compileArchitecturalDesign(base, { roof: palette.roofPrimary });
    const manor = compileArchitecturalDesign({ ...base, styleId: "manor_hip" }, { roof: palette.roofPrimary });
    expect(japanese.operations[0]?.type).toBe("gable_roof");
    expect(manor.operations[0]?.type).toBe("hip_roof");
    expect(japanese.metadata.styleProfile).not.toBe(manor.metadata.styleProfile);
  });

  it("keeps wall openings as compact server-side options instead of fake filled windows", () => {
    const design = architecturalDesignSchema.parse({
      designId: "opening-test", name: "Opening", styleId: "test", dimension: "minecraft:overworld", anchor: { x: 10, y: 64, z: 20 }, orientation: "north", footprint: { x: 7, z: 7 }, floors: 1, floorHeights: [3],
      paletteIntents: {}, components: [{ type: "wall_with_openings", from: { x: 0, y: 0, z: 0 }, to: { x: 6, y: 3, z: 0 }, paletteRole: "wallPrimary", openings: [{ from: { x: 2, y: 1, z: 0 }, to: { x: 3, y: 2, z: 0 }, paletteRole: "window" }], options: {} }]
    });
    const plan = compileArchitecturalDesign(design, { wallPrimary: palette.wallPrimary, window: palette.window });
    const wall = plan.operations.find(operation => operation.type === "wall");
    expect(wall?.options).toMatchObject({ openings: [{ from: { x: 12, y: 65, z: 20 }, to: { x: 13, y: 66, z: 20 } }] });
    expect(plan.operations.some(operation => operation.type === "set_block")).toBe(false);
  });

  it("compiles a door assembly into real upper/lower door states and rejects accidental overlaps", () => {
    const design = architecturalDesignSchema.parse({
      designId: "door-test", name: "Door", styleId: "test", dimension: "minecraft:overworld", anchor: { x: 0, y: 64, z: 0 }, orientation: "north", footprint: { x: 7, z: 7 }, floors: 1, floorHeights: [4],
      paletteIntents: {}, components: [{ type: "door_assembly", from: { x: 2, y: 0, z: 0 }, to: { x: 2, y: 1, z: 0 }, paletteRole: "door", frameRole: "structuralWood", openings: [], options: { hinge: "right" } }]
    });
    const plan = compileArchitecturalDesign(design, { door: { id: "minecraft:oak_door", properties: {} }, structuralWood: palette.structuralWood });
    const doors = plan.operations.filter(operation => operation.type === "set_block");
    expect(doors.map(operation => operation.properties?.half)).toEqual(["lower", "upper"]);
    expect(doors[0]?.properties?.hinge).toBe("right");

    const overlap = validateArchitecturalDesign({ ...design, components: [...design.components, { type: "foundation_skirt", from: { x: 0, y: 0, z: 0 }, to: { x: 3, y: 1, z: 3 }, paletteRole: "foundation", openings: [], options: {} }] });
    expect(overlap.errors.some(error => error.code === "COMPONENT_OVERLAP")).toBe(true);
  });
});
