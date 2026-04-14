## Why

Spring Boot 可执行 JAR（含 `jar:nested:`）中的 classpath 资源没有真实文件系统路径。使用 `ResourceUtils.getFile` 或等价方式按「磁盘文件」解析 classpath 会在生产部署时抛出 `FileNotFoundException`。`PermissionService` 已改为通过 `Resource.getInputStream()` 读取；代码库中仍存在同类用法，需要在打包运行场景下一并消除。

## What Changes

- 审计 `kiwi-admin`（及相关模块）Java 代码中对 **classpath / `classpath:`** 资源的加载方式。
- 将所有 **`ResourceUtils.getFile` + `File` 读取**（或等价「必须落在文件系统上」的写法）改为 **`Resource` / `InputStream` / `ResourceLoader`** 等可在 JAR 内工作的 API。
- 已确认待修：`BpmProcessDefinitionService` 对 `xbpm.process-definition.template-path`（默认 `classpath:bpm/bpm-template.xml`）使用 `ResourceUtils.getFile`。
- 不引入 **BREAKING** 行为变更：对外配置键与默认路径保持不变，仅修正加载实现。

## Capabilities

### New Capabilities

- `kiwi-admin-classpath-resources`: 约定在 fat JAR / nested JAR 下，内置配置与模板类资源必须通过可移植方式读取（流式或 Spring `Resource`），不得假设存在 OS 文件路径。

### Modified Capabilities

- （无）—— 不改变 `openspec/specs/slurm-workdir-cleanup` 等行为规格；本变更为运行时健壮性约束。

## Impact

- **代码**：`kiwi-admin/backend` 中 BPM 流程定义模板加载等；若审计发现其他模块同类模式一并修复。
- **部署**：修复后 `/opt/.../*.jar` 内嵌资源可正常加载，与 IDE / 解压目录运行行为一致。
- **依赖**：无新版本依赖要求；沿用 Spring `Resource` / `ResourceLoader` / `ApplicationContext#getResource`。
