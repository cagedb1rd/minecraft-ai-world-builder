import { Client } from "@modelcontextprotocol/client";
import { InMemoryTransport } from "@modelcontextprotocol/server";
import { describe, expect, it } from "vitest";
import { BridgeClient } from "../src/bridge-client.js";
import { createServer } from "../src/server.js";

class FakeBridge extends BridgeClient {
  calls: Array<{ operation: string; args: Record<string, unknown> }> = [];
  constructor() { super({ bridgePort: 1, bridgeToken: "0123456789abcdef", tokenFile: "token", clientId: "test", timeoutMs: 1, skillDirectory: "skills", autoReconnect: true }); }
  override async call(operation: Parameters<BridgeClient["call"]>[0], args: Record<string, unknown> = {}) { this.calls.push({ operation, args }); return { activeHost: true }; }
}

describe("MCP server integration", () => {
  it("lists tools, validates input, and forwards a call to Bridge", async () => { const bridge = new FakeBridge(); const server = createServer(bridge); const client = new Client({ name: "test-client", version: "1.0.0" }); const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport); await client.connect(clientTransport); const listed = await client.listTools(); expect(listed.tools.some(t => t.name === "get_world_info")).toBe(true); expect(listed.tools.some(t => t.name === "compile_architectural_design")).toBe(true); expect(listed.tools.some(t => t.name === "create_design_session")).toBe(true);
    const result = await client.callTool({ name: "get_world_info", arguments: {} }); expect(result.isError).not.toBe(true); expect(bridge.calls).toEqual([{ operation: "get_world_info", args: {} }]);
    await client.close(); await server.close();
  });
});
