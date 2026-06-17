## ADDED Requirements

### Requirement: Operaton 2.x 作为嵌入式 BPM 引擎

Kiwi 管理后端 SHALL 使用 **Operaton 2.x** 作为嵌入式 BPM/CMMN/DMN 引擎，替代原 `org.camunda.bpm` 依赖链。构建产物 MUST NOT 再依赖 Camunda 官方 CE 的 `camunda-bpm-spring-boot-starter*`（含 `-4` 变体）作为运行时引擎。

#### Scenario: Maven 坐标已切换至 Operaton 2.x

- **WHEN** 维护者检查根 POM 与 `kiwi-admin/backend`、`kiwi-bpmn-external-task` 的 BPM 引擎相关依赖
- **THEN** 引擎工件 MUST 使用 `org.operaton` 组 ID、Operaton **2.x** BOM 管理版本，且 MUST NOT 保留任何 `org.camunda.bpm` 运行时依赖

### Requirement: 与 Camunda 7.24 的流程兼容边界

所选 Operaton 2.x 版本 MUST 保持与 **Camunda 7.24** 流程定义及数据库 schema 的升级兼容路径。已存在的 BPMN 2.0 文件（含 `xmlns:camunda="http://camunda.org/schema/1.0/bpmn"`）SHALL 无需修改 namespace 即可部署与执行。

#### Scenario: 既有 BPMN 直接部署

- **WHEN** 运维在迁移后将迁移前已保存的 BPMN XML 部署到 Operaton 2.x
- **THEN** 部署 MUST 成功，且 Service Task / External Task 行为与迁移前 Camunda 7.24 等价（在无 Script Task ES5 依赖的前提下）

### Requirement: Spring Boot 配置前缀

引擎相关应用配置 MUST 使用 **`operaton.bpm`** 前缀。`application.yml` 及环境 profile MUST NOT 以 `camunda.bpm` 作为生效配置前缀（注释除外）。

#### Scenario: 主配置使用 operaton 前缀

- **WHEN** 应用以默认 profile 启动并加载 `application.yml`
- **THEN** admin 用户、schema-update、default-serialization-format 等 MUST 绑定到 `operaton.bpm.*` 并被 Operaton Spring Boot starter 识别

### Requirement: Engine REST API 路径

Operaton Spring Boot REST 集成 SHALL 继续对外提供 **`/engine-rest`**。前端 `camundaEngineRestPath`（或等价字段）MUST 默认仍为 `/engine-rest`。

#### Scenario: 设计器可访问引擎 REST

- **WHEN** 已登录用户打开 BPM 流程相关页面且 `api.baseUrl` 指向 Kiwi 后端
- **THEN** 对 `${baseUrl}/engine-rest` 的标准引擎 REST 请求 MUST 返回 HTTP 2xx，与迁移前行为一致

### Requirement: Java API 包名

流程引擎相关 Java 代码 MUST 使用 **`org.operaton.bpm.*`**（及 Operaton REST DTO 包），`src/main/java` 中 MUST NOT 保留 `import org.camunda`。

#### Scenario: 编译无 Camunda 包引用

- **WHEN** 对 `kiwi-admin/backend` 与 `kiwi-bpmn` 执行全量编译
- **THEN** 编译 MUST 成功，且 `import org.camunda` 在 `src/main/java` 中 MUST 为零匹配

### Requirement: 流程变量 JSON 序列化（Spin）

迁移后 MUST 继续支持 `operaton.bpm.default-serialization-format: application/json`（Spin/Jackson），新写入 POJO 变量 MUST NOT 要求 Java 原生序列化。

#### Scenario: 非 Serializable POJO 可写入变量

- **WHEN** 向运行中流程实例写入普通 Java 对象变量，且默认序列化格式为 `application/json`
- **THEN** 写入与后续读取 MUST 成功，语义与迁移前 Camunda Spin 行为一致

### Requirement: Camunda 基线版本控制保留

在 Operaton 迁移实施开始前，仓库 MUST 在 **`master` 当前提交**（不含 Operaton/Boot 4 迁移改动）上创建 **annotated git tag `camunda`**，并存在指向同一提交的 **`camunda` 分支**，用于保留 Camunda 7.24 + Spring Boot 3.5 末态。

#### Scenario: 标签与分支可检出 Camunda 末态

- **WHEN** 维护者执行 `git checkout camunda` 或 `git checkout tags/camunda`
- **THEN** 工作区 MUST 呈现 Camunda 7.24 依赖与 `camunda.bpm` 配置，且 MUST NOT 包含 Operaton 2.x 或 Spring Boot 4 的迁移提交
