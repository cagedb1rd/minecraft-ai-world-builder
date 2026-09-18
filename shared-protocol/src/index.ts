import { z } from "zod";

export const PROTOCOL_VERSION = "1.0.0" as const;

export const vector3iSchema = z.object({ x: z.int(), y: z.int(), z: z.int() });
export const blockStateSchema = z.object({
  id: z.string().regex(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/),
  properties: z.record(z.string(), z.string()).default({})
});
export type BlockState = z.infer<typeof blockStateSchema>;
export const boundingBoxSchema = z.object({ min: vector3iSchema, max: vector3iSchema });

export const operationSchema = z.enum([
  "handshake", "get_world_info", "get_dimension", "get_players", "get_player_reference_position",
  "get_block", "scan_area", "get_heightmap", "get_biome", "get_block_entity_info", "get_region_summary",
  "list_mods", "list_blocks", "list_items", "search_blocks", "search_items", "search_recipes", "get_block_info", "get_block_states", "get_item_info", "get_tags", "get_mod_capabilities",
  "set_block", "set_blocks_batch", "fill_region", "replace_region", "clear_region", "copy_region", "paste_region", "rotate_region", "mirror_region",
  "build_floor", "build_wall", "build_box", "build_column", "build_beam", "build_window", "build_doorway", "build_stairs", "build_staircase", "build_roof", "build_gable_roof", "build_hip_roof", "build_flat_roof", "build_circle", "build_cylinder", "build_tower", "build_platform", "build_foundation", "build_path", "build_road", "build_fence", "build_arch", "build_bridge",
  "get_build_status", "cancel_build", "undo_build", "redo_build", "get_build_history", "validate_building_plan",
  "estimate_build", "preview_build", "execute_building_plan", "create_experiment_workspace", "inspect_experiment", "reset_experiment_workspace"
]);
export type Operation = z.infer<typeof operationSchema>;

export const bridgeRequestSchema = z.object({
  protocolVersion: z.literal(PROTOCOL_VERSION),
  requestId: z.uuid(),
  operation: operationSchema,
  arguments: z.record(z.string(), z.unknown()).default({}),
  auth: z.object({ token: z.string().min(16), clientId: z.string().min(1).max(128) })
});
export type BridgeRequest = z.infer<typeof bridgeRequestSchema>;

export const errorCodeSchema = z.enum([
  "AUTHENTICATION_FAILED", "PERMISSION_DENIED", "PROTOCOL_VERSION_MISMATCH", "MALFORMED_REQUEST",
  "UNSUPPORTED_OPERATION", "BRIDGE_UNAVAILABLE", "BRIDGE_HOST_UNAVAILABLE", "WORLD_UNAVAILABLE", "TIMEOUT", "LIMIT_EXCEEDED",
  "INVALID_BLOCK_STATE", "TRANSACTION_FAILED", "NOT_FOUND", "MOD_NOT_INSTALLED", "INTERNAL_ERROR"
]);
export const bridgeErrorSchema = z.object({ code: errorCodeSchema, message: z.string(), details: z.unknown().optional() });
export const bridgeResponseSchema = z.object({
  protocolVersion: z.literal(PROTOCOL_VERSION),
  requestId: z.string(),
  success: z.boolean(),
  result: z.unknown().optional(),
  warnings: z.array(z.string()).default([]),
  errors: z.array(bridgeErrorSchema).default([])
});
export type BridgeResponse = z.infer<typeof bridgeResponseSchema>;

export const buildingOperationSchema = z.object({
  type: z.enum(["set_block", "floor", "wall", "box", "column", "beam", "window", "doorway", "stairs", "staircase", "flat_roof", "gable_roof", "hip_roof", "circle", "cylinder", "tower", "platform", "foundation", "path", "road", "fence", "arch", "bridge"]),
  from: vector3iSchema,
  to: vector3iSchema.optional(),
  paletteRole: z.string().min(1),
  properties: z.record(z.string(), z.string()).optional(),
  options: z.record(z.string(), z.unknown()).default({})
});
export const buildingPlanSchema = z.object({
  id: z.string().min(1),
  anchor: vector3iSchema,
  orientation: z.enum(["north", "south", "east", "west"]),
  dimension: z.string().min(1),
  boundingBox: boundingBoxSchema,
  dimensions: z.object({ x: z.int().positive(), y: z.int().positive(), z: z.int().positive() }),
  floors: z.int().positive(),
  style: z.string(),
  palette: z.record(z.string(), blockStateSchema),
  terrainAdaptation: z.enum(["preserveTerrain", "minimalTerraforming", "flatten", "terrace", "followSlope", "bridgeOverTerrain"]),
  sections: z.array(z.object({ id: z.string(), description: z.string() })).default([]),
  operations: z.array(buildingOperationSchema),
  constraints: z.object({ avoidExistingStructures: z.boolean().default(true), maxChanges: z.int().positive().optional() }),
  metadata: z.record(z.string(), z.unknown()).default({})
});
export type BuildingPlan = z.infer<typeof buildingPlanSchema>;
