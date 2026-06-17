## 1. 模型与指纹工具

- [x] 1.1 在 `BpmComponent` 上新增 `deploymentSignature` 字段
- [x] 1.2 新增 `BpmComponentDeploymentSignature`（或等价工具类），实现稳定 SHA-256 指纹计算

## 2. 服务逻辑

- [x] 2.1 调整 `BpmComponentService.deploy`：按指纹过滤，仅对变更/新增执行 `save`/`saveAll`
- [x] 2.2 调整 `deployComponent`：保存前设置 `deploymentSignature`

## 3. 验证

- [x] 3.1 为指纹工具添加单元测试（相同输入同结果、细微字段变化结果不同）
