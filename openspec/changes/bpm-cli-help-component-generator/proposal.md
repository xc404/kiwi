## Why

将任意命令行工具的 `--help` 输出手工整理成继承「命令行」(shell) 的 `BpmComponent` 元数据（逐项输入参数 + 覆盖父级 `command`）成本高、易错。需要在后端提供**可重复的生成规则**，并用规格固化行为，便于评审与回归。

## What Changes

- 新增 HTTP 接口：请求体仅含要在服务端执行的 `helpCommand`（如 `docker --help`），由进程捕获输出后生成**未持久化**的 `BpmComponent` 草稿；其余字段由后端默认推导。
- 子组件 `parentId` 指向 shell 父组件在库中的实际 id（通过 `key == "shell"` 解析，通常为 `classpath_shell`）。
- 解析 help 中类 GNU 版式的选项行，为每个选项生成 `cli_*` 输入参数；追加**隐藏**的 `command` 参数以合并策略覆盖父组件的 `command`。
- 在 `BpmComponentService` 中提供 `resolveShellParentComponentId()` 供上述解析使用。

## Capabilities

### New Capabilities

- `bpm-cli-help-component-generator`：定义从 CLI help 生成继承 shell 的 BPM 组件元数据及对应 API 行为。

### Modified Capabilities

- （无。）

## Impact

- **后端**：`BpmComponentCtl`、`BpmComponentService`、`CliHelpParser`（新建）。
- **前端**：无必选改动；可选用生成接口辅助录入组件。
- **运行时**：生成结果中的 `command` 默认值为 JUEL 模板字符串，实际执行行为仍取决于流程中 Camunda 输入映射与 shell 对 `command` 的解析方式。
