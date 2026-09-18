import { buildingPlanSchema, blockStateSchema, type BuildingPlan, type BlockState } from "@minecraft-ai-builder/shared-protocol";
import * as z from "zod/v4";

export const designOrientationSchema = z.enum(["north", "south", "east", "west"]);
export const designPositionSchema = z.object({ x: z.int(), y: z.int(), z: z.int() });
export const designSizeSchema = z.object({ x: z.int().positive(), y: z.int().positive(), z: z.int().positive() });

const semanticComponentTypes = [
  "wall_with_openings", "framed_wall", "timber_frame_grid", "lattice_window", "door_assembly",
  "veranda", "balcony", "railing", "entrance_canopy", "trim_band", "layered_roof",
  "extended_eaves", "roof_ridge", "gable_infill", "foundation_skirt"
] as const;

export const paletteIntentSchema = z.object({
  requiredTags: z.array(z.string()).default([]),
  preferredTags: z.array(z.string()).default([]),
  requiredKeywords: z.array(z.string()).default([]),
  preferredKeywords: z.array(z.string()).default([]),
  excludedKeywords: z.array(z.string()).default([]),
  preferredNamespaces: z.array(z.string()).default([]),
  preferredIds: z.array(z.string().regex(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/)).default([]),
  requiredProperties: z.record(z.string(), z.string()).default({}),
  preferredProperties: z.record(z.string(), z.string()).default({}),
  fallbackRoles: z.array(z.string()).default([]),
  allowVanillaFallback: z.boolean().default(false)
});

export const componentOpeningSchema = z.object({
  from: designPositionSchema,
  to: designPositionSchema,
  paletteRole: z.string().min(1).default("window")
});

export const semanticComponentSchema = z.object({
  type: z.enum(semanticComponentTypes),
  from: designPositionSchema,
  to: designPositionSchema.optional(),
  paletteRole: z.string().min(1).default("wallPrimary"),
  frameRole: z.string().min(1).optional(),
  fillRole: z.string().min(1).optional(),
  accentRole: z.string().min(1).optional(),
  openings: z.array(componentOpeningSchema).default([]),
  options: z.record(z.string(), z.unknown()).default({})
});

export const roofSchema = z.object({
  type: z.enum(["gable", "hip", "layered_gable", "layered_hip", "flat"]),
  from: designPositionSchema,
  to: designPositionSchema,
  paletteRole: z.string().min(1).default("roofPrimary"),
  trimRole: z.string().min(1).optional(),
  overhang: z.int().nonnegative().default(1),
  layers: z.int().positive().default(1)
});

export const roomSchema = z.object({
  id: z.string().min(1),
  description: z.string().default(""),
  from: designPositionSchema,
  to: designPositionSchema,
  purpose: z.string().optional()
});

export const architecturalDesignSchema = z.object({
  designId: z.string().min(1).regex(/^[a-zA-Z0-9_.-]+$/),
  name: z.string().min(1),
  styleId: z.string().min(1),
  variant: z.string().min(1).default("default"),
  dimension: z.string().regex(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/),
  anchor: designPositionSchema,
  orientation: designOrientationSchema,
  footprint: z.object({ x: z.int().positive(), z: z.int().positive() }),
  floors: z.int().positive(),
  floorHeights: z.array(z.int().positive()).min(1),
  detailLevel: z.enum(["low", "medium", "high"]).default("medium"),
  terrainAdaptation: z.enum(["preserveTerrain", "minimalTerraforming", "flatten", "terrace", "followSlope", "bridgeOverTerrain"]).default("preserveTerrain"),
  paletteIntents: z.record(z.string().min(1), paletteIntentSchema).default({}),
  structuralGrid: z.object({ x: z.int().positive().default(4), z: z.int().positive().default(4) }).default({ x: 4, z: 4 }),
  rooms: z.array(roomSchema).default([]),
  zones: z.array(roomSchema).default([]),
  components: z.array(semanticComponentSchema).min(1),
  roof: roofSchema.optional(),
  facadeRules: z.record(z.string(), z.unknown()).default({}),
  constraints: z.object({ avoidExistingStructures: z.boolean().default(true), maxChanges: z.int().positive().optional() }).default({ avoidExistingStructures: true }),
  metadata: z.record(z.string(), z.unknown()).default({})
});

