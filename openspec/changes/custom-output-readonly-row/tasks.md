## 1. 类型与通用只读行组件骨架



- [ ] 1.1 在 **property-panel 公共区** 实现只读行；输入类型为 **`PropertyDescription`**（`types.ts` 补充可选 `valueText` 若需），另建 `BpmnRuntimeVariable` 等辅助类型；**禁止**单独 `ReadonlyPropertyDescription`

- [ ] 1.2 新增 Standalone 组件 **`bpm-readonly-property-row`**（`readonly-property-row.component.ts` / `.html` / `.css`），声明 `propertyDescription`、`variables` 输入；引入 ng-zorro 占位模块



## 2. 展示与交互逻辑



- [ ] 2.1 将只读主行文案与「配置值 / 运行时值」解析集中在该通用组件内（自定义输出接入时与现有 `readOutputValue` 语义对齐），按 **`PropertyDescription.key`** 匹配 `variables`，缺省占位一致

- [ ] 2.2 实现行尾提示 + 点击浮层（如 `nz-popover`），展示名称、设计时配置值、运行时实际值；模板与文案避免写死「输出」专有术语



## 3. 接入自定义输出面板



- [ ] 3.1 修改 `custom-outputs-panel.html`：只读分支用 `@for` 渲染 **`bpm-readonly-property-row`** 并传入每行描述与 `variables()`；保留空状态「—」

- [ ] 3.2 更新 `custom-outputs-panel.ts`：从新路径 `import` 注册组件；精简 `readOutputValue` 等与通用组件重复的逻辑

- [ ] 3.3 调整样式：`custom-outputs-panel.css` 与只读行样式协调，只读/编辑两路径无回归



## 4. 验证



- [ ] 4.1 只读自定义输出场景符合 `specs/bpm-readonly-property-row/spec.md`；并确认组件路径可被其他属性面板按需复用


