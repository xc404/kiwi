# Implementation notes

## 2026-08-03 首轮落地

- 配置：`kiwi.ai.workflow-authoring.*`（默认 `enabled=false`）
- Catalog / 抽词 / Validator / PlanGenerate（LLM 可选，失败回退最小 BPMN）
- 内部流程：`classpath:bpm/ai/kiwi_ai_workflow_authoring.bpmn`，启用时 ApplicationReady 部署
- 桥接 API：`/ai/workflow-authoring/**`
- 助手分流：设计器会话（开关开启 + 有 processId）→ 启动编排；不再做场景意图正则过滤
- 人机：User Task（Preview / Install / Ask）；完成走 `completeTask`
- 前端：`previewOnly` 走 `importBpmnXml` 不自动保存；`AiWorkflowAuthoringService` 已加

## 2026-08-04 前端阶段面板

- `bpm-ai-chat` 接入 `AiWorkflowAuthoringService`：按目标流程 `statusByTarget`，聊天回合后刷新
- 右上角「AI 写工作流」面板：阶段标签 + 预览确认/拒绝、安装确认/拒绝、追问提交
- `await_preview` 时自动 `importBpmnXml`（不落库）；确认保存后后端 SaveDelegate 落库，前端再同步画布
- `ChatComponent` 增加 `turnCompleted` 输出

## 2026-08-04 去掉意图正则

- `AiAssistantService` 不再用 `SCENARIO_AUTHORING_INTENT`；启用后设计器内每轮用户消息（有 processId）都走编排
- 同一 `targetProcessId`：`start` 前取消旧活跃实例；`by-target` 取最新活跃实例（不再 `singleResult`）
- 卡在 `extract`：BPMN 内容变更时强制重部署；`start` 后同步执行 pending Job；未进人机阶段则抛错；不再预置 stage
- 聊天回复改为大模型 `summary`（流程变量 `assistantReply`），不再回阶段/实例 id
- Generate 支持在现有 BPMN 上修改（start 传入 baseBpmnXml）；每轮回传 `bpmnXml` action 更新画布
- 自动保存由前端 `aiAuthoringAutoSave` 控制（system 上下文传给后端）；后端不再有 auto-save-canvas 配置

## 与 design 差异

- Catalog installable 主要来自远程市场 plugin 列表（启用时）

## 2026-08-10 质量闭环迭代

- 新增 `authoring-rules.json`：Soft Rule 注入 create/modify prompt；Hard Rule 进入 Validator，Issue 携带 `ruleId`
- `userAnswer` 与结构化校验问题会进入 Generate/Repair；必填参数改为按组件节点检查
- Catalog 注入组件说明、delegate、参数 schema/示例，并为 Top-1 模板携带有长度上限的参考 BPMN
- create 模式优先用窄 Plan IR 经服务端确定性编译 BPMN（含组件绑定、参数、连线与 BPMNDI）；modify 保留原图 XML 路径
- 默认关闭前端 auto-save，仍需预览确认
- 插件候选按市场 `componentKeys` 展开，携带 slug/version/sourceId；用户确认后由 `AiAuthoringInstallDelegate` 真正安装，再回 Validate
- 增加规则、Catalog、Plan 编译、LLM 反馈、插件安装、内部流程定义及黄金场景回归测试
- 自测：AI authoring 专项 19 项通过（含两条跨组件闭环）；前端 development build 通过
- 已知仓库门禁：全量 Maven 在 Windows 被既有 `SlurmServicePathPolicyTest` 路径断言阻塞；production 前端 build 被既有 `login.component.less` budget 阻塞
