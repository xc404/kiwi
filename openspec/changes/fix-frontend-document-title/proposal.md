## Why

浏览器标签与默认 HTML `<title>` 仍使用模板名 `NgAntAdmin`，路由级标题后缀为 `Ant Design`，与产品品牌及 `environment.appName`（Kiwi Admin）不一致，影响识别度与 SEO 展示。

## What Changes

- 生产环境 `environment.prod.ts` 增加 `appName: 'Kiwi Admin'`，与开发环境一致。
- `CustomPageTitleResolverService` 将文档标题格式为「路由标题 - `environment.appName`」。
- `index.html` 默认 `<title>` 与 `appName` 对齐。
- 登录路由 `title` 改为「登录」，与其它中文业务路由风格一致。
- 默认布局页脚由模板文案「Ant Design ©2022…」改为「`environment.appName` + 当前年」，与品牌一致。

## Impact

- **代码**：`kiwi-admin/frontend` 下 `src/environments/environment.prod.ts`、`src/app/core/services/common/custom-page-title-resolver.service.ts`、`src/index.html`、`src/app/pages/login/login-routing.ts`、`src/app/layout/default/default.component.ts` 与 `default.component.html`。
- **行为**：登录页等带路由标题的页面显示为「页面名 - Kiwi Admin」；页脚展示 Kiwi Admin 与当年年份。
