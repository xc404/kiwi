## 1. SSH 配置与示例

- [ ] 1.1 在 `kiwi-admin/script/ssh/` 下新增示例 SSH 配置文件（占位 `Host`、User、`IdentityFile` 注释），并说明复制为本地私有路径或通过 `SSH_CONFIG` 引用
- [ ] 1.2 （可选）将 `*.local.conf` 或 `remote.conf` 加入 `.gitignore`，避免误提交真实配置

## 2. 核心 Bash 脚本

- [ ] 2.1 实现主脚本（可命名 `remote_run.sh` 或与现有 `run_in_test.sh` 对齐）：解析 `--help`、子命令或标志（deploy / stop / start / restart / debug-deploy）
- [ ] 2.2 统一封装 `ssh_cmd` / `scp_cmd`，始终传入 `-F "$SSH_CONFIG"`（若设置）及目标 `SSH_HOST`（config 中的 Host 别名）
- [ ] 2.3 支持环境变量：`SSH_CONFIG`、`SSH_HOST`、`LOCAL_JAR`、`REMOTE_DIR`、`JDWP_PORT`、调试绑定地址等，并在 `--help` 中列出默认值

## 3. 构建与上传

- [ ] 3.1 增加可选步骤：在脚本中调用 `mvn -f kiwi-admin/backend/pom.xml package -DskipTests`（可通过 `--skip-build` 跳过）
- [ ] 3.2 使用 `scp`（或 `rsync` 若可用）将 JAR 上传至远端 `REMOTE_DIR`，上传前可对旧 JAR 做备份（如 `*.bak`）

## 4. 远端启停与调试

- [ ] 4.1 实现停止：通过 PID 文件或 `pgrep -f` 匹配远端 JAR 路径后 `kill`，避免宽泛 `pkill java`
- [ ] 4.2 实现启动：`nohup java -jar` 写日志与 PID；`debug` 模式追加 JDWP，默认 `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:<port>`
- [ ] 4.3 在文档或 `--help` 中给出本地端口转发示例：`ssh -F ... -L <local>:127.0.0.1:<jdwp> ...`，便于 IDE 连接

## 5. 验证与收尾

- [ ] 5.1 在 Git Bash（Windows）下做一次 dry-run 或手动演练：help 输出、路径解析、SSH 调用参数正确
- [ ] 5.2 将本 `tasks.md` 中已完成项勾选为 `- [x]`（实施完成后）
