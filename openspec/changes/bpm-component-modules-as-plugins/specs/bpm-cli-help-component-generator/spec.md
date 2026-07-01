## MODIFIED Requirements

### Requirement: 从 CLI help 生成继承 shell 的组件草稿

系统 SHALL 提供 `POST /bpm/component/from-cli-help` 接口，接受 JSON 请求体，其中 **SHALL** 仅包含非空的 `helpCommand`（用于获取 help 的**完整命令行字符串**，由服务端执行）。

服务端 **SHALL** 使用进程 API（如 `ProcessBuilder`）执行该命令：在 Windows 上 **SHALL** 通过 `cmd.exe /c` 执行整行；在其它操作系统上 **SHALL** 通过 `sh -c` 执行整行。**SHALL** 合并 stderr 至 stdout 流；**SHALL** 对执行设置超时；**SHALL** 使用系统默认字符集解码输出以得到 help 文本。

`name`、`key`、`group`、`description`、command 模板中的**可执行前缀**（字面量段）等 **SHALL** 由后端从 `helpCommand` 推导默认值（例如去掉末尾 `--help`/`-h` 等后作为前缀，`name` 为「前缀 + ` CLI`」，`group` 为 `common` 等），**SHALL NOT** 要求客户端传入这些字段。

接口 **SHALL** 返回一个**未持久化**的 `BpmComponent` 实例，其 `parentId` **SHALL** 指向当前环境中 shell「命令行」父组件在持久化层的 id：实现 **SHALL** 优先在已加载的组件缓存中查找 `key` 为 `shell` 的组件并取其 `id`；若未找到 **SHALL** 回退为 `plugin_shell`（与插件自动部署的默认 id 规则一致）。

生成结果 **SHALL** 将 `type` 设为与继承 shell 相适应的类型（如 `SpringBean`），**SHALL** 将 `outputParameters` 置为 `null` 以便与父组件合并时继承 shell 的输出定义。

当 help 命令执行失败、超时或无可用输出时，接口 **SHOULD** 返回 HTTP 502（或等价错误码）并 **SHALL NOT** 返回成功生成的组件体。

#### Scenario: 缺少必填字段时拒绝请求

- **WHEN** 请求体缺失或 `helpCommand` 为空或仅空白
- **THEN** 接口 **SHALL** 返回 HTTP 400，且 **SHALL NOT** 返回成功生成的组件体

#### Scenario: 成功生成时包含 shell 父引用

- **WHEN** 请求合法且系统中存在已部署的 shell 组件
- **THEN** 返回体的 `parentId` **SHALL** 等于该 shell 组件的 `id`（通常为 `plugin_shell`）
