# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/dict_datastore_评估_029391f6.plan.md`；**核心改造已实现**后归档，plan 文件已删除。

## 实现状态

- **MVP 已达成**：`shared/datastore/`、`DictStoreService`、`CrudPage.registerStores`、`DictSelectFieldType`、消费侧统一；已移除启动 bulk 与 `HttpDictService`
- **明确不做**：字典管理 CRUD 后 `StoreManager.reload`（提示用户刷新页面）

## 与 plan 的设计差异（功能等价）

| plan | 实现 |
|------|------|
| `StoreManager.getStore()` | `DictStoreService.getStore()` |
| 消费方 `StoreManager.lookup()` | `DictStoreService.getStore({ storeId, autoLoad })` |
| `records` signal | `items` signal + `records()` 方法 |

## 未完成（可选，非阻塞）

- 业务页显式 `PageConfig.stores` 示例
- 删除已 `@Deprecated` 的 `GET /common/dict/groups`

## Main spec

无 delta spec；未同步至 `openspec/specs/`。
