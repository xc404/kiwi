# 已回退

因 **Camunda 7.24.0** 在 **Spring Boot 4** 下与 Jersey 自动配置不兼容（且 CE 在 Maven Central 尚无 `7.24.3` + `*-4` starter 组合），本变更**不再继续实施**。

工程已恢复为 **Spring Boot 3.5.8**、**Spring Framework 6.2.14** 及升级前的依赖与代码。

将来若 Central 提供 **Camunda ≥7.24.3** 的 `camunda-bpm-spring-boot-starter-4*` 等工件，可再开变更评估 Boot 4。