export type ArchitecturalDesign = z.infer<typeof architecturalDesignSchema>;
export type SemanticComponent = z.infer<typeof semanticComponentSchema>;
export type PaletteIntent = z.infer<typeof paletteIntentSchema>;
export type DesignPosition = z.infer<typeof designPositionSchema>;

export type DesignIssue = { code: string; message: string; component?: number };
export type DesignValidation = { valid: boolean; errors: DesignIssue[]; warnings: DesignIssue[] };

export function validateArchitecturalDesign(input: unknown): DesignValidation & { design?: ArchitecturalDesign } {
  const parsed = architecturalDesignSchema.safeParse(input);
  if (!parsed.success) return { valid: false, errors: parsed.error.issues.map(issue => ({ code: "SCHEMA_INVALID", message: `${issue.path.join(".") || "design"}: ${issue.message}` })), warnings: [] };
  const design = parsed.data;
  const errors: DesignIssue[] = [], warnings: DesignIssue[] = [];
  if (design.floorHeights.length !== design.floors) errors.push({ code: "FLOOR_HEIGHT_MISMATCH", message: "floorHeights must contain exactly one height per floor" });
  if (design.footprint.x < 3 || design.footprint.z < 3) errors.push({ code: "FOOTPRINT_TOO_SMALL", message: "footprint must be at least 3 by 3" });
  const totalHeight = design.floorHeights.reduce((sum, value) => sum + value, 0);
  for (const [index, component] of design.components.entries()) {
    const to = component.to ?? component.from;
    if (to.x < component.from.x || to.y < component.from.y || to.z < component.from.z) errors.push({ code: "INVALID_COMPONENT_BOUNDS", message: `${component.type} has inverted bounds`, component: index });
    const allowsOverhang = ["extended_eaves", "layered_roof", "veranda", "balcony", "entrance_canopy"].includes(component.type);
    const outside = component.from.x < (allowsOverhang ? -4 : 0) || component.from.z < (allowsOverhang ? -4 : 0) || to.x >= design.footprint.x + (allowsOverhang ? 4 : 0) || to.z >= design.footprint.z + (allowsOverhang ? 4 : 0) || component.from.y < 0 || to.y > totalHeight + 8;
    if (outside) errors.push({ code: "COMPONENT_OUT_OF_BOUNDS", message: `${component.type} is outside the design footprint/height`, component: index });
    if (component.type === "wall_with_openings" && component.openings.length === 0) warnings.push({ code: "WALL_WITHOUT_OPENINGS", message: "wall_with_openings has no openings", component: index });
    if (component.type === "door_assembly" && to.y - component.from.y < 1) errors.push({ code: "DOOR_HEIGHT_TOO_SMALL", message: "door_assembly requires at least two vertical blocks", component: index });
    for (const [openingIndex, opening] of component.openings.entries()) {
      if (opening.to.x < opening.from.x || opening.to.y < opening.from.y || opening.to.z < opening.from.z) errors.push({ code: "INVALID_OPENING_BOUNDS", message: `${component.type} opening ${openingIndex} has inverted bounds`, component: index });
      if (!boxesOverlap(component.from, to, opening.from, opening.to)) errors.push({ code: "OPENING_OUT_OF_COMPONENT", message: `${component.type} opening ${openingIndex} is outside its component`, component: index });
    }
  }
  for (let left = 0; left < design.components.length; left++) for (let right = left + 1; right < design.components.length; right++) {
    const a = design.components[left], b = design.components[right];
    if (!a || !b) continue;
    const aTo = a.to ?? a.from, bTo = b.to ?? b.from;
    if (a.options.allowOverlap !== true && b.options.allowOverlap !== true && boxesOverlap(a.from, aTo, b.from, bTo)) errors.push({ code: "COMPONENT_OVERLAP", message: `${a.type} overlaps ${b.type}; set options.allowOverlap=true only for intentional layering`, component: right });
  }
  if (design.components.length < 3) warnings.push({ code: "LOW_COMPONENT_DIVERSITY", message: "Design has fewer than three semantic components" });
  return { valid: errors.length === 0, errors, warnings, design };
}

