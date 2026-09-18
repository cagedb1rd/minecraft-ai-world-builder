import { McpServer } from "@modelcontextprotocol/server";
import { blockStateSchema, buildingPlanSchema } from "@minecraft-ai-builder/shared-protocol";
import * as z from "zod/v4";
import { architecturalDesignSchema, compileArchitecturalDesign, validateArchitecturalDesign, type ArchitecturalDesign } from "./architectural.js";
import { currentVersion, FileDesignSessionStore, type DesignSessionStore, versionByNumber, withCompiledVersion } from "./design-sessions.js";
import { PaletteResolver, type BridgeQueryClient } from "./palette.js";

const unknownDesign = z.object({ design: z.unknown() });
const compileInput = unknownDesign.extend({ palette: z.record(z.string(), blockStateSchema).optional() });
const sessionIdInput = z.object({ sessionId: z.string().uuid() });
const versionInput = sessionIdInput.extend({ version: z.int().positive().optional() });
const sessionContext = z.object({ worldIdentity: z.string().optional(), referencePlayer: z.string().optional() });

type ToolResult = { content: [{ type: "text"; text: string }]; structuredContent: { result: unknown }; isError?: boolean };
const success = (result: unknown): ToolResult => ({ content: [{ type: "text", text: JSON.stringify(result, null, 2) }], structuredContent: { result } });
const failure = (error: unknown): ToolResult => ({ isError: true, content: [{ type: "text", text: error instanceof Error ? error.message : String(error) }], structuredContent: { result: { errors: [error instanceof Error ? error.message : String(error)] } } });

function requiredRoles(design: ArchitecturalDesign): string[] {
  const roles = new Set<string>();
  for (const component of design.components) for (const role of [component.paletteRole, component.frameRole, component.fillRole, component.accentRole]) if (role) roles.add(role);
  if (design.roof) for (const role of [design.roof.paletteRole, design.roof.trimRole]) if (role) roles.add(role);
  return [...roles];
}

function validateRoles(design: ArchitecturalDesign, palette: Record<string, unknown>): string[] { return requiredRoles(design).filter(role => !palette[role]).map(role => `Missing palette role: ${role}`); }

async function compile(bridge: BridgeQueryClient, raw: unknown, explicitPalette?: Record<string, z.infer<typeof blockStateSchema>>) {
  const validation = validateArchitecturalDesign(raw);
  if (!validation.valid || !validation.design) throw new Error(JSON.stringify(validation));
  const design = validation.design;
  const resolved = explicitPalette ? { palette: explicitPalette, selections: [], warnings: [], cacheKey: "explicit" } : await new PaletteResolver(bridge).resolve(design);
  const roleErrors = validateRoles(design, resolved.palette);
  if (roleErrors.length) throw new Error(roleErrors.join("; "));
  const plan = compileArchitecturalDesign(design, resolved.palette);
  return { design, resolvedPalette: resolved, plan, validation };
}

function updateVersion(session: Awaited<ReturnType<DesignSessionStore["get"]>>, version: number, patch: Parameters<typeof withCompiledVersion>[2]): NonNullable<typeof session> {
  if (!session) throw new Error("Design session not found");
  return withCompiledVersion(session, version, patch);
}

export const ARCHITECTURAL_TOOL_NAMES = [
  "validate_architectural_design", "resolve_design_palette", "validate_resolved_palette", "compile_architectural_design", "estimate_architectural_design", "preview_architectural_design",
  "create_design_session", "get_design_session", "list_design_sessions", "revise_design_session", "list_design_versions", "compare_design_versions", "select_design_version", "compile_design_session", "preview_design_session", "approve_design_session", "execute_design_session"
] as const;

