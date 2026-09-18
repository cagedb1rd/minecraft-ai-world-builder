import net from "node:net";
import { randomUUID } from "node:crypto";
import os from "node:os";
import path from "node:path";
import fs from "node:fs";

const host = "127.0.0.1";
const port = Number(process.env.AI_BRIDGE_PORT ?? "8765");
const tokenFile = process.env.AI_BRIDGE_TOKEN_FILE ?? path.join(os.homedir(), ".minecraft-ai-world-builder", "local-token");
if (!fs.existsSync(tokenFile)) throw new Error(`Local Bridge token not found: ${tokenFile}. Start Minecraft with ai_world_builder_bridge.jar first.`);
const token = fs.readFileSync(tokenFile, "utf8").trim();
if (token.length < 16) throw new Error(`Invalid local Bridge token file: ${tokenFile}`);

async function call(operation, args = {}) {
  const requestId = randomUUID(); const request = { protocolVersion: "1.0.0", requestId, operation, arguments: args, auth: { token, clientId: process.env.AI_BRIDGE_CLIENT_ID ?? "local-codex" } };
  return new Promise((resolve, reject) => { const socket = net.createConnection({ host, port }); let text = "";
    socket.setTimeout(10_000); socket.on("connect", () => socket.write(`${JSON.stringify(request)}\n`));
    socket.on("data", chunk => { text += chunk; if (!text.includes("\n")) return; const response = JSON.parse(text.slice(0, text.indexOf("\n"))); socket.destroy(); response.success ? resolve(response.result) : reject(new Error(JSON.stringify(response.errors))); });
    socket.on("timeout", () => { socket.destroy(); reject(new Error("Bridge timed out")); }); socket.on("error", reject); });
}
async function waitForBuild(buildId) { for (let attempt = 0; attempt < 200; attempt++) { const status = await call("get_build_status", { buildId }); if (["complete", "failed", "cancelled"].includes(status.status)) return status; await new Promise(resolve => setTimeout(resolve, 100)); } throw new Error(`Build ${buildId} did not finish`); }

console.log("handshake", await call("handshake"));
console.log("world", await call("get_world_info"));
console.log("players", await call("get_players"));
console.log("block search", await call("search_blocks", { query: process.env.AI_TEST_BLOCK_QUERY ?? "planks", limit: 5 }));
console.log("recipe search", await call("search_recipes", { query: process.env.AI_TEST_RECIPE_QUERY ?? "planks", limit: 5 }));
if (process.env.AI_TEST_X && process.env.AI_TEST_Y && process.env.AI_TEST_Z) {
  const dimension = process.env.AI_TEST_DIMENSION ?? "minecraft:overworld";
  const position = { x: Number(process.env.AI_TEST_X), y: Number(process.env.AI_TEST_Y), z: Number(process.env.AI_TEST_Z) };
  const changed = await call("set_block", { dimension, position, state: { id: process.env.AI_TEST_BLOCK ?? "minecraft:stone", properties: {} }, summary: "integration smoke" });
  console.log("changed", changed); const undo = await call("undo_build", { transactionId: changed.transactionId }); console.log("undo queued", undo); console.log("undo complete", await waitForBuild(undo.buildId));
}
