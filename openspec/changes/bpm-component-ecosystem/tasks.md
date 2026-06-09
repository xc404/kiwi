## 1. 示例模块 kiwi-bpmn-component-example

- [x] 1.1 注册 Maven 模块（`kiwi-bpmn/pom.xml`、根 `dependencyManagement`）
- [x] 1.2 实现 `DemoGreetingActivity` + 单元测试
- [x] 1.3 `backend` 引入依赖；编写 `README.md`

## 2. 项目环境变量后端

- [x] 2.1 实体 `BpmProjectEnvVar`、Dao、加密 Service（复用 AesUtil 模式）
- [x] 2.2 `BpmProjectEnvCtl` CRUD API（`bpm/project/{projectId}/env`）+ 项目归属校验
- [x] 2.3 `BpmProcessStartService` 启动时加载并注入 env（用户 variables 优先；加密项瞬态变量）

## 3. 项目环境变量前端

- [x] 3.1 项目工作区「环境变量」Tab：列表、新增、编辑、删除
- [x] 3.2 加密项密码框、不回显明文

## 4. 文档与验证

- [x] 4.1 更新 `docs/bpm-component.zh-CN.md`（第三方组件 + 项目 env）
- [x] 4.2 `mvn -pl kiwi-admin/backend -am compile -DskipTests` 通过
