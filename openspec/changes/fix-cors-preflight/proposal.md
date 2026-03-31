## Why

自 `http://localhost:4201` 访问 `http://localhost:8088` 时，浏览器对带自定义头（如 `Authorization`）的请求先发 **OPTIONS 预检**。Sa-Token 全局拦截器对 `/**` 要求登录，预检请求**不带 Token**，鉴权失败，响应不满足 CORS 预检要求，浏览器报 CORS 错误。

## What Changes

- **SaTokenConfigure**：对 **`OPTIONS`** 请求在拦截器中直接放行，不执行 `StpUtil.checkLogin()`。
- **CorsConfig**：补充 **`allowedHeaders`**、**`exposedHeaders`**、**`maxAge`**，与 `app.cors.allowed-origins` 中的前端 Origin 配合，满足常见预检（含 `Authorization`）。

## Impact

- **代码**：`SaTokenConfigure.java`、`CorsConfig.java`。
- **行为**：跨域 API 预检通过；实际 `GET/POST` 仍走 Sa-Token 校验（需带 Token）。
