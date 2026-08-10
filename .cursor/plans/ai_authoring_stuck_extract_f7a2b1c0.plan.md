# AI authoring 卡在 extract

## 现象

聊天返回「阶段: extract」且无待办任务，说明 `execute()` 后未到达 User Task。

## 可能原因

1. 初始变量预置 `stage=extract`，掩盖「抽词尚未执行」
2. 引擎内 BPMN 未随 classpath 更新（deployer 见已有定义就跳过）
3. 存在未执行的 async Job / Incident

## 修复

1. `start` 不预置 stage；执行后同步 drain jobs；有 Incident 则抛错
2. Deployer 按 BPMN 内容变更强制重新部署
3. Extract 避免重复 LLM 调用
