# kiwi-admin 远程部署脚本

`remote_run.py` 在本地通过 **OpenSSH**（`ssh` / `scp`）与 **Maven** 完成 kiwi-admin 后端的远程部署、进程启停以及 **JDWP** 远程调试准备。连接信息使用 **单主机 YAML** 文件描述（见 `ssh/remote.example.yaml`）。

## 前置条件

- **Python 3**（建议 3.10+）
- 依赖：`pip install -r requirements-remote.txt`（当前为 **PyYAML**）
- 本机 **PATH** 中可用：`ssh`、`scp`、`mvn`
- 远端可 SSH 登录，且已具备运行 JAR 的 Java 环境
- **`auth: password` 时**：需安装 [**sshpass**](https://linux.die.net/man/1/sshpass)（脚本通过 `SSHPASS` 传密；stdin 用于远程脚本，**不支持**交互式键盘输密）

## 快速开始

1. 复制并编辑配置（勿提交含真实密码的副本；`ssh/.gitignore` 已忽略常见本地文件名）：

   ```bash
   cp ssh/remote.example.yaml ssh/remote.local.yaml
   # 编辑 hostname / user / port / auth 等
   ```

2. 安装依赖并执行子命令（连接参数写在子命令**之后**）：

   ```bash
   pip install -r requirements-remote.txt
   python remote_run.py deploy --config ssh/remote.local.yaml
   python remote_run.py start --config ssh/remote.local.yaml
   python remote_run.py --help
   python remote_run.py deploy --help
   ```

## YAML 配置说明（单主机）

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

密码优先级：**YAML `password`** → **`password_env` 所指变量** → **`KIWI_SSH_PASSWORD`**。

## 子命令一览

| 子命令 | 作用 |
|--------|------|
| `deploy` | 可选 `mvn package`，上传 JAR 到远端（上传前可备份旧包为 `*.bak`） |
| `stop` | 按 `app.pid` 与 `pgrep -f` 停止远端进程 |
| `start` | `nohup java -jar`，日志 `app.log`，PID `app.pid`；可加 `--debug` 启用 JDWP |
| `restart` | `stop` 后 `start` |
| `debug-deploy` | `deploy` 后以 JDWP 方式启动 |

常用选项：

- **`--config`**：YAML 路径（必填）
- **`--hostname` / `--user` / `--port`**：覆盖 YAML 中对应项
- **`--skip-build`**：`deploy` / `debug-deploy` 时跳过 `mvn package`
- **`--no-incremental`**：关闭增量策略（见下）
- **`--local-jar` / `--remote-dir` / `--remote-jar-name`**：产物与远端路径
- **`--jdwp-port` / `--jdwp-bind` / `--local-debug-port`**：调试相关（默认 JDWP 绑定 `127.0.0.1`，公网不暴露）

## 增量策略（默认开启）

- **构建**：未使用 `--skip-build` 时，若本地 JAR 已存在且其修改时间不早于 `backend/pom.xml` 与 `backend/src/main` 下文件最新修改时间，则跳过 `mvn package`。
- **上传**：比较本地与远端 JAR 的 **SHA256**；一致则跳过备份与 `scp`。

需要每次都完整构建并上传时使用 **`--no-incremental`**。

## 远程调试（JDWP）

远端以 JDWP 监听（默认 `127.0.0.1:5005`）时，在本机另开终端做端口转发，再在 IDE 里 **Remote JVM Debug** 连接本机端口，例如：

```bash
ssh -p <port> -L <本地端口>:127.0.0.1:<jdwp端口> <user>@<hostname> -N
```

具体端口与 `user`/`hostname` 以你的 YAML 与命令行参数为准。

## 文件布局

```
script/
├── README.md                 # 本说明
├── remote_run.py             # 入口脚本
├── requirements-remote.txt   # Python 依赖
└── ssh/
    ├── remote.example.yaml   # 配置示例
    └── .gitignore            # 忽略本地私密配置
```