function rotateLocal(position: DesignPosition, orientation: ArchitecturalDesign["orientation"]): DesignPosition {
  switch (orientation) {
    case "north": return position;
    case "south": return { x: -position.x, y: position.y, z: -position.z };
    case "east": return { x: position.z, y: position.y, z: -position.x };
    case "west": return { x: -position.z, y: position.y, z: position.x };
  }
}

function boxesOverlap(aFrom: DesignPosition, aTo: DesignPosition, bFrom: DesignPosition, bTo: DesignPosition): boolean {
  return aFrom.x <= bTo.x && aTo.x >= bFrom.x && aFrom.y <= bTo.y && aTo.y >= bFrom.y && aFrom.z <= bTo.z && aTo.z >= bFrom.z;
}

export function localToWorld(anchor: DesignPosition, position: DesignPosition, orientation: ArchitecturalDesign["orientation"]): DesignPosition {
  const rotated = rotateLocal(position, orientation);
  return { x: anchor.x + rotated.x, y: anchor.y + rotated.y, z: anchor.z + rotated.z };
}

type PlanOperation = BuildingPlan["operations"][number];
const componentNeedsRange = (component: SemanticComponent) => component.to ?? component.from;
type StyleProfile = { id: string; defaultRoof: "gable" | "hip"; defaultEaves: number };

function styleProfile(styleId: string): StyleProfile {
  const id = styleId.toLowerCase();
  if (id.includes("hip") || id.includes("manor") || id.includes("colonial")) return { id: "hip-profile", defaultRoof: "hip", defaultEaves: 1 };
  if (id.includes("japanese") || id.includes("minka") || id.includes("rural") || id.includes("cottage")) return { id: "deep-eaves-gable-profile", defaultRoof: "gable", defaultEaves: 2 };
  return { id: "generic-gable-profile", defaultRoof: "gable", defaultEaves: 1 };
}

function op(type: PlanOperation["type"], from: DesignPosition, to: DesignPosition | undefined, paletteRole: string, properties?: Record<string, string>, options: Record<string, unknown> = {}): PlanOperation {
  return { type, from, ...(to ? { to } : {}), paletteRole, ...(properties ? { properties } : {}), options } as PlanOperation;
}

function optionInt(component: SemanticComponent, key: string, fallback: number): number {
  const value = component.options[key];
  return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : fallback;
}

