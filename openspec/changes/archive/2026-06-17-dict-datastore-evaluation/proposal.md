## Why

字典原先经 `MainComponent` bulk 预载到 `HttpDictService` 内存 Map，启动重、动态字典无法纳入；`DictSelector` 另建 `CrudDataSource` 与全局路径不一致。需要 ExtJS Data Package 风格的按需加载与 `storeId` 全局复用。

## What Changes

- 新建 `shared/datastore/`（Model / Proxy / Reader / DataStore / DictStore / StoreManager）
- `PageConfig.stores` + `collectDictStoreConfigs`；`CrudPage` 按需 `registerStores`
- Formly `dict-select`、`FieldComp`、`MapPipe`、`DictSelector` 统一经 `DictStoreService`
- 移除 bulk 预载；`GET /common/dict/groups` 标记 `@Deprecated`
- `CrudDataSource extends DataStore`

## Capabilities

### New Capabilities

- （无 main spec；前端架构改造。）

### Modified Capabilities

- （无。）

## Impact

- `kiwi-admin/frontend/src/app/shared/datastore/**`
- `crud-page.ts`、`dict-selector.ts`、`field.ts`、`map.pipe.ts`、`dict-select-type.ts`
- 后端字典 API：`GET /common/dict/{storeId}` 不变；bulk groups 废弃

## 非目标

- 字典 CRUD 后自动 `reload()` Store
