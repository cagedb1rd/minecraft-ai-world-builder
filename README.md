# Minecraft AI World Builder

面向 Minecraft Java Edition 1.20.1 Forge（当前整合包锁定 Forge 47.4.10）的世界建造 Agent。它只修改房主服务端世界，不是玩家 Bot；没有移动、视角、背包、攻击、鼠标或键盘控制能力。

## 最终网络架构

MCP 只有一个入口：

`Codex → MCP Server → 127.0.0.1 本机 Bridge`

如果本机是房主，Bridge 直接调度本机 Integrated Server；如果本机是加入者，Bridge 将同一个 MCP 请求封装为 Forge SimpleImpl packet，通过当前 Minecraft 联机连接发给房主 Bridge。服务端从 packet context 获取真实 `ServerPlayer`，然后进入相同的 `BridgeDispatcher → SafetyPolicy → TransactionManager → BuildQueue → ServerLevel` 路径。

AI 只有这一条 MCP→本机 Bridge 入口；多人转发复用 Minecraft 自己的连接，不会建立第二条 AI 网络。

## 工程结构

- `bridge-mod`：同一个 Forge jar，同时提供 Integrated/Dedicated Server Host 和客户端 Relay。
- `mcp-server`：Codex 侧 TypeScript MCP stdio Server，只连接本机 loopback。
- `shared-protocol`：版本化 1.0.0 Wire Schema 和 BuildingPlan Schema。
- `skills`：技能数据目录和 Skill Store 接口。
- `tests`：TypeScript、协议、Forge packet、路由、权限和几何测试。
- `docs`：中文架构、安全和实机教程。

## 构建

```powershell
npm install
npm run typecheck
npm test
npm run build
cd bridge-mod
.\gradlew.bat build
```

Bridge 产物：`bridge-mod/build/libs/ai_world_builder_bridge-0.1.0.jar`（面向 Forge 47.4.10）

安装前请使用上述命令重新生成该 jar；不要直接复用旧的 `build/` 缓存产物。

## 安装和首次启动

双方都把同一个 jar 放入各自整合包的 `mods` 目录，不区分 Host 版和 Client 版。

第一次启动任意一侧时，Bridge 会在当前 Windows 用户目录生成本地认证文件：

`%USERPROFILE%/.minecraft-ai-world-builder/local-token`

MCP Server 会自动读取该文件。普通使用不需要填写任何远程连接参数。

## GitHub 公开发布

源码仓库不提交本地认证 token、设计会话、Minecraft 存档、Gradle 缓存、`node_modules`、`dist` 或调试日志；这些内容已列入根目录 `.gitignore`。Bridge jar 建议作为 GitHub Release 附件发布，而不是提交到源码历史中。公开发布前请先运行 `npm test` 和 `bridge-mod\gradlew.bat clean build`，并检查暂存文件列表中没有个人路径或凭据。完整清单见 [`docs/public-release.md`](docs/public-release.md)。

## Codex MCP 配置

先在项目根目录完成 `npm run build`，然后注册本机 MCP：

```powershell
codex mcp add minecraft-ai-builder -- node <项目根目录>\mcp-server\dist\index.js
codex mcp list
```

将 `<项目根目录>` 替换成你电脑上的实际项目路径。MCP Server 只会连接 `127.0.0.1:8765`。如需改变本机端口，可设置 `AI_BRIDGE_PORT`；日常启动不需要设置额外环境变量。

## 使用方式 A：我是房主

1. 安装 jar，正常进入单人世界。
2. 启动 Codex MCP。
3. 直接对 Codex 说：

   > 读取我的位置，在我前面建一个双层日式小屋。先扫描地形、搜索材料、预览并确保可以 undo。

请求会由本机 Bridge 直接进入 Integrated Server。

## 使用方式 B：对方是房主

双方安装同一个 jar。

房主：

1. 正常进入世界。
2. Open to LAN。
3. 如果需要跨网络，按普通 Minecraft 联机方式映射 Minecraft 联机端口（例如 SakuraFrp）。不要映射或配置 AI 端口。

AI 操作者：

1. 正常加入房主的 Minecraft 世界。
2. 在自己的电脑启动 MCP Server。
3. Codex 仍然只连接自己电脑的 `127.0.0.1` Bridge。
4. 直接说：

   > 读取我的位置，在我前面建一个双层日式小屋。

