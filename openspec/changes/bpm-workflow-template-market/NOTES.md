# Notes

## 2026-06-29 — OpenSpec 补档

- C1/C2 **代码已先于 OpenSpec 文档落地**（直接按 `.cursor/plans/bpm_流程模板_market_a8f3c21d.plan.md` 实施）。
- 本 change 用于规格追溯；`tasks.md` 中 1.x–5.x 已勾选完成。
- C3 Registry 与 AI 完整集成 **不在本 change 范围**，建议分别开 `bpm-market-registry` 与 AI plan 对应 change。

## 2026-06-29 — 移除 Cryo 官方种子

- 删除 `SeedCryoTemplatePackChangeUnit` 与 `bpm/samples/cryo-movie-minimal.bpmn`；市场初始内容由用户发布或 zip 导入提供。

## 已知局限（实现时记录）

1. 详情页 zip 下载使用 `window.open`，可能缺少 Sa-Token 头。
2. `installPackInto` 流程名冲突策略未产品化。
3. manifest 扫描了 `requiredComponentKeys`，安装前未强拦截。