export function registerArchitecturalTools(server: McpServer, bridge: BridgeQueryClient, store: DesignSessionStore = new FileDesignSessionStore("minecraft-ai-builder-data/design-sessions")): void {
  server.registerTool("validate_architectural_design", { description: "验证高层 ArchitecturalDesign 的尺寸、构件、坐标和材料角色", inputSchema: unknownDesign }, async ({ design }) => success(validateArchitecturalDesign(design)));
  server.registerTool("resolve_design_palette", { description: "按照当前 Minecraft Registry 为设计材料角色选择真实 BlockState", inputSchema: unknownDesign }, async ({ design }) => { try { const validation = validateArchitecturalDesign(design); if (!validation.valid || !validation.design) return failure(new Error(JSON.stringify(validation))); return success(await new PaletteResolver(bridge).resolve(validation.design)); } catch (error) { return failure(error); } });
  server.registerTool("validate_resolved_palette", { description: "验证设计使用的材料是否仍存在于当前 Minecraft Registry", inputSchema: z.object({ palette: z.record(z.string(), z.unknown()) }) }, async ({ palette }) => { try { return success(await new PaletteResolver(bridge).validate(palette)); } catch (error) { return failure(error); } });
  server.registerTool("compile_architectural_design", { description: "解析当前 Registry 材料并把 ArchitecturalDesign 编译为稳定 BuildingPlan", inputSchema: compileInput }, async ({ design, palette }) => { try { return success(await compile(bridge, design, palette)); } catch (error) { return failure(error); } });
  server.registerTool("estimate_architectural_design", { description: "编译 ArchitecturalDesign 并请求 Bridge 估算方块变化", inputSchema: compileInput }, async ({ design, palette }) => { try { const result = await compile(bridge, design, palette); const estimate = await bridge.call("estimate_build", { plan: result.plan }); return success({ ...result, estimate }); } catch (error) { return failure(error); } });
  server.registerTool("preview_architectural_design", { description: "编译 ArchitecturalDesign 并调用现有 BuildingPlan preview，不修改世界", inputSchema: compileInput }, async ({ design, palette }) => { try { const result = await compile(bridge, design, palette); const preview = await bridge.call("preview_build", { plan: result.plan }); return success({ ...result, preview }); } catch (error) { return failure(error); } });

  server.registerTool("create_design_session", { description: "创建可修改、可预览、可批准的建筑设计会话", inputSchema: unknownDesign.extend(sessionContext.shape) }, async ({ design, worldIdentity, referencePlayer }) => { try { const parsed = architecturalDesignSchema.parse(design); const session = await store.create(parsed, { ...(worldIdentity ? { worldIdentity } : {}), ...(referencePlayer ? { referencePlayer } : {}) }); return success(session); } catch (error) { return failure(error); } });
  server.registerTool("get_design_session", { description: "读取一个建筑设计会话及当前版本", inputSchema: sessionIdInput }, async ({ sessionId }) => { const session = await store.get(sessionId); return session ? success(session) : failure(new Error(`Design session not found: ${sessionId}`)); });
  server.registerTool("list_design_sessions", { description: "列出本地建筑设计会话", inputSchema: z.object({}) }, async () => success(await store.list()));
  server.registerTool("revise_design_session", { description: "用新的 ArchitecturalDesign 创建不可变的新版本", inputSchema: sessionIdInput.extend(unknownDesign.shape) }, async ({ sessionId, design }) => { try { return success(await store.revise(sessionId, architecturalDesignSchema.parse(design))); } catch (error) { return failure(error); } });
  server.registerTool("list_design_versions", { description: "列出设计会话的所有版本", inputSchema: sessionIdInput }, async ({ sessionId }) => { const session = await store.get(sessionId); return session ? success({ sessionId, currentVersion: session.currentVersion, versions: session.versions }) : failure(new Error(`Design session not found: ${sessionId}`)); });
  server.registerTool("compare_design_versions", { description: "比较设计会话中的两个版本元数据和操作数量", inputSchema: sessionIdInput.extend({ fromVersion: z.int().positive(), toVersion: z.int().positive() }) }, async ({ sessionId, fromVersion, toVersion }) => { const session = await store.get(sessionId); if (!session) return failure(new Error(`Design session not found: ${sessionId}`)); const from = session.versions.find(item => item.version === fromVersion), to = session.versions.find(item => item.version === toVersion); if (!from || !to) return failure(new Error("Design version not found")); return success({ sessionId, fromVersion, toVersion, changed: JSON.stringify(from.design) !== JSON.stringify(to.design), fromStatus: from.status, toStatus: to.status, fromPlanOperations: Array.isArray((from.compiledPlan as { operations?: unknown[] } | undefined)?.operations) ? (from.compiledPlan as { operations: unknown[] }).operations.length : null, toPlanOperations: Array.isArray((to.compiledPlan as { operations?: unknown[] } | undefined)?.operations) ? (to.compiledPlan as { operations: unknown[] }).operations.length : null }); });
  server.registerTool("select_design_version", { description: "切换会话当前版本；不会执行世界修改", inputSchema: sessionIdInput.extend({ version: z.int().positive() }) }, async ({ sessionId, version }) => { const session = await store.get(sessionId); if (!session) return failure(new Error(`Design session not found: ${sessionId}`)); if (!session.versions.some(item => item.version === version)) return failure(new Error(`Design version ${version} is missing`)); session.currentVersion = version; session.updatedAt = new Date().toISOString(); session.execution = {}; await store.update(session); return success(session); });

  server.registerTool("compile_design_session", { description: "解析会话版本的材料并保存编译后的 BuildingPlan", inputSchema: versionInput }, async ({ sessionId, version }) => { try { const session = await store.get(sessionId); if (!session) throw new Error(`Design session not found: ${sessionId}`); const target = versionByNumber(session, version); const result = await compile(bridge, target.design); const updated = updateVersion(session, target.version, { resolvedPalette: result.resolvedPalette, compiledPlan: result.plan, validation: result.validation, status: "compiled" }); await store.update(updated); return success({ session: updated, result }); } catch (error) { return failure(error); } });
  server.registerTool("preview_design_session", { description: "编译并预览会话版本；不会修改世界", inputSchema: versionInput }, async ({ sessionId, version }) => { try { const session = await store.get(sessionId); if (!session) throw new Error(`Design session not found: ${sessionId}`); const target = versionByNumber(session, version); const result = await compile(bridge, target.design); const preview = await bridge.call("preview_build", { plan: result.plan }); const updated = updateVersion(session, target.version, { resolvedPalette: result.resolvedPalette, compiledPlan: result.plan, validation: result.validation, preview, status: "previewed" }); await store.update(updated); return success({ session: updated, result: { ...result, preview } }); } catch (error) { return failure(error); } });
  server.registerTool("approve_design_session", { description: "批准已经 preview 且有效的设计版本", inputSchema: versionInput }, async ({ sessionId, version }) => { const session = await store.get(sessionId); if (!session) return failure(new Error(`Design session not found: ${sessionId}`)); const target = versionByNumber(session, version); const preview = target.preview as { valid?: unknown } | undefined; if (target.status !== "previewed" || !preview || preview.valid !== true) return failure(new Error("Design version must have a valid preview before approval")); const updated = updateVersion(session, target.version, { status: "approved" }); await store.update(updated); return success(updated); });
  server.registerTool("execute_design_session", { description: "执行已批准的设计版本；仍然通过现有 execute_building_plan 和 Bridge Transaction", inputSchema: versionInput.extend({ referencePlayer: z.string().optional(), experimentId: z.string().uuid().optional() }) }, async ({ sessionId, version, referencePlayer, experimentId }) => { try { const session = await store.get(sessionId); if (!session) throw new Error(`Design session not found: ${sessionId}`); const target = versionByNumber(session, version); if (target.status !== "approved") throw new Error("Design version must be approved after preview before execution"); const plan = buildingPlanSchema.parse(target.compiledPlan); const validation = await bridge.call("validate_building_plan", { plan }); if (!validation || typeof validation !== "object" || (validation as Record<string, unknown>).valid !== true) throw new Error("Approved design no longer passes Bridge BuildingPlan validation"); const result = await bridge.call("execute_building_plan", { plan, ...(referencePlayer ? { referencePlayer } : {}), ...(experimentId ? { experimentId } : {}) }); const value = result && typeof result === "object" ? result as Record<string, unknown> : {}; session.execution = { ...(typeof value.buildId === "string" ? { buildId: value.buildId } : {}), ...(typeof value.transactionId === "string" ? { transactionId: value.transactionId } : {}) }; const updated = updateVersion(session, target.version, { status: "executed" }); await store.update(updated); return success({ session: updated, result }); } catch (error) { return failure(error); } });
}