客户端 Bridge 会自动检测当前 Forge 连接是否存在 AI Bridge channel；存在时通过 packet relay 转发，不存在时返回 `BRIDGE_HOST_UNAVAILABLE`。不需要输入房主 IP，也不会建立第二条 TCP 连接。

## 服务端权限

服务端配置文件为 `config/ai_world_builder_bridge-common.toml`，核心字段包括：

```toml
enabled = true
port = 8765
playerRoles = ["玩家 UUID=OWNER", "受信任玩家 UUID=TRUSTED"]
referencePlayers = ["*"]
allowedDimensions = ["minecraft:overworld"]
protectedRegions = []
maxBlocksPerBuild = 10000
maxBuildRadius = 128
maxScanRadius = 64
blocksPerTick = 512
historyLimit = 50
```

Relay 请求的 UUID 和角色不能由客户端声明；服务端使用 Forge `NetworkEvent.Context#getSender()` 得到的真实玩家身份，再按 `playerRoles` 判断 `OWNER`、`TRUSTED` 或 `NONE`。

`playerRoles` 只使用 UUID。房主可先在自己的世界通过 `get_players` 读取联机玩家 UUID，再把需要授权的 UUID 写入配置并重启世界；也可以从整合包的 `usercache.json` 查找 UUID。未列出的玩家默认没有建造权限。

## 能力

已提供世界读取、动态 Registry、Recipe、BlockState、批量区域操作、23 类几何建筑、BuildingPlan 校验/估算/预览/执行、分 Tick 队列、Transaction、Undo/Redo、Build History、Experiment Workspace 和 Skill System。MCP 另外提供 ArchitecturalDesign、动态 PaletteResolver、DesignCompiler、DesignSession 版本管理和语义建筑构件编译；这些设计能力仍然通过现有 BuildingPlan 和 Bridge 安全执行路径落地。

### 高质量建筑设计流程

对于有风格的建筑，建议使用高层设计工具流程，而不是直接调用简单的 `build_box`：

1. 使用 `validate_architectural_design` 检查局部坐标、楼层、构件和材料角色。
2. 使用 `resolve_design_palette` 按当前整合包实际 Registry 选择材料，不要猜测 Mod 方块 ID。
3. 使用 `compile_architectural_design` 将设计编译为确定性的 BuildingPlan。
4. 使用 `preview_architectural_design` 检查尺寸、覆盖范围、材料和 BlockEntity 风险。
5. 需要多轮修改时使用 `create_design_session`、`revise_design_session` 和 `list_design_versions`。
6. 预览通过后使用 `approve_design_session`，再使用 `execute_design_session` 执行；执行仍然进入 Bridge 的 Transaction、分 Tick 和 Undo 路径。

ArchitecturalDesign 的 `components` 支持 `framed_wall`、`timber_frame_grid`、`lattice_window`、`veranda`、`balcony`、`layered_roof`、`extended_eaves`、`roof_ridge` 等语义构件。构件只引用材料角色，具体方块由当前 Registry 解析；`styleId` 会通过确定性的 StyleProfile 影响未显式指定的屋顶类型和屋檐默认深度，显式 roof 配置优先。

示例请求：

> 读取我的位置和前方地形，创建一个 13×11 的双层日式乡村小屋设计。使用深色木结构、浅色墙面、分层大屋顶、深屋檐、格栅窗、入口门廊、环绕走廊和栏杆。先解析当前整合包实际材料，编译 ArchitecturalDesign，预览并等待我确认，不要直接执行。

Create 普通注册方块自动支持；Create 高级铁路、列车、动力和物流能力只有在确切版本 Adapter 通过实际 API 验证后才会开放。

## 测试

无需 Minecraft 的验证：

```powershell
npm run typecheck
npm test
cd bridge-mod
.\gradlew.bat clean build
```

真实世界 smoke test：

```powershell
npm run integration
```

完整流程见：[实机测试教程](docs/integration-test.md)、[安全说明](docs/security.md)、[架构说明](docs/architecture.md)、[公开发布清单](docs/public-release.md) 和 [续接说明](CONTINUATION.md)。
