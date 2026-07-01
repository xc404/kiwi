# Kiwi BPM 支付组件

支付宝 / 微信支付沙箱 **下单**（`alipay.trade.precreate`、微信 Native）与 **查单** 的 BPM 插件。

## 组件

| Bean Key | 说明 |
|----------|------|
| `paymentCreate` | `channel`=`alipay`\|`wechat`，`outTradeNo`，`amount`（分），`subject` |
| `paymentQuery` | `channel`，`outTradeNo` → `payStatus`，`channelTradeNo` |

凭证通过节点入参或项目环境变量注入（`ALIPAY_*`、`WECHAT_*`），勿写入 BPMN 明文。

## 构建

```bash
mvn -pl kiwi-bpmn/kiwi-bpmn-component-payment package "-Dkiwi.build.plugins=true" -DskipTests
```

或仓库根目录：`mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests`

## 演示项目

见 `kiwi-admin/backend/src/main/resources/bpm/payment-integration-demo/`。
