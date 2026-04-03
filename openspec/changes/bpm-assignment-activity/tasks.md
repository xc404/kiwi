## 1. 运行时组件

- [x] 1.1 在 `kiwi-bpmn-component` 新增 `AssignmentActivity`：`@Component("assignmentActivity")`，`@ComponentDescription` 声明输入 `assignments`（`#text`，说明 JSON 格式与 `${var}` 约定）
- [x] 1.2 实现 `execute`：解析 JSON 对象，按 design 中规则写入 `ActivityExecution` 变量并 `leave`
- [x] 1.3 为解析与 `${var}` 分支添加单元测试（`spring-boot-starter-test`），覆盖：字面量、`${existing}`、非法 JSON、缺失变量

## 2. 验证

- [ ] 2.1 本地或 CI 执行 `kiwi-bpmn-component` 模块测试通过
- [ ] 2.2 手动：自动部署开启时，设计器中能选到「赋值」组件；部署流程实例，确认变量写入符合预期
