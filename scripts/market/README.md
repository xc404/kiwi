# Kiwi 远程市场发布工具

将模板包或插件 JAR 发布到 Nexus `kiwi-market-raw` 仓库，并更新 `market/index.json`。

## 前置条件

- Nexus 已启动并完成仓库初始化（见 `docker/nexus/README.md`）
- 环境变量 `NEXUS_PASSWORD`（及可选 `NEXUS_URL`、`NEXUS_USER`）

## 发布模板包

```powershell
.\scripts\market\publish.ps1 template demo-hello 1.0.0 .\demo-hello-1.0.0.kiwi-template-pack -Name "Hello Demo"
```

```bash
NEXUS_PASSWORD=secret ./scripts/market/publish.sh template demo-hello 1.0.0 ./demo-hello-1.0.0.kiwi-template-pack --name "Hello Demo"
```

## 发布插件 JAR

```powershell
.\scripts\market\publish.ps1 plugin kiwi-bpmn-component-example 1.0.0-SNAPSHOT `
  .\kiwi-bpmn\kiwi-bpmn-component-example\target\kiwi-bpmn-component-example-1.0.0-SNAPSHOT.jar `
  -ComponentKeys demoGreeting
```

## E2E 验收

在 Nexus 与 Kiwi 均已运行、且 `application-local.yml` 启用 `kiwi.bpm.remote-market` 后：

```powershell
$env:KIWI_TOKEN = '<Bearer token>'
$env:NEXUS_PASSWORD = '<nexus password>'
.\scripts\market\verify-e2e.ps1
```

本地无 Nexus 时，可运行单元测试验证核心闭环：

```bash
mvn -pl kiwi-admin/backend test "-Dtest=BpmRemoteMarketServiceTest,KiwiVersionCompatibilityHelperTest"
```

## 演示制品

```powershell
.\scripts\market\fixtures\build-demo-template-pack.ps1
mvn -pl kiwi-bpmn/kiwi-bpmn-component-example -am package -DskipTests
```
