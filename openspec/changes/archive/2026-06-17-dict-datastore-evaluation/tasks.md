# Tasks

## 1. Data Package 基础设施

- [x] `shared/datastore/` 分层（Model / Proxy / Reader / Store / StoreManager）
- [x] `CrudDataSource extends DataStore`
- [x] `DictSelector` 改为 `DictStoreService` / `StoreManager`

## 2. 页面 Store 声明

- [x] `PageConfig.stores`、`collectDictStoreConfigs`
- [x] `CrudPage.registerStores` + `autoLoad` 粒度

## 3. 消费侧

- [x] Formly `DictSelectFieldType`（`props.storeId`）
- [x] `FieldComp` / `map.pipe` 走 Store
- [x] 移除 `MainComponent` bulk；删除 `HttpDictService`

## 4. 清理

- [x] `groupKey` → `storeId`
- [x] `GET /common/dict/groups` — `@Deprecated`
- [x] Mongo 字典种子（`V20250610_001`–`004`）

## 5. 可选（未做）

- [ ] 业务页显式 `PageConfig.stores` 示例（如 user.component）
- [ ] 确认无外部调用后删除 `GET /common/dict/groups`
