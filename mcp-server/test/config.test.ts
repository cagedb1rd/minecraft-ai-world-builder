import { describe, expect, it } from "vitest";
import { loadConfig } from "../src/config.js";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

describe("MCP configuration", () => {
  it("always resolves the Bridge to loopback and persists a token", () => { const file=path.join(mkdtempSync(path.join(tmpdir(),"mc-ai-")),"token");const config=loadConfig({ AI_BRIDGE_TOKEN_FILE:file }); expect(config.bridgePort).toBe(8765);expect(config.tokenFile).toBe(file);expect(config.bridgeToken).toBe(readFileSync(file,"utf8").trim());expect(config.bridgeToken.length).toBeGreaterThanOrEqual(16);expect(config.designDirectory).toContain("minecraft-ai-builder-data");expect(Object.keys(config)).toEqual(["bridgePort","bridgeToken","tokenFile","clientId","timeoutMs","skillDirectory","designDirectory","autoReconnect"]); });
  it("rejects an invalid persisted token", () => { const file=path.join(mkdtempSync(path.join(tmpdir(),"mc-ai-")),"token");writeFileSync(file,"short");expect(()=>loadConfig({AI_BRIDGE_TOKEN_FILE:file})).toThrow("invalid"); });
});
