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

## 普通用户快速开始

### 准备环境

- Minecraft Java Edition 1.20.1
- Forge 47.4.10
- Java 17
- Node.js 20 或更高版本
- Codex，并启用 MCP

从 GitHub 获取 MCP Server：

```powershell
git clone https://github.com/cagedb1rd/minecraft-ai-world-builder.git
cd minecraft-ai-world-builder
npm install
npm run build
```

从 GitHub Releases 下载 `ai_world_builder_bridge-0.1.0.jar`，放入 Minecraft 整合包的 `mods` 文件夹。如果仓库暂时还没有 Release，请按下面的“开发者构建”步骤自行生成 jar。房主和加入者都使用同一个 jar，不需要 Host 版和 Client 版两个文件。

### 连接 Codex

在项目根目录执行：

```powershell
codex mcp add minecraft-ai-builder -- node <项目根目录>\mcp-server\dist\index.js
codex mcp list
```

将 `<项目根目录>` 替换成实际路径。MCP 只连接当前电脑的 `127.0.0.1:8765`，不需要填写房主 IP，也不需要配置 Tailscale、ZeroTier、SSH Tunnel 或远程 Bridge。

如需改变本机端口，可设置 `AI_BRIDGE_PORT`；普通使用不需要设置额外环境变量。

正常使用时不需要手动一直运行 `node dist/index.js`；Codex 会按照 MCP 配置启动它。只有排查问题时才手动启动：

```powershell
node <项目根目录>\mcp-server\dist\index.js
```

## 开发者构建

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

## 首次启动行为

双方都把同一个 jar 放入各自整合包的 `mods` 目录，不区分 Host 版和 Client 版。

第一次启动任意一侧时，Bridge 会在当前 Windows 用户目录生成本地认证文件：

`%USERPROFILE%/.minecraft-ai-world-builder/local-token`

MCP Server 会自动读取该文件。普通使用不需要填写任何远程连接参数。

## 第一次使用

建议先在新建或备份后的测试世界中使用。第一次请求先要求预览：

> 读取我的位置和前方地形，搜索当前整合包真实材料，设计一个双层日式乡村小屋。先创建设计会话、解析材料、编译并预览，不要直接执行。

确认预览结果没有越界或覆盖风险后，再发送：

> 批准当前设计并执行，使用分 Tick 建造。完成后告诉我 build status、buildId 和 transactionId。

建造完成后可以发送：

> 撤销刚才的建筑。

所有建筑请求都会经过服务端权限、边界校验、Transaction、分 Tick BuildQueue 和 Undo/Redo。

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
[bridge]
playerRoles = ["<玩家 UUID>=OWNER", "<受信任玩家 UUID>=TRUSTED"]
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

## 常见问题

- `ECONNREFUSED 127.0.0.1:8765`：Minecraft 尚未启动、Bridge Mod 没有成功加载，或安装的 jar 不是 Forge 47.4.10 版本。关闭游戏后重新安装 Release 中的 jar，再启动游戏。
- `BRIDGE_HOST_UNAVAILABLE`：本机没有 Integrated Server，且当前 Minecraft 联机服务器没有安装同一个 Bridge Mod，或者联机握手尚未完成。
- `PERMISSION_DENIED`：房主服务端没有在 `playerRoles` 中授权当前真实玩家 UUID，或请求超出了方块数量、半径、维度或保护区限制。
- 读取不到材料：先调用 `list_mods`、`search_blocks` 和 `get_block_states`，只使用当前 Registry 中真实存在的方块，不要手写猜测的 Mod ID。
- 建筑执行前被阻止：先使用 `preview_architectural_design` 或 `preview_build`，检查覆盖风险和 BlockEntity 警告，再批准执行。

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

完整流程见：[实机测试教程](docs/integration-test.md)、[安全说明](docs/security.md)、[架构说明](docs/architecture.md)。