function optionString(component: SemanticComponent, key: string): string | undefined {
  const value = component.options[key];
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function compileComponent(component: SemanticComponent, profile: StyleProfile): PlanOperation[] {
  const from = component.from, to = componentNeedsRange(component), frame = component.frameRole ?? component.accentRole ?? component.paletteRole;
  switch (component.type) {
    case "wall_with_openings": {
      if (!component.openings.length) return [op("wall", from, to, component.paletteRole)];
      return [op("wall", from, to, component.paletteRole, undefined, { openings: component.openings })];
    }
    case "framed_wall": return [op("wall", from, to, component.fillRole ?? component.paletteRole), op("beam", from, { x: to.x, y: from.y, z: from.z }, frame), op("beam", { x: from.x, y: to.y, z: from.z }, { x: to.x, y: to.y, z: from.z }, frame), op("beam", from, { x: from.x, y: to.y, z: to.z }, frame), op("beam", { x: to.x, y: from.y, z: to.z }, { x: to.x, y: to.y, z: to.z }, frame)];
    case "timber_frame_grid": {
      const result: PlanOperation[] = [op("wall", from, to, component.fillRole ?? component.paletteRole)];
      const spacingX = optionInt(component, "spacingX", 4), spacingY = optionInt(component, "spacingY", 3);
      for (let x = from.x; x <= to.x; x += spacingX) result.push(op("column", { x, y: from.y, z: from.z }, { x, y: to.y, z: from.z }, frame));
      for (let y = from.y; y <= to.y; y += spacingY) result.push(op("beam", { x: from.x, y, z: from.z }, { x: to.x, y, z: from.z }, frame));
      return result;
    }
    case "lattice_window": {
      const result: PlanOperation[] = [];
      for (let x = from.x; x <= to.x; x++) for (let y = from.y; y <= to.y; y++) result.push(op("set_block", { x, y, z: from.z }, undefined, component.fillRole ?? component.paletteRole, undefined, { lattice: true }));
      result.push(op("beam", from, { x: to.x, y: from.y, z: from.z }, frame), op("beam", { x: from.x, y: to.y, z: from.z }, { x: to.x, y: to.y, z: from.z }, frame));
      return result;
    }
    case "door_assembly": {
      // A doorway operation historically used the door palette for the
      // surrounding wall. Emit actual lower/upper door states instead, and
      // add a frame only when a separate frame role is supplied.
      const doorProperties: Record<string, string> = {};
      const hinge = optionString(component, "hinge");
      if (hinge === "left" || hinge === "right") doorProperties.hinge = hinge;
      const result: PlanOperation[] = [
        op("set_block", from, undefined, component.paletteRole, { ...doorProperties, half: "lower" }),
        op("set_block", { x: from.x, y: from.y + 1, z: from.z }, undefined, component.paletteRole, { ...doorProperties, half: "upper" })
      ];
      if (component.frameRole || component.accentRole) {
        result.push(
          op("beam", { x: from.x - 1, y: from.y, z: from.z }, { x: from.x - 1, y: to.y, z: from.z }, frame),
          op("beam", { x: from.x + 1, y: from.y, z: from.z }, { x: from.x + 1, y: to.y, z: from.z }, frame),
          op("beam", { x: from.x - 1, y: to.y, z: from.z }, { x: from.x + 1, y: to.y, z: from.z }, frame)
        );
      }
      return result;
    }
    case "veranda": return [op("floor", from, to, component.paletteRole), op("fence", { x: from.x, y: from.y + 1, z: from.z }, { x: to.x, y: from.y + 1, z: to.z }, component.accentRole ?? frame), op("column", { x: from.x, y: from.y, z: from.z }, { x: from.x, y: to.y, z: from.z }, frame), op("column", { x: to.x, y: from.y, z: to.z }, { x: to.x, y: to.y, z: to.z }, frame)];
    case "balcony": return [op("floor", from, to, component.paletteRole), op("fence", { x: from.x, y: from.y + 1, z: from.z }, { x: to.x, y: from.y + 1, z: to.z }, component.accentRole ?? frame)];
    case "railing": return [op("fence", from, to, component.paletteRole)];
    case "entrance_canopy": return [op("flat_roof", from, to, component.paletteRole), op("column", from, { x: from.x, y: to.y, z: from.z }, frame), op("column", { x: to.x, y: from.y, z: to.z }, to, frame)];
    case "trim_band": return [op("beam", from, to, component.paletteRole)];
    case "layered_roof": {
      const result: PlanOperation[] = [];
      const layers = optionInt(component, "layers", 2);
      const roofType = component.options.roofType === "hip" || (component.options.roofType === undefined && profile.defaultRoof === "hip") ? "hip_roof" : "gable_roof";
      for (let layer = 0; layer < layers; layer++) { const inset = layer; result.push(op(roofType, { x: from.x + inset, y: from.y + layer, z: from.z + inset }, { x: to.x - inset, y: to.y + layer, z: to.z - inset }, component.paletteRole, undefined, { layer, roofType })); }
      return result;
    }
    case "extended_eaves": return [op("flat_roof", from, to, component.paletteRole, undefined, { overhang: component.options.overhang ?? profile.defaultEaves })];
    case "roof_ridge": return [op("beam", from, to, component.paletteRole)];
    case "gable_infill": return [op("wall", from, to, component.paletteRole)];
    case "foundation_skirt": return [op("foundation", from, to, component.paletteRole)];
  }
}

function boundsOf(operations: PlanOperation[]): { min: DesignPosition; max: DesignPosition } {
  const points = operations.flatMap(operation => [operation.from, operation.to ?? operation.from]);
  return { min: { x: Math.min(...points.map(p => p.x)), y: Math.min(...points.map(p => p.y)), z: Math.min(...points.map(p => p.z)) }, max: { x: Math.max(...points.map(p => p.x)), y: Math.max(...points.map(p => p.y)), z: Math.max(...points.map(p => p.z)) } };
}

function worldOperation(operation: PlanOperation, design: ArchitecturalDesign): PlanOperation {
  const options = operation.options as Record<string, unknown> | undefined;
  const openings = Array.isArray(options?.openings) ? options.openings.map(value => {
    if (!value || typeof value !== "object") return value;
    const opening = value as { from?: DesignPosition; to?: DesignPosition; paletteRole?: string };
    if (!opening.from || !opening.to) return value;
    return { ...opening, from: localToWorld(design.anchor, opening.from, design.orientation), to: localToWorld(design.anchor, opening.to, design.orientation) };
  }) : undefined;
  return { ...operation, from: localToWorld(design.anchor, operation.from, design.orientation), ...(operation.to ? { to: localToWorld(design.anchor, operation.to, design.orientation) } : {}), ...(openings ? { options: { ...options, openings } } : {}) };
}

export function compileArchitecturalDesign(designInput: ArchitecturalDesign, palette: Record<string, BlockState>): BuildingPlan {
  const validation = validateArchitecturalDesign(designInput);
  if (!validation.valid || !validation.design) throw new Error(validation.errors.map(error => `${error.code}: ${error.message}`).join("; "));
  const design = validation.design;
  const profile = styleProfile(design.styleId);
  const localOperations = design.components.flatMap(component => compileComponent(component, profile));
  if (design.roof) {
    const roof = design.roof;
    const roofComponent: SemanticComponent = { type: roof.type === "flat" ? "extended_eaves" : "layered_roof", from: roof.from, to: roof.to, paletteRole: roof.paletteRole, frameRole: roof.trimRole, options: { layers: roof.layers, overhang: roof.overhang, roofType: roof.type.includes("hip") ? "hip" : "gable" }, openings: [] };
    localOperations.push(...compileComponent(roofComponent, profile));
  }
  const operations = localOperations.map(operation => worldOperation(operation, design));
  const bounds = boundsOf(operations);
  const plan = {
    id: design.designId,
    anchor: design.anchor,
    orientation: design.orientation,
    dimension: design.dimension,
    boundingBox: { min: bounds.min, max: bounds.max },
    dimensions: { x: bounds.max.x - bounds.min.x + 1, y: bounds.max.y - bounds.min.y + 1, z: bounds.max.z - bounds.min.z + 1 },
    floors: design.floors,
    style: design.styleId,
    palette,
    terrainAdaptation: design.terrainAdaptation,
    sections: [...design.rooms, ...design.zones].map(room => ({ id: room.id, description: room.description })),
    operations,
    constraints: design.constraints,
    metadata: { ...design.metadata, variant: design.variant, detailLevel: design.detailLevel, styleProfile: profile.id, architecturalDesign: true }
  };
  return buildingPlanSchema.parse(plan);
}

export function architecturalDesignExample(): ArchitecturalDesign {
  return architecturalDesignSchema.parse({
    designId: "japanese-minka-example", name: "Japanese Minka", styleId: "japanese_minka", variant: "rural", dimension: "minecraft:overworld",
    anchor: { x: 0, y: 64, z: 0 }, orientation: "north", footprint: { x: 13, z: 11 }, floors: 2, floorHeights: [4, 3], detailLevel: "high",
    paletteIntents: { structuralWood: { preferredKeywords: ["spruce", "stripped"] }, wallPrimary: { preferredKeywords: ["white", "quartz", "calcite"] }, roofPrimary: { preferredKeywords: ["deepslate", "dark"] }, window: { preferredKeywords: ["glass", "pane"] } },
    components: [
      { type: "foundation_skirt", from: { x: 0, y: 0, z: 0 }, to: { x: 12, y: 0, z: 10 }, paletteRole: "foundation", openings: [], options: {} },
      { type: "framed_wall", from: { x: 0, y: 1, z: 0 }, to: { x: 12, y: 7, z: 10 }, paletteRole: "wallPrimary", frameRole: "structuralWood", openings: [], options: {} },
      { type: "veranda", from: { x: 1, y: 1, z: -2 }, to: { x: 11, y: 1, z: -1 }, paletteRole: "floorPrimary", frameRole: "structuralWood", accentRole: "railing", openings: [], options: {} },
      { type: "layered_roof", from: { x: -1, y: 8, z: -1 }, to: { x: 13, y: 9, z: 11 }, paletteRole: "roofPrimary", frameRole: "roofTrim", openings: [], options: { layers: 2 } }
    ],
    constraints: { avoidExistingStructures: true }, metadata: {}
  });
}
