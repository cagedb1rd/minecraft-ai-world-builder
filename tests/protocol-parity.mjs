import { readFile } from "node:fs/promises";
import { operationSchema } from "../shared-protocol/dist/index.js";
import { BRIDGE_TOOL_NAMES } from "../mcp-server/dist/server.js";

const source = await readFile(new URL("../bridge-mod/src/main/java/dev/worldbuilder/bridge/BridgeDispatcher.java", import.meta.url), "utf8");
const dispatcherOperations = [...source.matchAll(/case ([^\n]+?)->/g)].flatMap(match => [...match[1].matchAll(/"([^"]+)"/g)].map(value => value[1]));
const protocolOperations = operationSchema.options;
const missingMcp = protocolOperations.filter(name => !BRIDGE_TOOL_NAMES.includes(name));
const missingBridge = protocolOperations.filter(name => !dispatcherOperations.includes(name));
const extras = dispatcherOperations.filter(name => !protocolOperations.includes(name));
if (missingMcp.length || missingBridge.length || extras.length) throw new Error(JSON.stringify({ missingMcp, missingBridge, extras }));
console.log(`Protocol parity OK: ${protocolOperations.length} Bridge operations, ${BRIDGE_TOOL_NAMES.length} MCP tools`);
