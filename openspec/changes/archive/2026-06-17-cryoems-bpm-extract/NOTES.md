# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/cryoems-bpm_迁出_66ed0076.plan.md`；plan 文件已删除。

## 实现状态（部分完成）

| 项 | 状态 |
|----|------|
| `cryoems-bpm` 物理迁出 kiwi 仓库 | ✅ 已迁至 `d:\Projects\cryoems-bpm`（独立目录） |
| 独立根 `pom.xml`（`${kiwi.version}` 依赖） | ✅ 已有；parent 为 `spring-boot-starter-parent:3.5.8` |
| kiwi 移除 `<module>cryoems-bpm</module>` | ✅ kiwi reactor 已无该模块 |
| `cryoems-bpm.version` 属性 | ✅ `pom.xml` 保留 `1.0.0-SNAPSHOT` |
| `cryoems-bpm-local` profile / GitHub Packages 仓库 | ❌ kiwi `pom.xml` 未配置 |
| `kiwi-admin` 依赖 `cryoems-bpm` | ❌ 当前 `backend/pom.xml` 无该依赖（可能已改为外部装配或暂移除） |
| GitHub Packages `distributionManagement` + CI deploy | ❌ `cryoems-bpm/pom.xml` 未配置 |
| 全量验证（Packages 拉取 + local profile） | ❌ 未完成 |

若需收尾 GitHub Packages 与 `cryoems-bpm-local` 联调，应新开 change 按现行 kiwi Spring Boot 4 / Operaton 版本对齐后再实施。

## Main spec

无 delta spec；未同步至 `openspec/specs/`。
