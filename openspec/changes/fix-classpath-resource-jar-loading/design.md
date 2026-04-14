## Context

- Spring Boot 3 可执行 JAR 使用嵌套结构（`jar:nested:`），classpath 条目不一定映射到 `java.io.File`。
- `PermissionService` 已改为 `ApplicationContext#getResource` + `getInputStream()`。
- `BpmProcessDefinitionService` 仍用 `ResourceUtils.getFile` 读取可配置的 `xbpm.process-definition.template-path`（默认 `classpath:bpm/bpm-template.xml`），在 JAR 部署时会失败。
- 全仓库 `ResourceUtils` 仅剩上述 BPM 一处（`grep` 已确认）。

## Goals / Non-Goals

**Goals:**

- 凡指向 **classpath**（含 `classpath:` 前缀）的配置路径，加载逻辑必须在 **JAR 内** 可用。
- 保持现有配置属性名、默认值与字符串替换行为不变。
- 若用户将 `template-path` 设为 **`file:`** 等真实文件 URL，仍应可读（Spring `Resource` 统一处理）。

**Non-Goals:**

- 不新增新的配置项或改变 BPM XML 模板格式。
- 不修改其他模块（如 Slurm 等对真实工作目录使用 `File` 的代码）—— 那些针对的是运行时目录而非 classpath。

## Decisions

1. **`BpmProcessDefinitionService` 使用 `ResourceLoader#getResource(String)`**  
   - **理由**：与 `@Value` 中的路径字符串一致；`DefaultResourceLoader` 对 `classpath:`、`file:`、`http:` 等有统一解析；JAR 内返回 `ClassPathResource` 等，可用 `getInputStream()`。
   - **备选**：仅 `new ClassPathResource(stripPrefix)` — 需自行剥离 `classpath:`，且对非 classpath 默认值不友好。

2. **用流读取为 `String`（如 `StreamUtils.copyToString` 或 `FileUtils.copyToString` 对 `InputStream` 的封装）**  
   - **理由**：避免依赖 `File`；与 `PermissionService` 模式一致。
   - **备选**：`Resource` 注入为字段 — 需改配置绑定方式，改动面更大。

3. **审计策略**：以 `ResourceUtils.getFile`、`classpath:` + `File` 组合为搜索信号；当前仅 BPM 一处待改。

## Risks / Trade-offs

- **[Risk] `ResourceLoader` 相对路径解析与 `ResourceUtils` 细微差异** → **缓解**：配置沿用绝对形式的 `classpath:bpm/...`，与 Spring Boot 文档一致。
- **[Risk] 超大模板文件内存占用** → **缓解**：与现状相同（整文件读入 `String`），非本变更引入。

## Migration Plan

- 部署新构建即可；无需数据迁移。
- **回滚**：恢复旧版本 JAR；无 schema 变更。

## Open Questions

- 无。
