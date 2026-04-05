## 1. 解析与生成

- [x] 1.1 新增 `OpenApiComponentGenerator`：解析 OpenAPI/Swagger，为每个操作生成 `BpmComponent` 与输入参数默认值
- [x] 1.2 `BpmComponentService` 增加 `resolveHttpRequestParentComponentId()`
- [x] 1.3 引入 `swagger-parser` 依赖

## 2. API

- [x] 2.1 `BpmComponentCtl` 增加 `POST bpm/component/from-openapi` 与请求 DTO
- [x] 2.2 参数校验与 400 响应

## 3. 验证

- [ ] 3.1 在具备 Maven 的环境中执行 `mvn -pl kiwi-admin/backend test`（或至少 `compile`）确认编译通过
