# remote-ssh-deploy-debug Specification

## Purpose
TBD - created by archiving change remote-start-ssh-deploy-debug. Update Purpose after archive.
## Requirements
### Requirement: SSH 部署连接与配置外置

`kiwi-admin` 后端远程部署工具 MUST 通过 OpenSSH（`ssh`/`scp`）或等价的受控传输连接远程主机；连接参数 MUST 来自本地 YAML 配置文件（如 `conf/build.local.yaml`），MUST NOT 在仓库中提交真实密码或非示例性敏感信息。`conf/.gitignore` SHOULD 忽略常见本地配置副本。

#### Scenario: 使用本地 YAML 部署

- **WHEN** 使用者复制 `build.example.yaml` 为 `build.local.yaml` 并填写 `ssh.hostname`、`ssh.user` 与认证方式
- **THEN** `deploy.py` 能使用该配置建立上传会话，且不依赖仓库内写死的 `user@hostname`

### Requirement: 构建产物上传

部署工具 MUST 在仓库根执行 Maven 打包（`mvn -pl kiwi-admin/backend -am package`，除非 `deploy.skip_build: true`），并将应用 jar（及按策略所需的 lib jar）、Spring 配置文件与部署辅助脚本上传至 YAML 中 `deploy.remote_dir`。

#### Scenario: 默认增量上传应用 jar

- **WHEN** `deploy.incremental` 为 true 且远端 lib jar 未过期
- **THEN** 工具构建并上传应用 jar（及必要的 config/脚本），不要求每次全量上传 lib jar

### Requirement: 部署工具不承担远端 JVM 启停与调试

本变更范围内的 `deploy.py` MUST 仅负责构建与文件同步；MUST NOT 要求在同一入口实现远端 `nohup java` 启停或 JDWP 监听。远端进程管理 MAY 通过已上传的 `restart.sh`/`stop.sh` 由操作员在服务器上手动执行。

#### Scenario: 上传后不自动拉起进程

- **WHEN** 使用者成功运行 `python deploy.py` 完成上传
- **THEN** 工具结束且不要求远端应用已自动重启；是否重启由操作员在远端执行 shell 脚本或既有运维流程决定

