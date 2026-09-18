import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { FileSkillStore } from "../src/skills.js";
let root: string | undefined;
afterEach(async () => { if (root) await rm(root, { recursive: true, force: true }); root = undefined; });
describe("FileSkillStore", () => {
  it("atomically creates and updates validated skill data", async () => { root = await mkdtemp(path.join(tmpdir(), "ai-builder-skill-")); const store = new FileSkillStore(root);
    const skill = { skillId: "generic.floor", modId: "minecraft", modVersion: "1.20.1", minecraftVersion: "1.20.1", description: "floor", requirements: [], constraints: [], procedure: ["build_floor"], validated: true, validationStatus: "validated" as const, sources: [] };
    await store.save(skill); await store.save({ ...skill, description: "updated" }); expect((await store.get(skill.skillId))?.description).toBe("updated"); expect((await store.list())).toHaveLength(1);
    expect(JSON.parse(await readFile(path.join(root, "discovered", "generic.floor.json"), "utf8")).skillId).toBe("generic.floor");
  });
  it("rejects path traversal", async () => { root = await mkdtemp(path.join(tmpdir(), "ai-builder-skill-")); const store = new FileSkillStore(root); await expect(store.get("../../escape")).rejects.toThrow("Invalid skillId"); });
});
