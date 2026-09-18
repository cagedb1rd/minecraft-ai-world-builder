import { mkdir, readFile, readdir, rename, unlink, writeFile } from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import * as z from "zod/v4";
import { architecturalDesignSchema, type ArchitecturalDesign } from "./architectural.js";
import type { ResolvedPalette } from "./palette.js";

const sessionIdSchema = z.string().uuid();
const designVersionSchema = z.object({
  version: z.int().positive(),
  createdAt: z.string().datetime(),
  design: z.unknown(),
  resolvedPalette: z.unknown().optional(),
  compiledPlan: z.unknown().optional(),
  validation: z.unknown().optional(),
  preview: z.unknown().optional(),
  status: z.enum(["draft", "compiled", "previewed", "approved", "executed"]).default("draft")
});
export const designSessionSchema = z.object({
  sessionId: sessionIdSchema,
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  worldIdentity: z.string().default("unknown"),
  dimension: z.string(),
  referencePlayer: z.string().optional(),
  anchor: z.object({ x: z.int(), y: z.int(), z: z.int() }),
  currentVersion: z.int().positive(),
  versions: z.array(designVersionSchema).min(1),
  execution: z.object({ buildId: z.string().optional(), transactionId: z.string().optional() }).default({})
});
export type DesignSession = z.infer<typeof designSessionSchema>;
export type DesignVersion = z.infer<typeof designVersionSchema>;

export interface DesignSessionStore {
  create(design: ArchitecturalDesign, context?: { worldIdentity?: string; referencePlayer?: string }): Promise<DesignSession>;
  get(sessionId: string): Promise<DesignSession | undefined>;
  list(): Promise<DesignSession[]>;
  revise(sessionId: string, design: ArchitecturalDesign): Promise<DesignSession>;
  update(session: DesignSession): Promise<void>;
  remove(sessionId: string): Promise<void>;
}

function now(): string { return new Date().toISOString(); }
function validateId(id: string): string { return sessionIdSchema.parse(id); }

export class FileDesignSessionStore implements DesignSessionStore {
  constructor(private readonly root: string) {}
  private file(id: string): string { return path.join(this.root, `${validateId(id)}.json`); }
  async create(design: ArchitecturalDesign, context: { worldIdentity?: string; referencePlayer?: string } = {}): Promise<DesignSession> {
    const timestamp = now();
    const session = designSessionSchema.parse({ sessionId: randomUUID(), createdAt: timestamp, updatedAt: timestamp, worldIdentity: context.worldIdentity ?? "unknown", dimension: design.dimension, referencePlayer: context.referencePlayer, anchor: design.anchor, currentVersion: 1, versions: [{ version: 1, createdAt: timestamp, design, status: "draft" }], execution: {} });
    await this.update(session);
    return session;
  }
  async get(sessionId: string): Promise<DesignSession | undefined> { try { return designSessionSchema.parse(JSON.parse(await readFile(this.file(sessionId), "utf8"))); } catch (error) { if ((error as NodeJS.ErrnoException).code === "ENOENT") return undefined; throw error; } }
  async list(): Promise<DesignSession[]> { await mkdir(this.root, { recursive: true }); const names = await readdir(this.root); const sessions: DesignSession[] = []; for (const name of names.filter(name => name.endsWith(".json"))) sessions.push(designSessionSchema.parse(JSON.parse(await readFile(path.join(this.root, name), "utf8")))); return sessions.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)); }
  async revise(sessionId: string, design: ArchitecturalDesign): Promise<DesignSession> { const session = await this.get(sessionId); if (!session) throw new Error(`Design session not found: ${sessionId}`); const timestamp = now(); const next = Math.max(...session.versions.map(version => version.version)) + 1; session.versions.push({ version: next, createdAt: timestamp, design: architecturalDesignSchema.parse(design), status: "draft" }); session.currentVersion = next; session.updatedAt = timestamp; session.dimension = design.dimension; session.anchor = design.anchor; session.execution = {}; await this.update(session); return session; }
  async update(session: DesignSession): Promise<void> { const valid = designSessionSchema.parse({ ...session, updatedAt: now() }); await mkdir(this.root, { recursive: true }); const file = this.file(valid.sessionId); const temporary = `${file}.${process.pid}.${randomUUID()}.tmp`; await writeFile(temporary, `${JSON.stringify(valid, null, 2)}\n`, { flag: "wx" }); await rename(temporary, file); }
  async remove(sessionId: string): Promise<void> { try { await unlink(this.file(sessionId)); } catch (error) { if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error; } }
}

export class InMemoryDesignSessionStore implements DesignSessionStore {
  private readonly sessions = new Map<string, DesignSession>();
  async create(design: ArchitecturalDesign, context: { worldIdentity?: string; referencePlayer?: string } = {}): Promise<DesignSession> { const timestamp = now(); const session = designSessionSchema.parse({ sessionId: randomUUID(), createdAt: timestamp, updatedAt: timestamp, worldIdentity: context.worldIdentity ?? "unknown", dimension: design.dimension, referencePlayer: context.referencePlayer, anchor: design.anchor, currentVersion: 1, versions: [{ version: 1, createdAt: timestamp, design, status: "draft" }], execution: {} }); this.sessions.set(session.sessionId, session); return structuredClone(session); }
  async get(sessionId: string): Promise<DesignSession | undefined> { const value = this.sessions.get(validateId(sessionId)); return value ? structuredClone(value) : undefined; }
  async list(): Promise<DesignSession[]> { return [...this.sessions.values()].map(value => structuredClone(value)).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)); }
  async revise(sessionId: string, design: ArchitecturalDesign): Promise<DesignSession> { const value = await this.get(sessionId); if (!value) throw new Error(`Design session not found: ${sessionId}`); const timestamp = now(); const next = Math.max(...value.versions.map(version => version.version)) + 1; value.versions.push({ version: next, createdAt: timestamp, design: architecturalDesignSchema.parse(design), status: "draft" }); value.currentVersion = next; value.updatedAt = timestamp; value.execution = {}; this.sessions.set(value.sessionId, value); return structuredClone(value); }
  async update(session: DesignSession): Promise<void> { this.sessions.set(validateId(session.sessionId), structuredClone(designSessionSchema.parse(session))); }
  async remove(sessionId: string): Promise<void> { this.sessions.delete(validateId(sessionId)); }
}

export function currentVersion(session: DesignSession): DesignVersion { const version = session.versions.find(item => item.version === session.currentVersion); if (!version) throw new Error(`Current design version ${session.currentVersion} is missing`); return version; }
export function versionByNumber(session: DesignSession, versionNumber?: number): DesignVersion { const version = versionNumber === undefined ? currentVersion(session) : session.versions.find(item => item.version === versionNumber); if (!version) throw new Error(`Design version ${versionNumber ?? session.currentVersion} is missing`); return version; }
export function withCompiledVersion(session: DesignSession, versionNumber: number, update: Pick<DesignVersion, "resolvedPalette" | "compiledPlan" | "validation" | "preview" | "status">): DesignSession { const target = session.versions.find(version => version.version === versionNumber); if (!target) throw new Error(`Design version ${versionNumber} is missing`); Object.assign(target, update); session.updatedAt = now(); return session; }
