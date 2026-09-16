# 未使用文件/目录清点（kiwi）

扫描范围：`kiwi` 仓库（Java 模块 + Angular 前端 + 顶层目录）。

**状态：已按本清单全部清理（2026-09-16）。**

判定约定：

- **高置信**：无引用，或文档已明确废弃；Spring/BPM 动态加载已排除。
- **中置信**：无引用，但可能是预留能力、模板库或半成品（本次一并删除）。
- **不删**：`@Component` / `@Controller` / `@ChangeUnit` / `@ComponentDescription`、路由懒加载、Formly `htmlType`、bpmn-js additionalModules。

---

## 1. 高置信：磁盘残留（多数不在 Git）

这些路径当前磁盘上存在，但 **`git ls-files` 无记录**（或已被 `.gitignore` 忽略）。删了不影响仓库，只清本地。

| 路径 | 原因 |
|------|------|
| `kiwi-bpmn-assistant/` | 已迁出实验模块。无 `pom.xml`、无 Java 源码，只剩空 `src/main/resources/bpm/ai` 和 `target/` |
| `kiwi-bpmn-designer-agent/` | 源码已并入 `kiwi-admin`。目录只剩 `target/`（含 2026-09 的 surefire） |
| `plugins/`（仓库根） | 空目录。真正插件目录是 `kiwi-admin/backend/plugins/` |
| `data/dev-bpm.mv.db` | H2 本地库；`.gitignore` 已忽略 `/data/` |
| `docker/plugins/*.jar` | 构建产物；`.gitignore` 已忽略，只保留 `.gitkeep` |
| `kiwi-admin/backend/bin/config/` | 部署脚本会同步产物；Git 只跟踪 `restart.sh` / `stop.sh` |

空目录（排除 `target/`、`node_modules`）：

- `plugins/`
- `kiwi-bpmn-assistant/src/main/resources/bpm/ai`

---

## 2. 高置信：Git 跟踪、可删

### 过期插件 JAR

- `kiwi-admin/backend/plugins/kiwi-bpmn-component-1.0.0-SNAPSHOT-plugin.jar`

官方核心组件已改为 Maven 依赖（`ClasspathBpmComponentProvider` / `classpath_*`）。`plugins/README.md` 明确写「不在此目录」，表里也不再列出该 JAR。启动时若仍加载，会与 classpath 组件重复。

**保留**：kafka / rabbitmq / s3 / slack / payment / example 的 `*-plugin.jar`。

### Java（无 Spring 注解、无其它引用）

| 文件 | 原因 |
|------|------|
| `kiwi-admin/backend/src/main/java/com/kiwi/framework/web/ctl/BaseController.java` | RuoYi 遗留；全部 Controller 继承 `BaseCtl` |
| `kiwi-admin/backend/src/main/java/com/kiwi/project/system/service/SysConfigService.java` | 空类，无注入 |
| `kiwi-admin/backend/src/main/java/com/kiwi/project/system/entity/SysConfig.java` | 无 Dao/Ctl/前端；`@Document("sys_config")` 无仓库使用 |
| `kiwi-admin/backend/src/main/java/com/kiwi/project/system/entity/SysNotice.java` | 无 Dao/Ctl/前端 |
| `kiwi-admin/backend/src/main/java/com/kiwi/framework/permission/PermissionDictsProvider.java` | 实现 `DictsProvider` 但无 `@Component`/`@Bean`，`DictService` 注入不到 |
| `kiwi-admin/backend/src/main/java/com/kiwi/framework/web/query/RequestTotalPageRequest.java` | 仅在 `BaseMongoRepositoryImpl` 注释中出现 |
| `kiwi-common/src/main/java/com/kiwi/common/mongo/MongoEntity.java` | 实体一律 `BaseEntity`，无 `extends MongoEntity` |
| `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/activity/Assignment.java` | `AssignmentActivity` 不使用该类 |
| `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/utils/ExternalTaskUtils.java` | 无引用（Slurm 走自己的 API） |
| `kiwi-admin/backend/src/main/java/com/kiwi/project/tools/codegen/entity/GenConstants.java` | 与 `codegen/utils/GenConstants.java` 重复；`GenUtils` 用的是同包 `utils` 版 |
| `kiwi-admin/backend/src/main/java/com/kiwi/common/excel/Excels.java` | `@Excels` 从未使用（`@Excel` 仍在用，保留） |

### 代码生成 Velocity 模板（`getTemplateList` 未引用）

现行模板只覆盖 mongo/mybatis + Angular + menu/permissions/readme。以下为若依 Vue/JS 时代残留：

