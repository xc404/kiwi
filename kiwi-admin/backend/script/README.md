# kiwi-admin 远程部署脚本

`deploy.py` 在本地通过 **OpenSSH**（`ssh` / `scp`）与 **Maven** 将 kiwi-admin 后端 JAR **构建并上传到远程主机**。SSH 连接信息写在 **`conf/` 下 YAML**（`ssh` 块），见 `conf/remote.example.yaml`。

构建与仓库根 [README](../../README.md) 一致：在 **kiwi 仓库根**执行 `mvn -pl kiwi-admin/backend -am package -DskipTests`，以按顺序编译 **kiwi-common**、**kiwi-bpmn-*** 等依赖模块。请使用完整克隆的仓库，勿只拷贝 `kiwi-admin` 子目录（否则无法解析父 POM 与反应堆）。

## 前置条件

- **Python 3**（建议 3.10+）
- 依赖：`pip install -r requirements-remote.txt`（**PyYAML**、**paramiko**）
- 本机 **PATH** 中可用：`ssh`、`scp`；需要本地构建时安装 **Maven**（脚本会查找 `mvn` / `mvn.cmd` 或 `MAVEN_HOME`、`MVN`；Windows 若仍报错可用 `--mvn "C:\\path\\to\\mvn.cmd"`）
- 远端可 SSH 登录，且对 `--remote-dir` 有写权限
- **`auth: password` 时**：优先使用 [**sshpass**](https://linux.die.net/man/1/sshpass) + 系统 `ssh`/`scp`；若未安装 sshpass，则自动使用 **paramiko**（已随 requirements 安装）。**不支持**交互式键盘输密。

## 快速开始

1. 复制并编辑配置（勿提交含真实密码的副本；`conf/.gitignore` 已忽略常见本地文件名）：

   ```bash
   cp conf/remote.example.yaml conf/remote.local.yaml
   # 编辑 ssh.*
   ```

2. 在 `script/` 目录安装依赖并部署：

   ```bash
   pip install -r requirements-remote.txt
   python deploy.py --config conf/remote.local.yaml
   python deploy.py --help
   ```

仅上传已有 JAR、跳过构建：

```bash
python deploy.py --config conf/remote.local.yaml --skip-build
```

## YAML 结构

根节点为映射，主要包含 **`ssh`** 块：

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

**兼容**：旧版无 `ssh:` 嵌套、字段平铺在根下的 YAML 仍可读；根节点中的 `spring` 等字段会被忽略。

密码优先级（SSH）：**YAML `password`** → **`password_env` 所指变量** → **`KIWI_SSH_PASSWORD`**。

## 命令行选项

| 选项 | 说明 |
|------|------|
| `--config` | YAML 路径（必填） |
| `--hostname` / `--user` / `--port` | 覆盖 YAML 中 `ssh.*` 对应项 |
| `--local-jar` | 本地 JAR 路径（默认：`backend/target/kiwi-admin-1.0.0.jar`） |
| `--remote-dir` | 远端目录（默认：`/opt/kiwi-admin`） |
| `--remote-jar-name` | 远端 JAR 文件名（默认：`kiwi-admin.jar`） |
| `--mvn` | 指定 Maven 可执行文件路径 |
| `--skip-build` | 跳过 `mvn package`，仅上传 |
| `--no-incremental` | 关闭增量策略（见下） |

## 增量策略（默认开启）

- **构建**：未使用 `--skip-build` 时，若本地 JAR 已存在且不早于根 POM、backend 与各依赖模块 POM 及 `src/main` 下最新修改时间，则跳过 `mvn package`。
- **上传**：比较本地与远端 JAR 的 **SHA256**；一致则跳过备份与 `scp`。上传前若远端已有同名 JAR，会先复制为 `*.bak`。

需要每次都完整构建并上传时使用 **`--no-incremental`**。

## 文件布局

```
script/
├── README.md                 # 本说明
├── deploy.py                 # 构建并上传 JAR
├── requirements-remote.txt   # Python 依赖
├── stop.sh / restart.sh      # 远端进程启停（与 deploy.py 独立）
└── conf/
    ├── remote.example.yaml   # 配置示例（ssh）
    └── .gitignore            # 忽略本地私密配置
```
