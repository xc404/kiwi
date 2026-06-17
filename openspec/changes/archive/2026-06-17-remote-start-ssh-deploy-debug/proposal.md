## Why

在远程测试机上运行 kiwi-admin 时，需要可重复的部署与远程调试流程；运维与机器细节由使用者自行配置，仓库侧应提供基于 SSH 的脚本与约定，避免把密钥与主机信息写死在项目里。

## What Changes

- 在 `kiwi-admin/script/`（或约定目录）下增加可在本地调用的 **远程启动/部署脚本**，通过 SSH 在远端执行构建产物上传、解压/覆盖、进程启停等步骤（具体步骤以 design 为准）。
- 提供 **独立 SSH 配置文件** 的用法与示例（例如 `~/.ssh/config` 片段或 `script/ssh/kiwi-remote.conf.example`），通过 `ssh -F` 或 `Include` 指向，不把真实主机名与用户密钥路径提交进仓库。
- 支持 **远程 JVM 调试**：在远端以 JDWP 监听（可配置端口），本地 IDE 通过 SSH 端口转发连接；脚本提供「仅部署」「部署并调试监听」等模式。
- 可选：补充简短 README 或脚本内 `--help`，说明环境变量与前置条件（Java、目录、systemd 等由远程机器预置）。

## Capabilities

### New Capabilities

- `remote-ssh-deploy-debug`: kiwi-admin 通过 SSH 的远程部署、启动与 JDWP 远程调试约定（脚本入口、SSH 配置分离、安全与可移植性要求）。

### Modified Capabilities

- （无）现有 `openspec/specs/` 下无与本需求重叠的需改行为规格。

## Impact

- 主要影响 `kiwi-admin/script/` 下的新增/修改 shell 脚本与示例配置；不强制要求改 Java 业务代码。
- 可能增加 Maven 打包与 `scp`/`rsync` 依赖；构建产物路径需与现有 `backend` 打包输出一致。
- 使用者需在本地安装 OpenSSH 客户端，远程端需可 SSH 登录、具备运行 Java 应用的环境。
