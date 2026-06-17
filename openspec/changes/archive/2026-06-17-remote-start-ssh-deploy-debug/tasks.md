## 1. 部署配置与示例

- [x] 1.1 `kiwi-admin/backend/deploy/conf/build.example.yaml`：`ssh` + `deploy` 块；本地复制为 `build.local.yaml`
- [x] 1.2 `conf/.gitignore` 忽略含密钥/密码的本地配置副本

## 2. 部署脚本（`deploy.py`）

- [x] 2.1 Python 3 + PyYAML + paramiko；`python deploy.py` / `-c` 指定配置
- [x] 2.2 经 `ssh`/`scp`（密码模式可 fallback paramiko）连接 YAML 中的 `hostname`/`user`/`port`
- [x] 2.3 支持 `deploy.skip_build`、`deploy.incremental`、`deploy.mvn` 等（见 README）

## 3. 构建与上传

- [x] 3.1 仓库根 `mvn -pl kiwi-admin/backend -am package -DskipTests`；产物同步至 `backend/bin/`
- [x] 3.2 上传应用 jar、lib jar（增量/全量策略）、`config/`、`restart.sh`/`stop.sh` 至 `remote_dir`

## 4. 范围说明（非本 change 实现）

- [x] 4.1 **不做**远端 `java -jar` 启停、JDWP 调试子命令（初稿 Bash `remote_run.sh` 已废弃）；启停由远端手动执行已上传的 `restart.sh`/`stop.sh`
- [x] 4.2 前端同类工具：`kiwi-admin/frontend/deploy/deploy.py`（独立，本 change 以 backend 为准）

## 5. 验证

- [x] 5.1 `deploy.py --help` 与 README 可用；`mvn compile` / 打包路径与仓库一致
- [x] 5.2 人工：配置 `build.local.yaml` 后执行部署上传（启停在远端自行操作）
