## Context

`BpmComponent` 支持 `parentId` 与 `BpmComponentService#fillComponentProperties` 的**按 key 合并**：子组件声明的输入/输出参数与同名父参数合并时，**子覆盖父**。内置「命令行」组件由 `ShellActivityBehavior`（Spring bean `shell`）经 `ClasspathBpmComponentProvider` 部署，数据库 id 一般为 `source_key` 即 `classpath_shell`。

## Goals / Non-Goals

**Goals:**

- 将常见 `--help` 文本中的选项行抽取为可编辑的 `BpmComponentParameter` 列表。
- 生成隐藏的 `command`，在合并后替换用户可见的父级 `command`，使最终执行命令由「可执行前缀 + 各选项拼装」表达。
- 避免子组件参数 key 与 shell 保留字段冲突（`command`、`directory`、`waitFlag`、`redirectError`、`cleanEnv`）。

**Non-goals:**

- 不保证解析所有非 GNU 版式或严重换行损坏的 help（仅类 GNU「选项列与描述列之间至少两个空格」等启发式）。
- 不在此变更中修改 `ShellActivityBehavior` 对 `command` 的分词或 JUEL 求值语义。
- 不强制前端实现「一键从 help 生成」；规格以 REST 与元数据结构为准。

## Decisions

1. **接口**：`POST /bpm/component/from-cli-help`，请求体仅包含必填 `helpCommand`（服务端执行以捕获 help）；`name`、`key`、`group`、`description` 与 command 字面量前缀由后端从该命令推导；返回未保存的 `BpmComponent`。
2. **父组件 id**：通过缓存中 `key == "shell"` 解析；若不存在则回退字符串 `classpath_shell`（与自动部署命名规则一致）。
3. **选项 key 前缀**：`cli_` + 长选项名（短选项且无长名时用 `opt_<短符>`），与保留 key 冲突时后缀 `_2`、`_3`… 去重。
4. **command 模板**：字面量 `executable` + 各选项段；有值选项用 `--flag ${cli_x}` 或 `--flag=${cli_x}`；布尔选项用 JUEL 三元片段 `${cli_x ? ' --flag' : ''}`（具体标志文本取自解析到的选项片段）。
5. **校验**：`helpCommand` 为空时返回 HTTP 400；执行失败/超时/无输出返回 HTTP 502。

## Risks / Trade-offs

- **[Trade-off]** help 版式多样 → 解析漏项或误判「是否带参」；需在规格中声明启发式边界。
- **[Risk]** JUEL 与前端默认值类型（如 CheckBox 字符串 `"true"`）在表达式中的真值行为 → 由流程建模者按需调整 `command` 表达式；规格不强制引擎级修正。

## Migration Plan

- 无数据迁移；新接口与生成逻辑为增量能力。

## Open Questions

- 是否在后续迭代增加「从本机执行 `cmd --help` 抓取」的服务器端能力（安全与部署环境相关），本规格不包含。
