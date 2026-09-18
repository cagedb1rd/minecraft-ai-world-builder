import { mkdir, readFile, readdir, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import { z } from "zod";

export const skillSchema = z.object({
  skillId: z.string().regex(/^[a-z0-9_.-]+$/), modId: z.string(), modVersion: z.string(), minecraftVersion: z.string(),
  description: z.string(), requirements: z.array(z.string()), constraints: z.array(z.string()), procedure: z.array(z.string()),
  validated: z.boolean(), validationResult: z.string().optional(), lastValidatedAt: z.string().datetime().optional(),
  sources: z.array(z.string()).default([]), validationStatus: z.enum(["validated", "needs_revalidation", "unvalidated"]).default("unvalidated")
});
export type Skill = z.infer<typeof skillSchema>;
export interface SkillStore { list(): Promise<Skill[]>; get(id: string): Promise<Skill | undefined>; save(skill: Skill): Promise<void>; }
export class FileSkillStore implements SkillStore {
  constructor(private readonly root: string) {}
  private file(id: string) { if (!/^[a-z0-9_.-]+$/.test(id)) throw new Error("Invalid skillId"); return path.join(this.root, "discovered", `${id}.json`); }
  async list() { await mkdir(path.join(this.root, "discovered"), { recursive: true }); const names = await readdir(path.join(this.root, "discovered"));
    return (await Promise.all(names.filter(n => n.endsWith(".json")).map(async n => skillSchema.parse(JSON.parse(await readFile(path.join(this.root, "discovered", n), "utf8")))))); }
  async get(id: string) { try { return skillSchema.parse(JSON.parse(await readFile(this.file(id), "utf8"))); } catch (e: unknown) { if ((e as NodeJS.ErrnoException).code === "ENOENT") return undefined; throw e; } }
  async save(skill: Skill) { const valid = skillSchema.parse(skill); const file = this.file(valid.skillId); await mkdir(path.dirname(file), { recursive: true });
    const temporary = `${file}.${process.pid}.tmp`; await writeFile(temporary, `${JSON.stringify(valid, null, 2)}\n`, { flag: "wx" }); await rename(temporary, file); }
}
