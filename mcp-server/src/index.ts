#!/usr/bin/env node
import { serveStdio } from "@modelcontextprotocol/server/stdio";
import { BridgeClient } from "./bridge-client.js";
import { loadConfig } from "./config.js";
import { createServer } from "./server.js";
import { FileSkillStore } from "./skills.js";
import { FileDesignSessionStore } from "./design-sessions.js";

try {
  const config = loadConfig();
  serveStdio(() => createServer(new BridgeClient(config), new FileSkillStore(config.skillDirectory), new FileDesignSessionStore(config.designDirectory ?? "minecraft-ai-builder-data/design-sessions")));
  console.error(`Minecraft AI Builder MCP ready; local Bridge 127.0.0.1:${config.bridgePort}; token file ${config.tokenFile}`);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
}