- `vm/vue/`（含 `v3/`）整目录
- `vm/js/api.js.vm`
- `vm/sql/sql.vm`
- `vm/xml/mapper.xml.vm`（非 `xml/mybatis/mapper.xml.vm`）
- `vm/java/controller.java.vm`
- `vm/java/domain.java.vm`
- `vm/java/mapper.java.vm`
- `vm/java/service.java.vm`
- `vm/java/serviceImpl.java.vm`
- `vm/java/sub-domain.java.vm`

### 前端

| 文件 | 原因 |
|------|------|
| `src/app/pages/bpm/design/context-pad/kiwi-append-component-module.ts` | 旧副本；编辑器只 import `append-component-module` |
| `src/app/pages/bpm/flow-elements/bpm-component-edit-panel.ts` | 占位组件，无 import、无路由 |
| `src/app/pages/bpm/design/assistant/bpm-designer-assistant.handlers.ts` | `createBpmDesignerAssistantHandlers` 未被调用 |
| `src/app/pages/bpm/design/extension/flowable/flowable-element-model.ts` | `app.config.ts` 只用 Camunda |
| `src/app/pages/bpm/design/extension/flowable/flowable.json` | 仅被上一文件引用 |
| `src/app/core/services/http/example/example.service.ts` | 演示 `sessionTimeOut`，无引用 |
| `src/app/core/services/common/guard/judgeAuth.guard.ts` | `layout-routing` 里 `canActivateChild` 已注释 |
| `src/app/core/services/store/biz-store-service/search-list/search-list-store.service.ts` | 无引用 |
| `src/app/shared/drawers/ex-drawer-drawer/` | 组件+service 仅互相引用 |
| `public/data/flare.json` | ng-alain 示例数据，源码无引用 |

---

## 3. 中置信：建议确认后再删

### 锁屏半成品

- `src/app/shared/components/empty-for-lock/`：`lock-widget` 会 `navigateByUrl('/blank/empty-for-lock')`，但 **没有任何路由注册该 path**，组件实际加载不到。
- 若保留锁屏，应补路由；若砍锁屏，可连 `empty-for-lock` 一起删（`lock-screen` / `lock-widget` 仍在用）。

### Datastore 未消费实现

- `shared/datastore/array-store.ts`
- `shared/datastore/proxy/memory-proxy.ts`

只从 `index.ts` 再导出，业务代码未 import。`getMenuArrayStore()` 是 MenuStore 方法名，与 `ArrayStore` 类无关。

### MSW

- `public/mockServiceWorker.js` + `package.json` 的 `msw` devDependency
- `src/` 下无 handler、无 `setupWorker`

### README 未引用的截图

`docs/screenshots/` 中 README 用了 gif/login 和 4 张「屏幕截图 2026-04-20 …」。未出现在 README 的：

- `屏幕截图 2026-04-20 110358.png`
- `…110457.png` / `…110505.png` / `…110545.png`
- `屏幕截图 2026-07-06 111821.png`

### `.gitignore` 过期条目（不是文件，是噪声）

- `ui/`、`nest-api/`：目录已不存在
- `kiwi-admin/script/`：目录已不存在

### POM 死条目

- 根 `pom.xml` 的 `cryoems-bpm` `dependencyManagement`：模块已迁出，backend 未声明依赖（见 archived OpenSpec NOTES）

---

## 4. 明确不要当垃圾删

- **Maven 插件模块**（kafka/s3/slack/payment/example/slurm）：slurm 走 classpath；其余走 `plugins/` JAR。
- **`kiwi-admin/backend/bin/restart.sh`、`stop.sh`**：远程部署在用。
- **`docker/plugins/.gitkeep`**：CI/Docker 构建输出目录。
- **BPM JavaDelegate / `@ComponentDescription`**：BPMN XML 按 bean 名引用。
- **Formly 自定义 type、bpmn-js additionalModules**：字符串/模块注册，静态 import 扫描会漏。
- **代码生成仍在用的 vm**：`vm/java/mongo/*`、`vm/java/mybatis/*`、`vm/xml/mybatis/mapper.xml.vm`、`vm/angular/*`、`vm/mongo/*`、`vm/integration/readme.txt.vm`。

---

## 5. 建议清理顺序

1. 本地：删 `kiwi-bpmn-assistant/`、`kiwi-bpmn-designer-agent/`、根 `plugins/`、`data/`。
2. Git：去掉过期的 `kiwi-bpmn-component-*-plugin.jar`（核心组件那只）。
3. Git：RuoYi 遗留 Java（BaseController / SysConfig / SysNotice / MongoEntity / 空 SysConfigService 等）一批删。
4. Git：若依 Vue/JS Velocity 模板。
5. Git：前端确认项（flowable、example.service、kiwi-append 副本、占位 edit-panel、judgeAuth）。
6. 可选：gitignore 过期路径、`cryoems-bpm` BOM、MSW、未引用截图。

每批单独提交，避免和功能改动混在一起。
