## 1. 审计与确认

- [x] 1.1 在仓库内搜索 `ResourceUtils.getFile`、`classpath:` 与 `File`/`getFile` 组合；确认除 `BpmProcessDefinitionService` 外无遗漏（`PermissionService` 应已使用 `Resource#getInputStream`）。

## 2. 实现修复

- [x] 2.1 在 `BpmProcessDefinitionService` 中注入 `ResourceLoader`（或等价的 `ApplicationContext`/`Resource` 获取方式），用 `getResource(processDefinitionTemplatePath).getInputStream()` 将模板读为 `String`（UTF-8），移除对 `ResourceUtils.getFile` 的依赖。
- [ ] 2.2 运行 `mvn -q compile -DskipTests`（`kiwi-admin/backend` 或根模块按项目惯例）确认编译通过。

## 3. 验证

- [ ] 3.1 （可选）本地打包 JAR 后启动或最小化集成验证，确认 `classpath:bpm/bpm-template.xml` 与权限 JSON 加载无 `FileNotFoundException`。
