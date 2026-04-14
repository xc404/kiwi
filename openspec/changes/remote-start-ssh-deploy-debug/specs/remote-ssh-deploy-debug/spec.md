## ADDED Requirements

### Requirement: SSH 配置与连接

部署与调试脚本 MUST 通过 OpenSSH 客户端连接远程主机，且 MUST 支持使用 **独立配置文件**（例如通过 `ssh -F <path>` 或等价方式），不得在仓库中提交真实主机名、私钥内容或非示例性的敏感信息。

#### Scenario: 使用外置 SSH 配置调用

- **WHEN** 使用者设置有效的 `SSH_CONFIG`（或脚本约定的配置路径参数）且其中定义了目标 `Host`
- **THEN** 脚本使用该配置文件建立 SSH/SCP 会话，且不依赖仓库内写死的 `user@hostname`

### Requirement: 构建产物部署

脚本 MUST 支持将 kiwi-admin 的可执行 JAR 从本地上传到远端指定目录，并 MUST 允许通过环境变量或参数覆盖本地 JAR 路径与远端部署目录。

#### Scenario: 部署默认 Maven 产物

- **WHEN** 使用者已在本地成功执行 Maven 打包并指定与默认一致的产物路径
- **THEN** 脚本将该 JAR 上传至远端目标目录并完成覆盖或原子替换（以设计为准），使远端可以启动该版本

### Requirement: 远程启动与停止

脚本 MUST 提供在远端启动与停止应用的能力（至少包括启动与停止之一成对出现），且 MUST 在停止或重启时避免无差别终止无关 Java 进程（例如通过 PID 文件或明确的 JAR 路径匹配）。

#### Scenario: 重启后运行新版本

- **WHEN** 使用者执行脚本的部署并重启流程
- **THEN** 远端进程加载新上传的 JAR，且旧进程已退出

### Requirement: 远程 JVM 调试

脚本 MUST 支持以启用 JDWP 的方式在远端启动 JVM（可配置端口与 suspend 行为），且 MUST 在文档或 `--help` 中说明本地 IDE 如何通过 SSH 端口转发连接调试端口。

#### Scenario: 本地 IDE 经端口转发附加调试器

- **WHEN** 使用者在远端以 JDWP 监听指定端口，并在本地建立到该端口的 SSH 本地转发
- **THEN** IDE 可连接到本机转发端口并成功附加到远端 JVM（在应用与网络正常的前提下）

### Requirement: 可操作性与安全提示

脚本 SHOULD 提供 `--help` 或等价用法说明；对于调试监听地址，MUST 默认采用不将 JDWP 暴露到公网接口的安全默认（例如仅监听 `127.0.0.1`），若允许监听所有接口则 MUST 在文档中明确风险并依赖 SSH 隧道或防火墙。

#### Scenario: 默认调试绑定为回环地址

- **WHEN** 使用者使用脚本提供的「调试启动」且未显式覆盖绑定地址
- **THEN** JDWP 监听配置为仅本机可达的地址，需通过 SSH 转发才能从开发者机器调试
