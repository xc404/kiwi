## ADDED Requirements

### Requirement: Movie Workflow 选择器
cryo-em-server-frontend 创建任务页 SHALL 在 `GlobalSettings` 的 Project Information 区显示一个 **Movie Workflow** Select 输入框，使用与 `Is Tomo` 一致的 `LabeledInputRow + select native` 风格。Select 选项 MUST 来自 `GET /api/bpm/processes?type=movie` 返回的简化 DTO，`label` 显示流程 `name`，`value` 为流程 `id`。该字段在 `status === 'create'` 时 MUST 必填，提交时 MUST 通过 `taskSettings2Params` 写入请求体顶层 `movieProcessDefinitionId`。`status === 'edit'` 时 MUST 显示当前已绑定流程的 `name`（基于回填的 `movieProcessDefinitionId`）但禁用更改。

#### Scenario: 创建模式加载选项
- **WHEN** 用户进入 `/create` 页面
- **THEN** 前端调用 `GET /api/bpm/processes?type=movie`，并把返回的流程列表渲染为 Movie Workflow Select 的下拉选项

#### Scenario: 必填校验
- **WHEN** 用户在创建模式未选择 Movie Workflow 即点击 "Create Task"
- **THEN** `checkValidation` 返回 `false`，按现有失败路径阻断提交，并提示 "请选择 Movie Workflow"

#### Scenario: 提交携带字段
- **WHEN** 用户选择了 `id="MP1"` 的流程并点击 "Create Task"
- **THEN** `POST /api/task` 请求体顶层包含 `movieProcessDefinitionId: "MP1"`

#### Scenario: 编辑模式禁用
- **WHEN** 用户进入 `/edit?taskId=...` 页面，且后端返回的 task 对象 `movieProcessDefinitionId="MP1"`
- **THEN** Movie Workflow Select 显示对应流程名，但 `disabled` 为 `true`，鼠标悬停显示 tooltip "流程已绑定，无法更改"

#### Scenario: 列表为空
- **WHEN** `GET /api/bpm/processes?type=movie` 返回空数组
- **THEN** Select 下拉为空，前端用 toast 提示 "未找到可用的 Movie 流程，请联系管理员配置"

### Requirement: Mdoc Workflow 选择器条件可见
cryo-em-server-frontend SHALL 仅在所选数据集的 `is_tomo === 'true'` 时，于 Movie Workflow Select 之下额外渲染一个 **Mdoc Workflow** Select。该 Select 的数据源 MUST 为 `GET /api/bpm/processes?type=mdoc`；当显示时在创建模式 MUST 必填，编辑模式 MUST 禁用且回显当前 `mdocProcessDefinitionId`；当 `is_tomo === 'false'` 时该 Select MUST 不渲染且 `taskSettings2Params` MUST NOT 在请求体中包含 `mdocProcessDefinitionId` 字段。

#### Scenario: tomo 数据集出现 Mdoc Select
- **WHEN** 用户通过 `DatasetSelector` 选中一个 `is_tomo=true` 的数据集
- **THEN** Mdoc Workflow Select 立即出现在 Movie Workflow Select 下方，并请求 `GET /api/bpm/processes?type=mdoc` 加载选项

#### Scenario: 切换为 non-tomo 数据集
- **WHEN** 用户从 tomo 数据集切换为 non-tomo 数据集
- **THEN** Mdoc Workflow Select 立即消失，先前选中的 mdoc 值在前端状态中清空，下次提交不携带 `mdocProcessDefinitionId`

#### Scenario: tomo 必填
- **WHEN** 数据集 `is_tomo=true`，用户未选择 Mdoc Workflow 即点击 "Create Task"
- **THEN** `checkValidation` 返回 `false` 并提示 "请选择 Mdoc Workflow"

#### Scenario: non-tomo 不必填且不发送
- **WHEN** 数据集 `is_tomo=false`，用户点击 "Create Task"
- **THEN** `checkValidation` 通过，`POST /api/task` 请求体不包含 `mdocProcessDefinitionId` 字段

#### Scenario: 编辑模式 tomo 任务回显
- **WHEN** 用户进入 `/edit` 页面，task 的 `is_tomo=true` 且 `mdocProcessDefinitionId="MD1"`
- **THEN** Mdoc Workflow Select 显示对应流程名并 `disabled`

### Requirement: 数据集切换不重置已选 Workflow
当用户在创建模式切换数据集时，前端 SHALL 不主动重置已经选中的 `movieProcessDefinitionId`/`mdocProcessDefinitionId`；其切换语义仅触发 Mdoc Select 的可见性变更（按上一条要求执行清空）。

#### Scenario: tomo 间切换保留 movie 选择
- **WHEN** 用户已选 Movie Workflow=`MP1`，并把数据集从 tomo 数据集 A 切换到 tomo 数据集 B
- **THEN** Movie Workflow Select 仍保持 `MP1`，Mdoc Workflow Select 保持先前的选中值

#### Scenario: tomo→non-tomo 切换仅清 mdoc
- **WHEN** 用户已选 Movie=`MP1`、Mdoc=`MD1`，并把数据集切换为 non-tomo
- **THEN** Movie Workflow Select 仍保持 `MP1`，Mdoc Workflow Select 不再渲染，前端状态中 `mdocProcessDefinitionId` 被清空
