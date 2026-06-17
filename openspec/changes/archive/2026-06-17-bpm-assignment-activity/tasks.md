## 1. 运行时组件

- [x] 1.1 在 `kiwi-bpmn-component` 新增 `AssignmentActivity`：`@Component("assignmentActivity")`，`@ComponentDescription` 展示名为「变量组件」，`inputs`/`outputs` 为空（变量由 BPMN 映射配置）
- [x] 1.2 实现 `execute`：仅 `leave` 流转（详见 [NOTES.md](NOTES.md)，勿在 Activity 内解析 assignments JSON）
- [x] 1.3 （已取消）assignments JSON 运行时解析与单测 — 规格与实现不符，见 NOTES.md

## 2. 验证

- [x] 2.1 本地或 CI 执行 `kiwi-bpmn-component` 模块测试通过
- [x] 2.2 手动：自动部署开启时，设计器中能选到「变量组件」；部署流程实例，确认变量映射符合预期
