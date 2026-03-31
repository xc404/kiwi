## 1. PageConfig 与模板

- [x] 1.1 将 `PageConfig.editModal.colunms` 重命名为 `columns`，并更新 `editModalOptions` 默认值
- [x] 1.2 更新 `crud-page.html` 中对 `editModalOptions()` 的绑定（`columns`）
- [x] 1.3 统一 `PageConfig` 接口成员分隔符与可选属性书写（与设计一致）

## 2. CrudPage 实现细节

- [x] 2.1 简化 `_popupEditModal` 中 `record` 默认值赋值逻辑，行为与现有一致

## 3. 验证

- [x] 3.1 运行前端构建或相关 lint，确认无回归
