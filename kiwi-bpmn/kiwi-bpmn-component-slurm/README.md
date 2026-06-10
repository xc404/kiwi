# kiwi-bpmn-component-slurm

独立可选模块：Slurm External Task 组件（`@ExternalTaskSubscription(topicName = "slurm")`），含 sbatch 提交、sacct/Mongo 作业跟踪与工作目录清理。

## 接入

在 `kiwi-admin/backend/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.kiwi</groupId>
    <artifactId>kiwi-bpmn-component-slurm</artifactId>
</dependency>
```

依赖 `kiwi-bpmn-component`（Shell 回退执行）与 MongoDB（`slurm_job` 集合跟踪）。无 Slurm 集群时可设 `kiwi.bpm.slurm.enabled=false`。

## 配置

见 `application.yml` 中 `kiwi.bpm.slurm.*`；工作目录清理说明见 [docs/slurm-workdir-cleanup.md](docs/slurm-workdir-cleanup.md)。

## 本地验证

```bash
mvn -pl kiwi-bpmn/kiwi-bpmn-component-slurm test
mvn -pl kiwi-admin/backend -am compile -DskipTests
```
