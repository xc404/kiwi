## Context

kiwi-admin 后端为 Spring Boot 3.x、Java 17，本地通过 Maven 打包生成可执行 JAR（默认 `target/kiwi-admin-1.0.0.jar`）。使用者已有或可自建远程 Linux 测试机，希望通过 SSH 完成上传、启动与 IDE 远程调试，且 **主机、用户、密钥路径不得硬编码进仓库**，应使用独立 SSH 配置文件（或 `Include`）。

## Goals / Non-Goals

**Goals:**

- 提供 Bash 脚本（在 Git Bash / WSL / Linux / macOS 上可运行），通过 `ssh`/`scp`（或可选 `rsync`）连接远端。
- 使用 **独立 SSH 配置文件**：脚本通过环境变量或参数指定配置文件路径（如 `SSH_CONFIG`），调用 `ssh -F "$SSH_CONFIG" <Host>`，示例文件仅含占位符与注释，可 `.gitignore` 真实副本。
- **部署**：本地 `mvn package -DskipTests`（或由脚本参数跳过构建仅上传）后，将 JAR 传到远端约定目录并重启进程（`nohup java -jar` 或调用远端已有 wrapper 脚本；若远端用 systemd，可提供示例 unit 但不强制）。
- **远程调试**：远端 JVM 以 JDWP 监听（如 `transport=dt_socket,server=y,suspend=n,address=*:5005` 或绑定 `127.0.0.1` 后由 SSH 转发）；本地用 `ssh -L local:host:remote` 将调试端口映射到本机，IDE 连接 `localhost:localPort`。
- 脚本支持子命令或标志：`deploy`、`start`、`stop`、`logs`（最小集）、`debug`（部署并以调试参数启动）。

**Non-Goals:**

- 不在仓库中实现 Ansible、Kubernetes 或 CI/CD 流水线。
- 不负责远程机系统级初始化（安装 JDK、创建用户、防火墙）；仅以文档/注释说明前提条件。
- 不在本变更中修改 Java 应用业务代码或 Spring 配置（除非后续发现必须暴露的 management 端口等）。

## Decisions

1. **脚本语言：Bash**  
   - *Rationale*：与现有 `kiwi-admin/script/` 风格一致，SSH 工具链在类 Unix 环境通用。  
   - *Alternatives*：PowerShell 原生脚本（Windows 团队友好但对 scp/ssh 行为分叉多）— 采用 Bash + 文档说明 Windows 使用 Git Bash/WSL。

2. **SSH 配置外置**  
   - *Rationale*：满足「单独配置文件」与安全要求。  
   - *Convention*：示例路径 `kiwi-admin/script/ssh/remote.example.conf`，复制为 `remote.conf`（加入 `.gitignore` 可选）或通过 `SSH_CONFIG=$HOME/.ssh/kiwi-remote` 指向。  
   - *Alternatives*：仅用命令行 `user@host` + `IdentityFile` 环境变量 — 灵活性差，拒绝。

3. **产物与路径**  
   - *Rationale*：与 Maven 默认输出一致。  
   - *Decision*：`JAR` 默认 `backend/target/kiwi-admin-1.0.0.jar`（可通过环境变量覆盖）；远端目录 `REMOTE_APP_DIR`（默认 `/opt/kiwi-admin` 或示例中的占位符）。

4. **调试模型**  
   - *Decision*：JDWP 在远端监听；本地 **必须** 使用 SSH 本地端口转发访问，避免在公网直接暴露 5005。  
   - *Alternatives*：仅 VPN 内网直连 JDWP — 可作为可选说明，不作为默认路径。

5. **进程管理**  
   - *Decision*：最小实现为 `pkill -f` + `nohup java ... &` 或写入 `app.pid`；进阶文档可给 systemd 片段。  
   - *Trade-off*：不如 systemd 健壮，但零远程依赖、易落地。

## Risks / Trade-offs

- **[Risk] 误杀进程**：宽泛的 `pkill -f kiwi-admin` 可能影响同机其他实例 → **Mitigation**：PID 文件或明确 JAR 路径匹配。  
- **[Risk] JDWP 绑定 `*:port` 在内网仍暴露** → **Mitigation**：文档默认推荐 `127.0.0.1:5005` + SSH `-L`，并在 spec 中要求脚本默认安全绑定。  
- **[Risk] Windows 路径与 Bash** → **Mitigation**：文档写明 Git Bash/WSL，脚本使用 `cygpath` 可选兼容（若实现成本高则仅在 README 说明）。

## Migration Plan

1. 合并后开发者在本地复制 SSH 示例并配置 `Host`。  
2. 首次部署前在远端创建目录、上传 JAR、验证 `java -version`。  
3. 回滚：保留上一版 JAR 备份（脚本可提供 `cp` 备份步骤），停止进程后换回旧 JAR 再启动。

## Open Questions

- 远端是否统一使用固定非 root 用户与目录（可在 `tasks` 实现时以变量固化）。  
- 是否需要前端静态资源一并部署（本 proposal 以 **后端 JAR** 为主；若 monorepo 有前端，可后续扩展第二条 rsync 目标）。
