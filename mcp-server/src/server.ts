import { McpServer } from "@modelcontextprotocol/server";
import * as z from "zod/v4";
import { buildingPlanSchema, blockStateSchema, type Operation } from "@minecraft-ai-builder/shared-protocol";
import { BridgeCallError, BridgeClient } from "./bridge-client.js";
import { skillSchema, type SkillStore } from "./skills.js";
import { registerArchitecturalTools, ARCHITECTURAL_TOOL_NAMES } from "./architectural-tools.js";
import type { DesignSessionStore } from "./design-sessions.js";

const empty = z.object({});
const position = z.object({ x: z.int(), y: z.int(), z: z.int() });
const dimension = z.string().regex(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/);
const region = z.object({ dimension, min: position, max: position });
const commonBuild = region.extend({ state: blockStateSchema, orientation: z.enum(["north", "south", "east", "west"]).optional(), summary: z.string().default("MCP build"), referencePlayer: z.string().optional(), experimentId: z.string().uuid().optional() });
const bridgeTools: Array<[Operation, string, z.ZodType]> = [
  ["handshake", "读取当前 Minecraft 服务端的协议和能力", empty],
  ["get_world_info", "读取活动服务端世界、主机状态和可用能力", empty],
  ["get_dimension", "读取维度时间、天气、世界高度和难度", z.object({ dimension })],
  ["get_players", "列出仅用于参考定位的玩家；不能控制玩家", empty],
  ["get_player_reference_position", "读取指定玩家的位置和朝向", z.object({ player: z.string().optional() })],
  ["get_block", "读取一个服务端方块", z.object({ dimension, position })],
  ["scan_area", "以 palette+RLE 压缩格式扫描有界区域", region],
  ["get_heightmap", "读取有界 XZ 区域的世界表面高度图", z.object({ dimension, minX: z.int(), minZ: z.int(), maxX: z.int(), maxZ: z.int() })],
  ["get_biome", "读取一个位置的 Biome", z.object({ dimension, position })],
  ["get_block_entity_info", "保守读取 BlockEntity 类型和脱敏 NBT", z.object({ dimension, position })],
  ["get_region_summary", "读取区域方块直方图、液体与复杂方块摘要", region],
  ["list_mods", "动态列出 Forge 运行时 Mod", empty],
  ["list_blocks", "分页列出运行时 Block Registry", z.object({ offset: z.int().nonnegative().default(0), limit: z.int().min(1).max(500).default(100) })],
  ["list_items", "分页列出运行时 Item Registry", z.object({ offset: z.int().nonnegative().default(0), limit: z.int().min(1).max(500).default(100) })],
  ["search_blocks", "搜索运行时 Block Registry", z.object({ query: z.string().min(1), limit: z.int().min(1).max(100).default(20) })],
  ["search_items", "搜索运行时 Item Registry", z.object({ query: z.string().min(1), limit: z.int().min(1).max(100).default(20) })],
  ["search_recipes", "搜索当前服务端实际加载的配方及其输入输出物品", z.object({ query: z.string().default(""), limit: z.int().min(1).max(100).default(20) })],
  ["get_block_info", "读取方块、标签及全部允许的 BlockState 值", z.object({ id: z.string() })],
  ["get_block_states", "读取方块允许的全部 BlockState 属性值", z.object({ id: z.string() })],
  ["get_item_info", "读取物品和标签", z.object({ id: z.string() })],
  ["get_tags", "读取 block 或 item Registry 标签", z.object({ registry: z.enum(["block", "item"]), namespace: z.string().optional() })],
  ["get_mod_capabilities", "检测通用和 Mod Adapter 能力，包括 Create 状态", empty],
  ["set_block", "在可撤销事务中放置一个方块", z.object({ dimension, position, state: blockStateSchema, summary: z.string().default("MCP set_block"), referencePlayer: z.string().optional() })],
  ["set_blocks_batch", "把一批方块作为一个分 tick 事务排队", z.object({ dimension, blocks: z.array(z.object({ position, state: blockStateSchema })).min(1), summary: z.string().default("MCP batch"), referencePlayer: z.string().optional(), experimentId: z.string().uuid().optional() })],
  ["fill_region", "分 tick 填充区域", commonBuild],
  ["replace_region", "分 tick 替换区域中匹配的方块", commonBuild.extend({ match: z.string() })],
  ["clear_region", "分 tick 清空区域", region.extend({ summary: z.string().default("MCP clear"), referencePlayer: z.string().optional(), experimentId: z.string().uuid().optional() })],
  ["copy_region", "复制有界区域到 Bridge 内存剪贴板", region],
  ["paste_region", "分 tick 粘贴剪贴板", z.object({ dimension, clipboardId: z.string().uuid(), target: position, rotation: z.enum(["none", "clockwise_90", "clockwise_180", "counterclockwise_90"]).default("none"), mirror: z.enum(["none", "left_right", "front_back"]).default("none"), summary: z.string().default("MCP paste"), referencePlayer: z.string().optional(), experimentId: z.string().uuid().optional() })],
  ["rotate_region", "把区域绕 Y 轴旋转并作为事务执行", region.extend({ rotation: z.enum(["clockwise_90", "clockwise_180", "counterclockwise_90"]), summary: z.string().default("MCP rotate"), referencePlayer: z.string().optional() })],
  ["mirror_region", "镜像区域并作为事务执行", region.extend({ mirror: z.enum(["left_right", "front_back"]), summary: z.string().default("MCP mirror"), referencePlayer: z.string().optional() })],
  ...(["build_floor", "build_wall", "build_box", "build_column", "build_beam", "build_window", "build_doorway", "build_stairs", "build_staircase", "build_roof", "build_gable_roof", "build_hip_roof", "build_flat_roof", "build_circle", "build_cylinder", "build_tower", "build_platform", "build_foundation", "build_path", "build_road", "build_fence", "build_arch", "build_bridge"] as Operation[]).map(name => [name, `确定性几何工具 ${name}`, commonBuild] as [Operation,string,z.ZodType]),
  ["get_build_status", "读取排队或运行中的施工进度", z.object({ buildId: z.string().uuid() })],
  ["cancel_build", "安全停止施工并保留可撤销的已执行部分", z.object({ buildId: z.string().uuid() })],
  ["undo_build", "把撤销任务分 tick 排队；使用返回的 buildId 查询完成状态", z.object({ transactionId: z.string().uuid() })],
  ["redo_build", "把重做任务分 tick 排队；使用返回的 buildId 查询完成状态", z.object({ transactionId: z.string().uuid() })],
  ["get_build_history", "列出事务历史", empty],
  ["validate_building_plan", "验证 BuildingPlan、材料、边界和限制", z.object({ plan: buildingPlanSchema })],
  ["estimate_build", "估算 BuildingPlan 的确定性方块变化", z.object({ plan: buildingPlanSchema })],
  ["preview_build", "不修改世界地预览覆盖、材料和 BlockEntity 风险", z.object({ plan: buildingPlanSchema })],
  ["execute_building_plan", "把已验证 BuildingPlan 作为分 tick 事务执行", z.object({ plan: buildingPlanSchema, referencePlayer: z.string().optional(), experimentId: z.string().uuid().optional() })],
  ["create_experiment_workspace", "在显式有界区域创建可复原实验工作区", region],
  ["inspect_experiment", "检查实验工作区状态", z.object({ experimentId: z.string().uuid() })],
  ["reset_experiment_workspace", "把实验工作区恢复到创建时快照", z.object({ experimentId: z.string().uuid(), referencePlayer: z.string().optional() })]
];
export const BRIDGE_TOOL_NAMES = bridgeTools.map(([name]) => name);
export { ARCHITECTURAL_TOOL_NAMES };

