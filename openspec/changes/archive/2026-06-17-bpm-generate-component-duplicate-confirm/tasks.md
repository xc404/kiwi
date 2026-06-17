## 1. 模型与生成器：sourceKey

- [x] 1.1 在 `BpmComponent` 增加字段 `sourceKey`（及必要的序列化/前端类型）
- [x] 1.2 `CliHelpParser`（或等价路径）：为 CLI 生成的每条组件写入稳定、可复现的 `sourceKey`
- [x] 1.3 `OpenApiComponentGenerator`（或等价路径）：为 OpenAPI 生成的每条组件写入稳定、可复现的 `sourceKey`

## 2. 后端：按 parentId + sourceKey 冲突查询与预检 API

- [x] 2.1 在 `BpmComponentDao`/`BpmComponentService` 增加按 `parentId` + `sourceKey` 查找已有组件的方法（明确 null/空串匹配规则；**不使用 `key` 判重**）
- [x] 2.2 在 `BpmComponentCtl` 增加批量预检接口（如 `POST /bpm/component/preview-conflicts`），入参为待保存草稿列表，出参标明每条是否冲突及已有记录的 `id`
- [x] 2.3 为预检接口补充 OpenAPI 注解与校验；实现「新增」分支用的 **唯一 `sourceKey` 生成** 辅助逻辑（不改动业务 `key` 的语义）

## 3. 前端：共用保存管线与冲突 UI

- [x] 3.1 抽取方法：输入 `BpmComponent[]` → 调用预检 → 返回需用户确认的冲突列表模型（展示 **`sourceKey`**，不以 `key` 作为判重依据）
- [x] 3.2 实现冲突确认 UI（模态或步骤）：每条展示摘要（名称、`sourceKey`、父级等），提供 **取消 / 覆盖 / 新增** 选项
- [x] 3.3 根据用户选择执行 `POST`（含新增时调整后的 `sourceKey`）或 `PUT`（覆盖），处理部分失败与提示文案

## 4. 接入两条生成入口

- [x] 4.1 修改 `confirmGenerateFromCli`：生成成功后改为走共用管线（单元素数组）
- [x] 4.2 修改 `confirmGenerateFromOpenApi`：生成成功后改为走共用管线（批量）
- [x] 4.3 回归：无冲突、全冲突、混合冲突及用户全选取消等路径的手动验证
