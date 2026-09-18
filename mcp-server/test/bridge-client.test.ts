import net from "node:net";
import { afterEach, describe, expect, it } from "vitest";
import { BridgeCallError, BridgeClient } from "../src/bridge-client.js";
import { PROTOCOL_VERSION } from "@minecraft-ai-builder/shared-protocol";
let server: net.Server | undefined;
afterEach(() => new Promise<void>(resolve => server?.close(() => resolve()) ?? resolve()));
describe("BridgeClient", () => {
  it("round trips a correlated request", async () => {
    server = net.createServer(socket => { let data = ""; socket.on("data", c => { data += c; if (!data.includes("\n")) return;
      const req = JSON.parse(data); socket.end(JSON.stringify({ protocolVersion: PROTOCOL_VERSION, requestId: req.requestId, success: true, result: { active: true }, warnings: [], errors: [] }) + "\n"); }); });
    await new Promise<void>(resolve => server!.listen(0, "127.0.0.1", resolve));
    const address = server.address(); if (!address || typeof address === "string") throw new Error("no address");
    const client = new BridgeClient({ bridgePort: address.port, bridgeToken: "0123456789abcdef", tokenFile: "token", clientId: "test", timeoutMs: 1000, skillDirectory: "skills", autoReconnect: true });
    await expect(client.call("get_world_info")).resolves.toEqual({ active: true });
  });
  it("preserves structured Bridge errors without retrying", async () => { let connections=0;server=net.createServer(socket=>{connections++;let data="";socket.on("data",c=>{data+=c;if(!data.includes("\n"))return;const req=JSON.parse(data);socket.end(JSON.stringify({protocolVersion:PROTOCOL_VERSION,requestId:req.requestId,success:false,warnings:[],errors:[{code:"PERMISSION_DENIED",message:"denied"}]})+"\n");});});await new Promise<void>(resolve=>server!.listen(0,"127.0.0.1",resolve));const address=server.address();if(!address||typeof address==="string")throw new Error("no address");const client=new BridgeClient({bridgePort:address.port,bridgeToken:"0123456789abcdef",tokenFile:"token",clientId:"test",timeoutMs:1000,skillDirectory:"skills",autoReconnect:true});await expect(client.call("get_world_info")).rejects.toBeInstanceOf(BridgeCallError);expect(connections).toBe(1);});
  it("keeps the explicit Forge-host-unavailable error code", async () => { server=net.createServer(socket=>{let data="";socket.on("data",c=>{data+=c;if(!data.includes("\n"))return;const req=JSON.parse(data);socket.end(JSON.stringify({protocolVersion:PROTOCOL_VERSION,requestId:req.requestId,success:false,warnings:[],errors:[{code:"BRIDGE_HOST_UNAVAILABLE",message:"no Minecraft host"}]})+"\n");});});await new Promise<void>(resolve=>server!.listen(0,"127.0.0.1",resolve));const address=server.address();if(!address||typeof address==="string")throw new Error("no address");const client=new BridgeClient({bridgePort:address.port,bridgeToken:"0123456789abcdef",tokenFile:"token",clientId:"test",timeoutMs:1000,skillDirectory:"skills",autoReconnect:true});await expect(client.call("get_world_info")).rejects.toMatchObject({response:{errors:[{code:"BRIDGE_HOST_UNAVAILABLE"}]}});});
});
