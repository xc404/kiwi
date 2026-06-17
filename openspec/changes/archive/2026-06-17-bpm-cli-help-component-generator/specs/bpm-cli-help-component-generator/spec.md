## ADDED Requirements

### Requirement: 从 CLI help 生成继承 shell 的组件草稿

系统 SHALL 提供 `POST /bpm/component/from-cli-help` 接口，接受 JSON 请求体，其中 **SHALL** 仅包含非空的 `helpCommand`（用于获取 help 的**完整命令行字符串**，由服务端执行）。

服务端 **SHALL** 使用进程 API（如 `ProcessBuilder`）执行该命令：在 Windows 上 **SHALL** 通过 `cmd.exe /c` 执行整行；在其它操作系统上 **SHALL** 通过 `sh -c` 执行整行。**SHALL** 合并 stderr 至 stdout 流；**SHALL** 对执行设置超时；**SHALL** 使用系统默认字符集解码输出以得到 help 文本。

`name`、`key`、`group`、`description`、command 模板中的**可执行前缀**（字面量段）等 **SHALL** 由后端从 `helpCommand` 推导默认值（例如去掉末尾 `--help`/`-h` 等后作为前缀，`name` 为「前缀 + ` CLI`」，`group` 为 `common` 等），**SHALL NOT** 要求客户端传入这些字段。

接口 **SHALL** 返回一个**未持久化**的 `BpmComponent` 实例，其 `parentId` **SHALL** 指向当前环境中 shell「命令行」父组件在持久化层的 id：实现 **SHALL** 优先在已加载的组件缓存中查找 `key` 为 `shell` 的组件并取其 `id`；若未找到 **SHALL** 回退为 `classpath_shell`（与类路径自动部署的默认 id 规则一致）。

生成结果 **SHALL** 将 `type` 设为与继承 shell 相适应的类型（如 `SpringBean`），**SHALL** 将 `outputParameters` 置为 `null` 以便与父组件合并时继承 shell 的输出定义。

当 help 命令执行失败、超时或无可用输出时，接口 **SHOULD** 返回 HTTP 502（或等价错误码）并 **SHALL NOT** 返回成功生成的组件体。

#### Scenario: 缺少必填字段时拒绝请求

- **WHEN** 请求体缺失或 `helpCommand` 为空或仅空白
- **THEN** 接口 **SHALL** 返回 HTTP 400，且 **SHALL NOT** 返回成功生成的组件体

#### Scenario: 成功生成时包含 shell 父引用

- **WHEN** 请求合法且系统中存在已部署的 shell 组件
- **THEN** 返回体的 `parentId` **SHALL** 等于该 shell 组件的 `id`（通常为 `classpath_shell`）

---

### Requirement: Help 选项解析与 cli_* 输入参数

系统 SHALL 从执行 `helpCommand` 得到的 help 文本中按启发式抽取**选项行**：行首（忽略前导空白）以 `-` 开头，且选项列与描述列之间 **SHALL** 存在至少两个连续空格；**SHALL** 跳过明显非选项行（如 `Usage:` 行、单独一行的 `Options:` 等标题行）。

对每个解析到的选项，系统 **SHALL** 生成一条 `BpmComponentParameter`，其 `key` **SHALL** 以 `cli_` 为前缀并基于长选项名（无长选项时基于短选项派生），且 **SHALL NOT** 与 shell 保留输入名冲突：`command`、`directory`、`waitFlag`、`redirectError`、`cleanEnv`。若冲突 **SHALL** 通过追加数字后缀等方式保证唯一。

系统 **SHALL** 根据选项片段判定「是否期望取值」：例如包含 `=`、尖括号占位，或行尾为大写占位词（如 `FILE`）时视为带参选项（`htmlType` 宜为 `#text`）；否则视为布尔型开关（`htmlType` 宜为 `CheckBox`，并 **SHALL** 提供合适的默认布尔字符串如 `false`）。解析到的选项 **SHALL** 归入同一分组（如 `CLI`），且 **SHALL** 在合并展示时作为重要参数（`important` 为真），除非实现文档另有说明。

#### Scenario: 重复长选项只保留一条

- **WHEN** help 中同一长选项出现多次
- **THEN** 实现 **SHALL** 仅保留首次解析结果（或等价去重策略），避免重复 `cli_*` key

---

### Requirement: 隐藏 command 覆盖父组件

生成结果 **SHALL** 在输入参数列表中包含一条 `key` 为 `command` 的 `BpmComponentParameter`，且 **SHALL** 设置 `hidden` 为真，以在合并父组件参数后**覆盖** shell 自带的可见 `command` 定义。

该参数的 `defaultValue` **SHALL** 为一段字符串，其内容由字面量 `executable` 与按解析选项拼装的片段组成：带参选项 **SHALL** 包含标志与 `${cli_*}` 形式的变量引用；布尔选项 **SHALL** 使用 JUEL 风格三元片段，在选中时拼接对应标志文本。实现 **SHALL** 在说明字段中声明该字符串供 Camunda 输入映射中作为表达式使用的意图。

#### Scenario: 与 BpmComponentService 合并行为一致

- **WHEN** 对生成草稿调用与列表接口相同的「填充/合并父级属性」逻辑
- **THEN** 合并后的输入参数中 **SHALL** 以子组件提供的 `command` 为准（同名覆盖父级 `command`），且用户侧对 CLI 选项的编辑项 **SHALL** 出现在合并后的参数列表中
