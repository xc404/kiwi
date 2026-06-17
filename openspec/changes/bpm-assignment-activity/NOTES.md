# 说明：本 change 规格与实现不一致

`AssignmentActivity`（Bean 名 `assignmentActivity`）在设计器中的展示名为 **「变量组件」**，运行时仅 `leave` 流转；变量读写由 BPMN 服务任务的 **输入/输出映射**（及设计器侧配置）完成，**不在** `execute` 内解析 `assignments` JSON。

本 change 初稿误将「赋值」语义写进 Activity 运行时，与现有产品行为不符。实现以代码为准；本 change **不应再按 tasks 中的 JSON 解析需求实施**，待归档或修订 proposal/design/spec 后再关闭。
