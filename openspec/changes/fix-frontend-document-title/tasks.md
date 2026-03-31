## 1. 代码与配置

- [x] 1.1 在 `environment.prod.ts` 增加 `appName: 'Kiwi Admin'`。
- [x] 1.2 在 `custom-page-title-resolver.service.ts` 中引入 `environment`，后缀由 `Ant Design` 改为 `environment.appName`。
- [x] 1.3 `index.html` 中 `<title>` 改为 `Kiwi Admin`。
- [x] 1.4 `login-routing.ts` 中登录路由 `title` 改为 `登录`。
- [x] 1.5 `default.component` 页脚使用 `environment.appName` 与当前年，替换 Ant Design 模板文案。

## 2. 验证

- [x] 2.1 在 `kiwi-admin/frontend` 执行 `npx tsc -p tsconfig.app.json --noEmit` 与 `npx ng lint` 通过。说明：`npm run build`（production）当前因既有 bpmn-js 字体资源 loader 配置失败，与本次 title 改动无关。
- [ ] 2.2 本地启动后访问 `/login` 与带 `title` 的内页路由，确认浏览器标签为「页面名 - Kiwi Admin」；在开启页脚时确认底部为「Kiwi Admin ©当前年」（需人工）。
