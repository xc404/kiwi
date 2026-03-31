## Context

- 路由：`/default/bpm/project` 为项目 CRUD 列表；`/default/bpm/project/:id` 为该项目下流程列表（`BpmProjectProcess`）。
- 已有 `WindowService` 封装 `localStorage`，与现有「清除缓存」行为一致（全清会一并清空工作区记忆，可接受）。

## Goals / Non-Goals

**Goals:**

- 单一存储键，仅存 **projectId**（字符串，与 Mongo `_id` 一致）。
- 列表页可发现、可进入上次工作区；项目内页进入即更新记忆。

**Non-Goals:**

- 不在后端持久化「用户默认项目」（若需多设备一致，另起变更）。
- 不做全局顶栏工作区切换器（范围过大）。

## Decisions

1. **存储键**：`kiwi.bpm.lastWorkspaceProjectId`，避免与现有 token 键冲突。
2. **写入时机**：`BpmProjectProcess` 在路由 `id` 有效时写入（`effect`/`AfterViewInit` 均可，以路由 id 为准）。
3. **读取与展示**：`BpmProject` 初始化读取；若存在则展示 **进入上次工作区**（`router.navigate` 至 `/default/bpm/project/:id`）。

## Risks / Trade-offs

- **[Risk] 项目已删除** → 跳转后 CRUD 可能空或报错；可后续在跳转前 HEAD/GET 校验（本变更不实现）。
- **[Risk] localStorage 被清除** → 记忆丢失。

## Migration Plan

- 无后端迁移；发版即生效。

## Open Questions

- 是否在登录后默认跳转到上次工作区（当前仅列表页入口，不自动跳转）。
