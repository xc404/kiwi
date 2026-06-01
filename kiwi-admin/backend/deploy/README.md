# kiwi-admin 远程部署脚本

`deploy.py` 在本地通过 **OpenSSH**（`ssh` / `scp`）与 **Maven** 将 kiwi-admin 后端构建产物**构建并上传到远程主机**。连接与部署选项均写在 **`conf/build.local.yaml`**（`ssh`、`deploy` 块），见 `conf/build.example.yaml`。

构建与仓库根 [README](../../README.md) 一致：在 **kiwi 仓库根**执行 `mvn -pl kiwi-admin/backend -am package -DskipTests`，以按顺序编译 **kiwi-common**、**kiwi-bpmn-*** 等依赖模块。Maven 输出在 `target/`，`deploy.py` 会将其同步到 **`backend/bin/`**（应用 jar + 依赖 lib jar）再上传。请使用完整克隆的仓库，勿只拷贝 `kiwi-admin` 子目录。

## 前置条件

- **Python 3**（建议 3.10+）
- 依赖：`pip install -r requirements-remote.txt`（**PyYAML**、**paramiko**）
- 本机 **PATH** 中可用：`ssh`、`scp`；需要本地构建时安装 **Maven**（脚本会查找 `mvn` / `mvn.cmd` 或 `MAVEN_HOME`、`MVN`；Windows 若仍报错可在 YAML 的 `deploy.mvn` 指定完整路径）
- 远端可 SSH 登录，且对 `deploy.remote_dir` 有写权限
- **`auth: password` 时**：优先使用 [**sshpass**](https://linux.die.net/man/1/sshpass) + 系统 `ssh`/`scp`；若未安装 sshpass，则自动使用 **paramiko**（已随 requirements 安装）。**不支持**交互式键盘输密。

## 快速开始

1. 复制并编辑配置（勿提交含真实密码的副本；`conf/.gitignore` 已忽略常见本地文件名）：

   ```bash
   cp conf/build.example.yaml conf/build.local.yaml
   # 编辑 ssh.*、deploy.*
   ```

2. 在 `script/` 目录安装依赖并部署：

   ```bash
   pip install -r requirements-remote.txt
   python deploy.py
   ```

仅上传已有产物、跳过构建：在 `build.local.yaml` 中设置 `deploy.skip_build: true`。

## 部署策略

| `incremental` | 上传内容 |
|---------------|----------|
| **true**（默认） | 通常仅 **应用 jar**；远端尚无 lib jar、或本地 lib jar 因 POM 变更过期时，自动上传 **应用 jar + lib jar** |
| **false** | 始终构建并上传 **应用 jar + lib jar**（覆盖远端，不做 lib 过期判断） |

远端启动见 `restart.sh`：使用 `-cp lib:app` 启动（需已存在 lib jar；首次部署在增量模式下也会自动补传 lib jar）。

## YAML 结构

### `ssh`

| 字段 | 说明 |
|------|------|
| `host` | 可选；展示名，用于日志 |
| `hostname` | 必填；IP 或域名 |
| `user` | 必填；SSH 用户名 |
| `port` | 端口，默认 `22` |
| `auth` | `key` 或 `password` |
| `identity_file` | `auth: key` 时可选；私钥路径，支持 `~` |
| `password` | `auth: password` 时可选；明文密码（不推荐入库） |
| `password_env` | `auth: password` 时可选；从该环境变量名读取密码，未设时还可使用 `KIWI_SSH_PASSWORD` |
| `strict_host_key_checking` | 可选；默认 `accept-new`，传给 `ssh -o` |

### `deploy`

| 字段 | 说明 |
|------|------|
| `spring_profiles_active` | Spring profile（默认 `dev`）；同步 `application.yml`、`application-<profile>.yml` 到 `bin/config/` 并上传远端 `config/` |
| `app_jar` | 部署用应用 jar（默认 `bin/kiwi-admin.jar`；`local_jar` 为兼容别名） |
| `lib_jar` | 部署用依赖 lib jar（默认 `bin/kiwi-admin-lib.jar`） |
| `config_dir` | 本地配置目录（默认与 `app_jar` 同级的 `config/`） |
| `remote_dir` | 远端目录（默认：`/opt/kiwi-admin`） |
| `remote_app_name` | 远端应用 jar 名（默认：`kiwi-admin.jar`） |
| `remote_lib_name` | 远端 lib jar 名（默认：`kiwi-admin-lib.jar`） |
| `mvn` | 可选；Maven 可执行文件路径 |
| `skip_build` | 为 `true` 时跳过 `mvn package`，仅上传 |
| `incremental` | 默认 `true`；见上表 |
| `no_incremental` | 为 `true` 时等同 `incremental: false` |

**兼容**：`remote_jar_name` 映射为 `remote_app_name`；旧版 `deploy.package=zip` 会提示已废弃。

## 增量细节（`incremental: true`）

- **构建**：每次部署均执行 `mvn package`（除非 `skip_build: true`）。需要 lib jar 时 deploy 显式加 `-Plib-jar`；若远端已有 lib jar 且本地 lib 未因 POM 过期，则不加该 profile（不生成 `*-lib.jar`，缩短构建时间）。
- **上传**：应用 jar 按 SHA256 增量；`config/` 与 `restart.sh`/`stop.sh` 在远端不存在时直接上传，内容不一致时会提示确认是否覆盖。
- **全量触发**：`incremental: false`，远端缺少 lib jar，或本地 lib jar 早于反应堆 POM 修改时间（本地无 lib 但远端有时视为未过期，仍跳过 lib 构建）。

## 文件布局

```
backend/
├── bin/
│   ├── restart.sh、stop.sh   # 由 deploy 上传至远端部署目录
│   ├── kiwi-admin.jar、kiwi-admin-lib.jar  # Maven 同步产物（本地生成，勿提交）
│   └── config/               # Spring 配置同步目录（见仓库根 .gitignore）
└── script/
    ├── README.md
    ├── deploy.py
    ├── requirements-remote.txt
    └── conf/
        ├── build.example.yaml
        └── .gitignore
```
