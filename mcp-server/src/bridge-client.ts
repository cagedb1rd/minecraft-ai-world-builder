import net from "node:net";
import { randomUUID } from "node:crypto";
import { bridgeResponseSchema, PROTOCOL_VERSION, type BridgeResponse, type Operation } from "@minecraft-ai-builder/shared-protocol";
import type { ServerConfig } from "./config.js";

export class BridgeCallError extends Error {
  constructor(public readonly response: BridgeResponse) { super(response.errors.map(e => `${e.code}: ${e.message}`).join("; ")); }
}

export class BridgeClient {
  constructor(private readonly config: ServerConfig) {}
  async call(operation: Operation, args: Record<string, unknown> = {}): Promise<unknown> {
    try { return await this.callOnce(operation, args); }
    catch (error) { if (!this.config.autoReconnect || error instanceof BridgeCallError) throw error; await new Promise(resolve => setTimeout(resolve, 150)); return this.callOnce(operation, args); }
  }
  private callOnce(operation: Operation, args: Record<string, unknown>): Promise<unknown> {
    const requestId = randomUUID();
    const payload = JSON.stringify({ protocolVersion: PROTOCOL_VERSION, requestId, operation, arguments: args,
      auth: { token: this.config.bridgeToken, clientId: this.config.clientId } }) + "\n";
    if (Buffer.byteLength(payload, "utf8") > 1_048_576) return Promise.reject(new Error("Bridge request exceeds 1 MiB protocol limit"));
    return new Promise((resolve, reject) => {
      const socket = net.createConnection({ host: "127.0.0.1", port: this.config.bridgePort });
      let buffer = "";
      const timer = setTimeout(() => { socket.destroy(); reject(new Error(`Bridge timeout after ${this.config.timeoutMs}ms`)); }, this.config.timeoutMs);
      const finish = (error?: Error, value?: unknown) => { clearTimeout(timer); socket.destroy(); error ? reject(error) : resolve(value); };
      socket.setEncoding("utf8");
      socket.once("connect", () => socket.write(payload));
      socket.on("data", chunk => {
        buffer += chunk;
        if (buffer.length > 16_777_216) return finish(new Error("Bridge response exceeds 16 MiB safety limit"));
        const newline = buffer.indexOf("\n");
        if (newline < 0) return;
        try {
          const response = bridgeResponseSchema.parse(JSON.parse(buffer.slice(0, newline)));
          if (response.requestId !== requestId) return finish(new Error("Bridge response requestId mismatch"));
          if (!response.success) return finish(new BridgeCallError(response));
          finish(undefined, response.result);
        } catch (error) { finish(error instanceof Error ? error : new Error(String(error))); }
      });
      socket.once("error", error => finish(new Error(`Local Bridge unavailable at 127.0.0.1:${this.config.bridgePort}: ${error.message}`)));
    });
  }
}
