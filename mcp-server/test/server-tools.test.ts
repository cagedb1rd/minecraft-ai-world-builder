import { describe, expect, it } from "vitest";
import { ARCHITECTURAL_TOOL_NAMES, BRIDGE_TOOL_NAMES } from "../src/server.js";
import { operationSchema } from "@minecraft-ai-builder/shared-protocol";

describe("MCP tool surface", () => {
  it("exposes implemented core paths and no player controls", () => {
    expect(BRIDGE_TOOL_NAMES).toEqual(expect.arrayContaining(["scan_area", "get_heightmap", "list_items", "set_blocks_batch", "fill_region", "copy_region", "paste_region", "execute_building_plan", "cancel_build"]));
    expect(BRIDGE_TOOL_NAMES.some(name => /move_player|camera|attack|inventory|mouse|keyboard/.test(name))).toBe(false);
    expect(BRIDGE_TOOL_NAMES.every(name => operationSchema.safeParse(name).success)).toBe(true);
  });
  it("exposes the deterministic architectural planning surface separately from Bridge operations", () => {
    expect(ARCHITECTURAL_TOOL_NAMES).toEqual(expect.arrayContaining(["validate_architectural_design", "resolve_design_palette", "compile_architectural_design", "create_design_session", "revise_design_session", "preview_design_session", "execute_design_session"]));
    expect(ARCHITECTURAL_TOOL_NAMES.some(name => BRIDGE_TOOL_NAMES.includes(name as never))).toBe(false);
  });
});
