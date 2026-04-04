## 1. 公共工具

- [x] 1.1 在 `kiwi-common` 新增 `ProcessHelper` 与 `StreamResult`
- [x] 1.2 实现 `waitForDrain`（stdout/可选 stderr 排空、限时/无限等待、超时销毁）

## 2. 调用迁移

- [x] 2.1 `CliHelpParser` 使用 `ProcessHelper` 替代手写读线程
- [x] 2.2 `ShellActivityBehavior` 使用 `ProcessHelper`（与 `redirectErrorStream` 标志一致）
- [x] 2.3 `SlurmTaskManager#submitSbatch` 使用 `ProcessHelper`（未合并 stderr）

## 3. 验证

- [ ] 3.1 在具备 Maven 的环境编译 `kiwi-common`、`kiwi-admin/backend`、`kiwi-bpmn-component`
