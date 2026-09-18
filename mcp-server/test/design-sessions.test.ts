import { describe, expect, it } from "vitest";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { architecturalDesignExample } from "../src/architectural.js";
import { FileDesignSessionStore } from "../src/design-sessions.js";

describe("DesignSession persistence and immutable versions", () => {
  it("creates, revises, reloads, and atomically stores versions", async () => {
    const root = await mkdtemp(path.join(tmpdir(), "ai-builder-design-"));
    try {
      const store = new FileDesignSessionStore(root);
      const initial = await store.create(architecturalDesignExample(), { worldIdentity: "test-world", referencePlayer: "Alex" });
      const revised = await store.revise(initial.sessionId, { ...architecturalDesignExample(), name: "Revised Minka" });
      expect(revised.currentVersion).toBe(2);
      expect(revised.versions[0]?.design).toMatchObject({ name: "Japanese Minka" });
      expect(revised.versions[1]?.design).toMatchObject({ name: "Revised Minka" });
      const loaded = await store.get(initial.sessionId);
      expect(loaded?.versions).toHaveLength(2);
      expect(JSON.parse(await readFile(path.join(root, `${initial.sessionId}.json`), "utf8")).currentVersion).toBe(2);
    } finally { await rm(root, { recursive: true, force: true }); }
  });
});
