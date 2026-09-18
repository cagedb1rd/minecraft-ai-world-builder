# 架构说明

主链路：

`Codex → MCP stdio Server → localhost Bridge → (Integrated Server direct | Forge Relay) → ServerLevel`

MCP Server 负责工具定义、Zod 校验、localhost 连接和本地 Skill 数据。Bridge 负责 Forge Registry、权限、安全边界、服务端世界访问、确定性几何、施工队列和事务。AI Planning 不进入 Mod。

Bridge jar 同时安装在双方整合包：本机有 Integrated Server 时直接执行；没有时，客户端 Relay 通过 Minecraft 的 Forge SimpleImpl channel 发往服务端 Bridge。MCP Tool API 不感知当前是否房主。

localhost 网络线程只解析一个最大 1 MiB 的请求，然后交给 `BridgeRuntime`；Relay packet 在服务端从 `Context#getSender()` 绑定玩家身份，再提交到 Minecraft Server Thread。所有世界对象只在 Server Thread 上访问。批量施工由 `BuildQueue` 在 `ServerTickEvent` 末尾按 Chunk 顺序和 `blocksPerTick` 预算执行；不会永久 force-load Chunk。

每个修改在应用前保存 BlockState 和 BlockEntity NBT，应用后保存新快照。事务写入世界自己的 `ai_world_builder_transactions` SavedData，因此正常存档后可以跨重启查看和撤销。运行中施工会定期 checkpoint；若重启后发现未完成事务，会标记为可撤销的 cancelled 状态。

历史记录持久化 `buildId`、`transactionId`、发起客户端、可选参考玩家、时间、耗时、维度、边界、材料、方块数和状态；旧版没有 `buildId` 的记录会把事务 ID 作为兼容值载入。

`BuildingPlanService` 将 Schema 与执行分离，负责材料解析、边界一致性、变化数估算、覆盖/BlockEntity 预览和几何展开。`GeometryEngine` 生成坐标，`BuildQueue` 执行，二者均不承担 AI 设计。

地形模式是规划约束：MCP/Codex 先读取高度图和区域摘要，再把 flatten、terrace、followSlope 或 bridgeOverTerrain 的结果显式写入确定性 operations；Bridge 不在执行时擅自重写地形。`avoidExistingStructures=true` 时任何非空气覆盖都会阻止执行。