function output(result: unknown) { return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }], structuredContent: { result } }; }
function failure(error: unknown) { const details=error instanceof BridgeCallError?{errors:error.response.errors}:{errors:[{code:"MCP_ERROR",message:error instanceof Error?error.message:String(error)}]};return { isError: true as const, content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],structuredContent:details }; }

export function createServer(bridge: BridgeClient, skills?: SkillStore, designSessions?: DesignSessionStore): McpServer {
  const server = new McpServer({ name: "minecraft-ai-world-builder", version: "0.2.0" });
  for (const [name, description, inputSchema] of bridgeTools) server.registerTool(name, { description, inputSchema }, async (args: unknown) => {
    try { return output(await bridge.call(name, args as Record<string, unknown>)); } catch (error) { return failure(error); }
  });
  registerArchitecturalTools(server, bridge, designSessions);
  if (skills) {
    server.registerTool("list_skills", { description: "列出持久化 Mod 技能", inputSchema: empty }, async () => output(await skills.list()));
    server.registerTool("get_skill", { description: "读取一个持久化技能", inputSchema: z.object({ skillId: z.string() }) }, async ({ skillId }) => { const skill = await skills.get(skillId); return skill ? output(skill) : failure(new Error(`Skill not found: ${skillId}`)); });
    server.registerTool("save_skill", { description: "验证并原子保存技能", inputSchema: z.object({ skill: skillSchema }) }, async ({ skill }) => { try { await skills.save(skill); return output(skill); } catch (error) { return failure(error); } });
    server.registerTool("revalidate_skill", { description: "按当前 Mod 与 Minecraft 版本检查技能证据", inputSchema: z.object({ skillId: z.string() }) }, async ({ skillId }) => {
      try { const skill = await skills.get(skillId); if (!skill) return failure(new Error(`Skill not found: ${skillId}`)); const world = await bridge.call("get_world_info") as { minecraftVersion?: string }; const mods = await bridge.call("list_mods") as Array<{id:string,version:string}>;
        const current = mods.find(m => m.id === skill.modId); const matches = skill.minecraftVersion === (world.minecraftVersion ?? "1.20.1") && ((!!current && current.version === skill.modVersion) || skill.modId === "minecraft");
        const updated = { ...skill, validated: matches && skill.validated, validationStatus: matches && skill.validated ? "validated" as const : "needs_revalidation" as const, validationResult: matches ? skill.validationResult : "Installed Minecraft or mod version differs from saved evidence", lastValidatedAt: new Date().toISOString() };
        await skills.save(updated); return output(updated); } catch (error) { return failure(error); }
    });
  }
  return server;
}
