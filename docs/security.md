# 安全边界

## 网络边界

MCP Server 永远只连接当前 Codex 电脑的 `127.0.0.1` Bridge。Bridge 的 TCP listener 只绑定 loopback，并拒绝任何非 loopback socket；项目没有远程 TCP 模式、远程 IP、远程 allowlist 或第二条 AI 网络。

多人世界使用 Minecraft 本身的 Forge SimpleImpl 连接：客户端 Relay packet 只携带已验证的操作请求，服务端从 `NetworkEvent.Context#getSender()` 获取真实 `ServerPlayer`，忽略客户端声明的 UUID、角色和权限。

## 身份和权限

本机 TCP 仅使用首次生成并持久化的本地 token（`%USERPROFILE%/.minecraft-ai-world-builder/local-token`）。多人 Relay 不使用 TCP token；权限由服务端 `playerRoles` 按服务端认证的玩家 UUID 配置，未知玩家默认 `NONE`。`OWNER` 才能撤销、重做、取消和创建实验区；`TRUSTED` 受构建数量上限约束。

## 世界安全

每次请求都经过维度、坐标、高度、半径、数量、体积、保护区、在线玩家和 BlockEntity 检查。修改统一经过 `BuildQueue`、`TransactionManager` 和服务端线程；失败回滚，取消保留可撤销事务。

## 数据和日志

Bridge 只在世界 SavedData 写入自身事务历史，MCP 只在自身技能目录写入技能。日志记录客户端身份、连接状态和施工结果，不记录 token、私人路径或网络凭据。
