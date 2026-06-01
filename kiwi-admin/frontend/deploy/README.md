# kiwi-admin 前端远程部署脚本

`deploy.py` 在本地通过 **OpenSSH**（`ssh` / `scp`）与 **npm/ng** 将 kiwi-admin 前端静态资源**构建并上传到远程主机**。

部署参数在 YAML（`ssh`、`deploy` 块）中配置，见 `conf/build.example.yaml`。脚本仅接受一个可选参数：配置文件路径，默认为 `conf/build.local.yaml`：

```bash
python deploy.py
python deploy.py conf/build.local.yaml
python deploy.py /path/to/custom.yaml
```

## 前置条件

- **Python 3**（建议 3.10+）
- 依赖：`pip install -r requirements-remote.txt`（**PyYAML**、**paramiko**）
- 本机 **Node.js** 与 **npm**，且已在 `frontend/` 执行过 `npm install`
- 本机 **PATH** 中可用：`ssh`、`scp`
- 远端可 SSH 登录，且对 `deploy.remote_dir` 有写权限
- **`auth: password` 时**：优先使用 [**sshpass**](https://linux.die.net/man/1/sshpass) + 系统 `ssh`/`scp`；若未安装 sshpass，则自动使用 **paramiko**。**不支持**交互式键盘输密。

## 快速开始

1. 复制并编辑配置（勿提交含真实密码的副本；`conf/.gitignore` 已忽略常见本地文件名）：

   ```bash
   cp conf/build.example.yaml conf/build.local.yaml
   # 至少填写 ssh.hostname、ssh.user
   ```

2. 在 `deploy/` 目录安装依赖并部署：

   ```bash
   pip install -r requirements-remote.txt
   python deploy.py
   ```

## YAML 结构

### `ssh`

| 字段 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `hostname` | 是 | — | IP 或域名 |
| `user` | 是 | — | SSH 用户名 |
| `host` | 否 | `user@hostname` | 展示名，用于日志 |
| `port` | 否 | `22` | SSH 端口 |
| `auth` | 否 | `key` | `key` 或 `password` |
| `identity_file` | 否 | — | `auth: key` 时私钥路径，支持 `~` |
| `password` | 否 | — | `auth: password` 时明文密码（不推荐入库） |
| `password_env` | 否 | — | `auth: password` 时从环境变量读取密码 |
| `strict_host_key_checking` | 否 | `accept-new` | 传给 `ssh -o` |

`auth: password` 时须配置 `password` 或 `password_env` 之一。

### `deploy`

| 字段 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `backup_before_upload` | 否 | `true` | 上传前是否为远端已存在文件创建 `.bak` 备份 |
| `dist_dir` | 否 | `dist` | 本地构建产物目录（相对 `frontend/`） |
| `remote_dir` | 否 | `/var/www/kiwi-admin` | 远端静态资源根目录 |
| `npm` | 否 | PATH 中的 `npm` | npm 可执行文件 |
| `skip_build` | 否 | `false` | 为 `true` 时跳过构建，仅上传 |
| `build_script` | 否 | `ng` | 构建方式：`ng` 或 `npm` |
| `npm_script` | 否 | `build` | `build_script: npm` 时的 script 名 |
| `build_configuration` | 否 | `production` | `build_script: ng` 时的 `--configuration` |
| `base_href` | 否 | `/kiwi-admin/` | `build_script: ng` 时的 `--base-href` |
| `sync_mode` | 否 | `archive` | 同步方式：`archive`（tar.gz 打包单次上传，推荐）或 `sftp`（逐文件 SFTP） |

## 同步方式

默认 `sync_mode: archive`：本地将 `dist/` 打包为 tar.gz，**一次 SFTP 上传**后在远端解压。相比逐文件 SFTP（`sync_mode: sftp`），可显著减少连接次数与部署时间，尤其适合含大量静态资源（如图标）的前端产物。

远端需有 `tar` 命令（Linux 通常已预装）。`backup_before_upload: true` 时会在解压前将整个远端目录复制为 `remote_dir.bak.时间戳`。

```
frontend/
├── dist/                 # ng build 产物（本地生成，勿提交）
└── deploy/
    ├── README.md
    ├── deploy.py
    ├── requirements-remote.txt
    └── conf/
        ├── build.example.yaml
        └── .gitignore
```

## Nginx 示例

远端 `remote_dir` 部署完成后，可将 Web 服务器根路径指向前缀 `/kiwi-admin/`：

```nginx
location /kiwi-admin/ {
    alias /var/www/kiwi-admin/;
    try_files $uri $uri/ /kiwi-admin/index.html;
}
```
