import { z } from "zod";
import path from "node:path";
import os from "node:os";
import fs from "node:fs";
import { randomBytes } from "node:crypto";

const envSchema = z.object({
  AI_BRIDGE_PORT: z.coerce.number().int().min(1).max(65535).default(8765),
  AI_BRIDGE_TOKEN_FILE: z.string().optional(),
  AI_BRIDGE_CLIENT_ID: z.string().default("local-codex"),
  AI_BRIDGE_TIMEOUT_MS: z.coerce.number().int().positive().default(10_000),
  AI_BUILDER_SKILL_DIR: z.string().optional(),
  AI_BRIDGE_AUTO_RECONNECT: z.enum(["true", "false"]).default("true")
});
export type ServerConfig = {
  bridgePort: number; bridgeToken: string; tokenFile: string; clientId: string; timeoutMs: number; skillDirectory: string; designDirectory?: string; autoReconnect: boolean;
};
export const DEFAULT_TOKEN_FILE = path.join(os.homedir(), ".minecraft-ai-world-builder", "local-token");
export function loadOrCreateToken(filePath = DEFAULT_TOKEN_FILE): string {
  const directory = path.dirname(filePath); fs.mkdirSync(directory, { recursive: true });
  try { const token = fs.readFileSync(filePath, "utf8").trim(); if (token.length < 16) throw new Error(`Local Bridge token file is invalid: ${filePath}`); return token; }
  catch (error) { if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error; }
  const token = randomBytes(32).toString("base64url");
  try { const fd = fs.openSync(filePath, "wx", 0o600); try { fs.writeFileSync(fd, `${token}\n`, "utf8"); } finally { fs.closeSync(fd); } return token; }
  catch (error) { if ((error as NodeJS.ErrnoException).code === "EEXIST") return loadOrCreateToken(filePath); throw error; }
}
export function loadConfig(env: NodeJS.ProcessEnv = process.env): ServerConfig {
  const value = envSchema.parse(env);
  const tokenFile = value.AI_BRIDGE_TOKEN_FILE ? path.resolve(value.AI_BRIDGE_TOKEN_FILE) : DEFAULT_TOKEN_FILE;
  return { bridgePort: value.AI_BRIDGE_PORT, bridgeToken: loadOrCreateToken(tokenFile), tokenFile,
    clientId: value.AI_BRIDGE_CLIENT_ID, timeoutMs: value.AI_BRIDGE_TIMEOUT_MS, skillDirectory: value.AI_BUILDER_SKILL_DIR ?? path.resolve("minecraft-ai-builder-data", "skills"), designDirectory: path.resolve("minecraft-ai-builder-data", "design-sessions"), autoReconnect: value.AI_BRIDGE_AUTO_RECONNECT === "true" };
}
