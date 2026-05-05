## 1. DI 与合成器改造

- [x] 1.1 新增用于 `PropertyProvider` 贡献者的 `InjectionToken`（multi），并在 `CompositePropertyProvider` 中注入贡献者数组，用循环替代对 `BasePropertyProvider` / `ComponentPropertyProvider` 的硬编码引用
- [x] 1.2 将现有「按 Tab 名合并 groups」逻辑提取或保持为对贡献者列表依次归并的单一路径，确保空贡献者数组时行为安全（或约束至少存在默认贡献者）

## 2. 默认注册与入口装配

- [x] 2.1 在 BPM 设计器或应用级 `providers` 中，以 `multi: true` + `useExisting`（指向现有 root 单例）注册 `BasePropertyProvider` 与 `ComponentPropertyProvider`，顺序与设计文档一致
- [x] 2.2 若有重复路径（多处 providers），收敛到单一 `provide*` 辅助函数或单一配置入口，避免遗漏或顺序分叉

## 3. 验证

- [ ] 3.1 手动验证：选中流程根、普通节点、`ServiceTask`/`CallActivity`，确认 Tab 与分组与改造前一致（已通过 `ng build --configuration development` 编译验证）
- [ ] 3.2 （可选）新增一个本地桩贡献者类仅在开发环境注册，验证其 Tab 出现在合并结果中且同名 Tab 与默认 Tab 正确合并
