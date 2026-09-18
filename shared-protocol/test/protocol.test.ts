import { describe, expect, it } from "vitest";
import { bridgeRequestSchema, bridgeResponseSchema, buildingPlanSchema, PROTOCOL_VERSION } from "../src/index.js";

describe("shared protocol", () => {
  it("validates a versioned request", () => expect(bridgeRequestSchema.safeParse({
    protocolVersion: PROTOCOL_VERSION, requestId: crypto.randomUUID(), operation: "get_world_info",
    arguments: {}, auth: { token: "0123456789abcdef", clientId: "test" }
  }).success).toBe(true));
  it("rejects incomplete plans", () => expect(buildingPlanSchema.safeParse({ id: "x" }).success).toBe(false));
  it("keeps relay-unavailable errors wire-compatible", () => expect(bridgeResponseSchema.safeParse({ protocolVersion: PROTOCOL_VERSION, requestId: "r", success: false, warnings: [], errors: [{ code: "BRIDGE_HOST_UNAVAILABLE", message: "no Forge host" }] }).success).toBe(true));
  it("accepts a complete deterministic plan", () => expect(buildingPlanSchema.safeParse({
    id: "small-floor", anchor: { x: 0, y: 64, z: 0 }, orientation: "north", dimension: "minecraft:overworld",
    boundingBox: { min: { x: 0, y: 64, z: 0 }, max: { x: 2, y: 64, z: 2 } }, dimensions: { x: 3, y: 1, z: 3 },
    floors: 1, style: "test", palette: { floor: { id: "minecraft:oak_planks", properties: {} } }, terrainAdaptation: "preserveTerrain",
    sections: [], operations: [{ type: "floor", from: { x: 0, y: 64, z: 0 }, to: { x: 2, y: 64, z: 2 }, paletteRole: "floor" }],
    constraints: { avoidExistingStructures: true }, metadata: {}
  }).success).toBe(true));
});
