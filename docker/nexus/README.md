# Kiwi Nexus 远程市场

Nexus 3 作为公网侧制品仓库，存放模板包（`.kiwi-template-pack`）、插件 JAR 与 `market/index.json` 索引。

## 快速启动

```bash
docker compose -f docker/nexus/docker-compose.yml up -d
```

首次启动约需 1–2 分钟。管理界面：<http://localhost:8081>

## 初始化

1. 获取初始管理员密码：

   ```bash
   docker exec kiwi-nexus cat /nexus-data/admin.password
   ```

2. 登录后修改密码，完成设置向导。

3. 创建仓库（也可运行自动化脚本）：

   ```bash
   # Linux/macOS
   NEXUS_URL=http://localhost:8081 NEXUS_USER=admin NEXUS_PASSWORD=<password> \
     ./scripts/nexus/setup-repos.sh

   # Windows PowerShell
   $env:NEXUS_URL='http://localhost:8081'
   $env:NEXUS_USER='admin'
   $env:NEXUS_PASSWORD='<password>'
   .\scripts\nexus\setup-repos.ps1
   ```

   将创建：

   | 仓库 | 类型 | 用途 |
   |------|------|------|
   | `kiwi-market-raw` | Raw (hosted) | 模板包、`market/index.json`、manifest |
   | `kiwi-market-plugins` | Maven2 (hosted) | 插件 JAR（可选，第一版也可用 Raw） |

4. （推荐）为公网只读下载开启匿名访问，或使用专用只读账号。

5. 创建发布账号（对两仓库有写权限），供 CI / `scripts/market/publish` 使用。

## 验证上传下载

```bash
NEXUS_URL=http://localhost:8081 NEXUS_USER=admin NEXUS_PASSWORD=<password> \
  ./scripts/nexus/verify-upload.sh
```

## Kiwi 配置

在 `application-local.yml` 中启用远程市场：

```yaml
kiwi:
  bpm:
    remote-market:
      enabled: true
      kiwi-version: 1.0.0-SNAPSHOT
      sources:
        - id: nexus-local
          name: Local Nexus Market
          base-url: http://localhost:8081/repository/kiwi-market-raw/
          index-path: market/index.json
          username: admin
          password: <password>
```

## 目录约定

见 `docs/bpm-remote-market/README.md` 与 `docs/bpm-remote-market/index.schema.json`。
