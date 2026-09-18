# GitHub 公开发布清单

本项目可以公开发布源码，但本地运行数据和认证材料不能进入 Git 历史。

## 可以提交

- `bridge-mod/src`、Gradle 配置和测试
- `mcp-server/src`、`shared-protocol/src`
- `skills`、`tests`、`docs`
- `README.md`、`CONTINUATION.md`；`need.md` 仅作为已标注的历史需求记录，可按需要保留
- `package.json`、`package-lock.json` 和 TypeScript 配置

## 不要提交

- 本地 Bridge token：`%USERPROFILE%/.minecraft-ai-world-builder/local-token`
- `minecraft-ai-builder-data/` 中的设计会话或本地状态
- Minecraft 存档、整合包目录和 `run/`
- `node_modules/`、`dist/`、`build/`、`.gradle/`
- `bridge-build-*`、`gradle-sandbox-cache/`
- 调试日志、临时 classpath 文件和崩溃报告
- 任何密码、访问令牌、私钥或个人电脑绝对路径

根目录 `.gitignore` 已覆盖这些常见内容，但首次提交前仍应运行 `git status` 和 `git diff --cached` 检查暂存文件。

## 推荐发布流程

1. 在 GitHub 上创建空仓库；不要预先创建 README 或 `.gitignore`。
2. 在本地项目目录初始化 Git 并提交源码。
3. 运行 `npm run typecheck`、`npm test` 和 `bridge-mod\gradlew.bat clean build`。
4. 推送 `main` 分支。
5. 用版本标签（例如 `v0.1.0`）创建 GitHub Release，并上传 `bridge-mod/build/libs/ai_world_builder_bridge-0.1.0.jar`。

公开仓库中的 MCP 配置示例必须使用 `<项目根目录>` 等占位符，不能写入开发者个人路径。运行时 token 由 Bridge 首次启动自动生成，不应复制到仓库或 issue 中。
