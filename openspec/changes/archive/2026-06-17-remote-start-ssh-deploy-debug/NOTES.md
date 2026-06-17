# 归档说明

**日期：** 2026-06-17

## 范围收敛：仅 deploy

初稿为 Bash `remote_run.sh`（deploy / start / stop / **JDWP debug**）。**当前实现**为：

- 路径：`kiwi-admin/backend/deploy/`（`deploy.py` + `conf/build.*.yaml`）
- 能力：**本地 Maven 构建 + SSH/scp 上传**（应用 jar、lib jar、Spring `config/`、shell 脚本）
- **不包含**：脚本内远端启停、JDWP、端口转发说明

远端启动由操作员 SSH 登录后执行已上传的 `backend/bin/restart.sh`（脚本本身无 JDWP 参数）。

## Spec

归档前已修订 delta spec，去掉 JDWP/脚本启停要求，与「仅 deploy」对齐。
