## 1. 数据与模板

- [x] 1.1 核对 `GET /bpm/process/{id}` 返回 JSON 字段名（`updatedTime` / `deployedAt` 等），在 `bpm-editor` 中统一读取并在必要时增加 `computed` 映射
- [x] 1.2 在 `bpm-editor.html` 顶部增加流程信息展示结构（名称、修改时间、部署时间；可选：ID、版本、项目 ID）
- [x] 1.3 绑定 `bpmProcess()` 与加载态；保存/部署成功后依赖现有 `bpmProcess` 更新即可刷新展示

## 2. 样式与体验

- [x] 2.1 在 `bpm-editor.scss` 中为新顶栏添加布局与样式，与工具栏、画布高度协调，避免挤压 BPMN 画布
- [x] 2.2 窄屏或长名称时处理省略或换行（`min-width: 0`、ellipsis 等）

## 3. 验证

- [x] 3.1 手动打开设计页：加载完成后信息正确；未部署流程部署时间展示符合设计
- [x] 3.2 保存后修改时间更新；部署后部署时间与版本相关展示更新
