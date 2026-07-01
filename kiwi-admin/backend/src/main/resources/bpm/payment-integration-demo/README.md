# 通用支付集成演示

`payment-integration-demo` 项目演示 **Solution 型** 模板：主流程通过 CallActivity 编排创建订单、渠道下单、查单与 Slack 通知。

## 流程

| 流程 | 说明 |
|------|------|
| `p-pay-main-001` | 入口主流程 |
| `p-pay-create-001` | 生成 `outTradeNo` |
| `p-pay-alipay-001` / `p-pay-wechat-001` | 渠道下单（`plugin_paymentCreate`） |
| `p-pay-query-001` | 等待后查单（`plugin_paymentQuery`） |

## 使用前

1. 在项目环境变量中填写支付宝或微信 **沙箱** 凭证（`ALIPAY_*` / `WECHAT_*`）。
2. 设置 `PAY_CHANNEL` 为 `alipay` 或 `wechat`。
3. 可选：配置 `SLACK_WEBHOOK_URL` 接收结果通知。
4. 确保 `plugins/` 已包含 `kiwi-bpmn-component-payment-*-plugin.jar`（`mvn -Pbuild-plugins`）。

## 插件组件

- `plugin_paymentCreate` — `kiwi-bpmn-component-payment`
- `plugin_paymentQuery` — `kiwi-bpmn-component-payment`

生产环境请使用异步回调 + 幂等查单；本演示以主动查单为主。
