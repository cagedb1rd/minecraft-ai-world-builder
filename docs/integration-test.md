# Minecraft 实机测试教程

请使用新建测试世界并先备份。不要把第一次写入测试指向现有基地。

## 1. 双方安装和启动

双方把同一个面向 Minecraft 1.20.1 / Forge 47.4.10 的 `ai_world_builder_bridge-0.1.0.jar` 放入各自整合包 `mods`。Bridge 会在本机用户目录自动生成 `%USERPROFILE%/.minecraft-ai-world-builder/local-token`，MCP Server 会读取同一个文件；日常不需要填写远程连接参数。

房主正常进入世界并 Open to LAN；联机端口仍按普通 Minecraft 联机方式使用。AI 操作者加入该世界后，在自己的电脑启动 MCP：

```powershell
npm run build
npm start -w @minecraft-ai-builder/mcp-server
```

MCP 只连接自己的 `127.0.0.1` Bridge。若本机没有 Integrated Server，Bridge 会自动通过当前 Minecraft 连接的 Forge Relay 转发；若服务器未安装同一 Bridge，返回 `BRIDGE_HOST_UNAVAILABLE`。

联机授权前，房主可用自己的本机 MCP 调用 `get_players` 读取对方 UUID，将其加入 `config/ai_world_builder_bridge-common.toml` 的 `playerRoles`，然后重启世界。角色配置只按 UUID 生效，不按客户端自报的名称或角色生效。

## 2. 基础连接和能力握手

```powershell
npm run integration
```

脚本会读取本地 token，验证 handshake、世界、玩家、Registry、Recipe 和能力；客户端加入服务器时还会自动发送 Forge capability handshake。

## 3. 方块修改和 Undo

站到空旷测试区，用 F3 确定坐标：

```powershell
$env:AI_TEST_X = "100"
$env:AI_TEST_Y = "80"
$env:AI_TEST_Z = "100"
$env:AI_TEST_BLOCK = "minecraft:stone"
npm run integration
```

本机 Host 和远程玩家 Relay 都进入同一个服务端 Transaction、分 Tick BuildQueue 和 Undo/Redo 逻辑。

## 4. BuildingPlan / 图片参考建筑

向 Codex 发送：

> 读取我的参考坐标和前方 20 格范围，扫描高度、地形摘要和现有 BlockEntity。搜索当前整合包中的木材、墙面和屋顶材料，生成 9×7×5 的 BuildingPlan。先 validate、estimate、preview，确认没有覆盖风险后执行并轮询完成；不要控制玩家。

图片参考建筑时直接提供一张或多张图片，让 Codex 分析比例、层数、屋顶、框架、墙体、窗户和配色，再通过 `search_blocks` 匹配当前 Registry；不得让模型发送数千个独立 `set_block`。

## 5. Create

先调用 `get_mod_capabilities` 和 `list_mods`。Create 普通注册方块可直接搜索、放置和 Undo；高级火车、动力和装配能力只有在对应版本 Adapter 被实际验证后才会开放。

## 6. 故障验收

- 单人世界：MCP 请求应直接到本机 Integrated Server。
- 加入他人世界：请求应显示服务端玩家身份权限，并通过 Forge Relay 完成。
- 未安装服务端 Bridge 或协议不兼容：应明确返回 `BRIDGE_HOST_UNAVAILABLE`，不能建立任何额外 TCP 连接。
