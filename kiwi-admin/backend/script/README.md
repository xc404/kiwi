# kiwi-admin 远程部署脚本

`remote_run.py` 在本地通过 **OpenSSH**（`ssh` / `scp`）与 **Maven** 完成 kiwi-admin 后端的远程部署、进程启停以及 **JDWP** 远程调试准备。连接与 Spring 环境使用 **`conf/` 下结构化 YAML**（`ssh` + `spring`），见 `conf/remote.example.yaml`。

构建时与仓库根 [README](../../README.md) 一致：在 **kiwi 仓库根**执行 `mvn -pl kiwi-admin/backend -am package -DskipTests`，以按顺序编译 **kiwi-common**、**kiwi-bpmn-*** 等依赖模块。请使用完整克隆的仓库，勿只拷贝 `kiwi-admin` 子目录（否则无法解析父 POM 与反应堆）。

## 前置条件

- **Python 3**（建议 3.10+）
- 依赖：`pip install -r requirements-remote.txt`（**PyYAML**、**paramiko**）
- 本机 **PATH** 中可用：`ssh`、`scp`；**构建** 时需要 **Maven**（脚本会查找 `mvn` / `mvn.cmd` 或 `MAVEN_HOME`、`MVN`；Windows 若仍报错可用 `--mvn "C:\\path\\to\\mvn.cmd"`）
- 远端可 SSH 登录，且已具备运行 JAR 的 Java 环境
- **`auth: password` 时**：优先使用 [**sshpass**](https://linux.die.net/man/1/sshpass) + 系统 `ssh`/`scp`；若未安装 sshpass，则自动使用 **paramiko**（已随 requirements 安装）。远程脚本通过 stdin 传递，**不支持**交互式键盘输密。

## 快速开始

1. 复制并编辑配置（勿提交含真实密码的副本；`conf/.gitignore` 已忽略常见本地文件名）：

   ```bash
   cp conf/remote.example.yaml conf/remote.local.yaml
   # 编辑 ssh.* / spring.*
   ```

2. 安装依赖并执行子命令（连接参数写在子命令**之后**）：

   ```bash
   pip install -r requirements-remote.txt
   python remote_run.py deploy --config conf/remote.local.yaml
   python remote_run.py start --config conf/remote.local.yaml
   python remote_run.py --help
   python remote_run.py deploy --help
   ```

## YAML 结构

根节点为映射，主要包含：

### `ssh`（必填块）

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

**兼容**：旧版无 `ssh:` 嵌套、字段平铺在根下的 YAML 仍可读（会忽略非 ssh 的保留键如 `spring` 之外的扩展）。

### `spring`（可选）

| 字段 | 说明 |
|------|------|
| `profiles_active` | 字符串，逗号分隔 profile；远端使用 **`java -Dspring.profiles.active=… -jar …`**（建议加引号如 `"local,dev"`，避免 YAML 误解析） |
| `profiles` | 字符串或字符串列表；与 `profiles_active` 二选一，列表会拼成逗号分隔 |

命令行 **`--spring-profiles`** 可覆盖 YAML（空字符串表示不传 profile）。

密码优先级（SSH）：**YAML `password`** → **`password_env` 所指变量** → **`KIWI_SSH_PASSWORD`**。

## 子命令一览

| 子命令 | 作用 |
|--------|------|
| `deploy` | 可选 `mvn package`，上传 JAR 到远端（上传前可备份旧包为 `*.bak`） |
| `stop` | 按 `app.pid` 与 `pgrep -f` 停止远端进程 |
| `start` | 启动并检测 PID；成功后默认 **tail -f app.log**；可加 `--debug`、`--no-tail` |
| `restart` | `stop` 后 `start` |
| `debug-deploy` | `deploy` 后以 JDWP 方式启动 |

`start` / `restart` / `debug-deploy`：启动后轮询远端 PID（约 20s）；成功则经 SSH 执行 **tail -f** 远端 `app.log`（Ctrl+C 结束）；失败则把 **app.log 末尾** 打到 stderr。

常用选项：

- **`--config`**：YAML 路径（必填）
- **`--hostname` / `--user` / `--port`**：覆盖 YAML 中 `ssh.*` 对应项
- **`--spring-profiles`**：覆盖 `spring.profiles_active`
- **`--skip-build`**：`deploy` / `debug-deploy` 时跳过 `mvn package`
- **`--no-incremental`**：关闭增量策略（见下）
- **`--no-tail`**：`start` / `restart` / `debug-deploy` 在启动检测通过后不执行 tail
- **`--local-jar` / `--remote-dir` / `--remote-jar-name`**：产物与远端路径
- **`--jdwp-port` / `--jdwp-bind`**：JDWP 监听端口与绑定地址（默认绑定 **`0.0.0.0`**，便于 IDE 直连「`ssh.hostname` : `jdwp-port`」）

## 增量策略（默认开启）

- **构建**：未使用 `--skip-build` 时，若本地 JAR 已存在且不早于根 POM、backend 与各依赖模块 POM 及 `src/main` 下最新修改时间，则跳过 `mvn package`。
- **上传**：比较本地与远端 JAR 的 **SHA256**；一致则跳过备份与 `scp`。

需要每次都完整构建并上传时使用 **`--no-incremental`**。

## 远程调试（JDWP）

默认 **`--jdwp-bind` 为 `0.0.0.0`**，JDWP 在远端监听所有网卡；在 IDE 的 **Remote JVM Debug** 中填写：

- **Host**：与配置里 **`ssh.hostname`** 相同（即你 SSH 登录用的那台机器的地址，公网 IP 或内网 IP，以你实际可达为准）。
- **Port**：与 **`--jdwp-port`** 一致（默认 `5005`）。

无需在本机做 `ssh -L` 端口转发。若仅在远端本机调试，可将 **`--jdwp-bind 127.0.0.1`** 并改用 SSH 转发（安全性更好）。

## 防火墙与安全组

请保证从你运行 IDE 的机器到目标主机 **网络可达**。除云厂商 **安全组 / 网络 ACL** 外，若 Linux 启用了本机防火墙（`firewalld`、`ufw` 等），需在**服务器上**放行下表端口（端口以你实际配置为准）。

| 用途 | 端口（示例） | 说明 |
|------|----------------|------|
| SSH | `22` 或 YAML 中 `ssh.port` | `remote_run.py` 的 `ssh`/`scp` |
| JDWP | `--jdwp-port`（默认 `5005`） | `--debug` / `debug-deploy` 远程调试 |
| HTTP（可选） | 默认多为 **`8088`**（见 `application.yml` 的 `server.port`） | 浏览器访问管理端 API |

以下为 **在默认区域放行端口** 的示例；**生产环境**请把来源收紧为你的办公网段或固定 IP，调试结束后 **`--remove`** 或删除规则。

### firewalld（RHEL / CentOS Stream / Fedora 等）

```bash
# 查看默认区域（多为 public）
sudo firewall-cmd --get-default-zone

# 持久放行 SSH（若尚未开放）
sudo firewall-cmd --permanent --add-service=ssh
# 或显式端口（SSH 非 22 时）
# sudo firewall-cmd --permanent --add-port=2222/tcp

# 放行 JDWP（示例 5005）与 HTTP（示例 8088）
sudo firewall-cmd --permanent --add-port=5005/tcp
sudo firewall-cmd --permanent --add-port=8088/tcp

# 仅允许某网段访问 JDWP（推荐，将 198.51.100.0/24 换成你的网段）
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="198.51.100.0/24" port port="5005" protocol="tcp" accept'

# 重载生效
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```

撤销示例（按名称删除 rich rule 需先 `firewall-cmd --list-rich-rules` 复制完整 rule 再 `--remove-rich-rule='...'`），简单端口可用：

```bash
sudo firewall-cmd --permanent --remove-port=5005/tcp
sudo firewall-cmd --reload
```

### UFW（Ubuntu / Debian 等）

```bash
# 启用 UFW（首次会提示默认放行 SSH，避免锁死）
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
# 若 SSH 为自定义端口：sudo ufw allow 2222/tcp comment 'SSH'

sudo ufw allow 8088/tcp comment 'kiwi-admin HTTP'
# JDWP：仅允许你的开发机 IP（示例）
sudo ufw allow 5005/tcp comment 'JDWP'

sudo ufw enable
sudo ufw status numbered
```

删除某条规则：`sudo ufw delete <编号>`。

### 云安全组（通用思路）

在控制台为实例绑定安全组，入站规则示例：

- **TCP 22**（或你的 SSH 端口）：来源「你的 IP / 跳板机网段」  
- **TCP 5005**：来源「运行 IDE 的公网 IP 或 VPN 网段」（调试期）  
- **TCP 8088**：来源按访问需求（如办公网）

调试结束后删除或收紧 **5005** 规则；若 JDWP 仅绑定 `127.0.0.1`，则无需对公网开放 5005，改用 SSH 本地转发即可。

## 文件布局

```
script/
├── README.md                 # 本说明
├── remote_run.py             # 入口脚本
├── requirements-remote.txt   # Python 依赖
└── conf/
    ├── remote.example.yaml   # 配置示例（ssh + spring）
    └── .gitignore            # 忽略本地私密配置
```
