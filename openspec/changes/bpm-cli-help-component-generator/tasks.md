## 1. 服务与解析

- [x] 1.1 新增 `CliHelpParser`：解析 help 文本、生成 `BpmComponent` 与 `command` 模板
- [x] 1.2 `BpmComponentService` 增加 `resolveShellParentComponentId()`

## 2. API

- [x] 2.1 `BpmComponentCtl` 增加 `POST bpm/component/from-cli-help` 与请求 DTO
- [x] 2.2 参数校验与 400 响应

## 3. 验证

- [ ] 3.1 在具备 Maven 的环境中执行 `mvn -f kiwi-admin/backend/pom.xml test`（或至少 `compile`）确认编译通过
