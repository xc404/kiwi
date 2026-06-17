# 归档说明

**日期：** 2026-06-17

## 状态

- **Spring Boot 4.0.x** 与 **Spring Framework 7.x** 已在仓库落地（见根 `pom.xml`、`operaton-migration` 归档）。
- BPM 集成路径为 **Operaton 2.x** starter，**非**本 change 初稿的 Camunda `*-4` starter（见 `openspec/specs/java-backend-runtime/spec.md`，由 `operaton-migration` 同步）。
- 目录内 `REVERTED.md` 记录的是中途回退 Boot 3.5 的历史；后续由 `operaton-migration` 重新完成 Boot 4 + Operaton 迁移。

## 归档

`openspec archive --skip-specs --no-validate`：避免将仍写 Camunda `-4` 的 delta spec 覆盖/冲突 main 中的 `java-backend-runtime`。

`cyroems` 已不在本 monorepo；任务 4.3 不适用。
