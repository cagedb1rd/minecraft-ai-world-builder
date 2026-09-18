# Bridge Protocol 1.0.0

MCP 只通过 UTF-8 NDJSON TCP 连接本机 `127.0.0.1` Bridge；每个连接只发送一个请求并接收一个响应。玩家联机中继不建立第二条 TCP 连接，而是把同一 JSON 请求封装进 Forge SimpleChannel 数据包。

请求字段：`protocolVersion`、UUID `requestId`、`operation`、对象 `arguments`、`auth { token, clientId }`。

响应字段：`protocolVersion`、关联的 `requestId`、`success`、可选 `result`、`warnings[]`、`errors[]`。

稳定错误码覆盖认证失败、权限拒绝、协议版本不匹配、格式错误、`BRIDGE_HOST_UNAVAILABLE`、Bridge/世界不可用、超时、限制超出、非法 BlockState、事务失败、资源不存在、Mod 未安装和内部错误。

TypeScript 的权威运行时定义位于 `shared-protocol/src/index.ts`；Java `Protocol` 只包含 Wire DTO，不引用 Minecraft 类型。1.x 只能增加向后兼容字段，破坏性改动必须升级主版本并加入握手协商。

`scan_area` 使用 palette + RLE，顺序为 `y,z,x`，避免逐方块重复输出完整状态。

当前协议共有 66 个 Bridge 操作（含 `handshake`），全部作为 MCP Bridge 工具暴露。`search_recipes` 查询服务端 `RecipeManager` 中实际加载的配方，不维护硬编码配方表。
