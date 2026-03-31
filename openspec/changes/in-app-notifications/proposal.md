## Why

管理后台顶部铃铛下拉原为静态占位数据，与「个人设置」中的站内信说明不一致；需要 **按登录用户** 从服务端拉取通知/消息/待办预览，并提供独立 **消息通知** 页做完整浏览与操作，便于后续接入真实推送与已读回写。

## What Changes

- **后端**：MongoDB 存储站内消息（按 `userId` 与 `channel` 分栏）；`GET /notifications`（需登录）返回当前用户列表（倒序）；新用户首次访问时写入种子数据以便联调。
- **前端**：`NotificationsService` 调用上述接口（响应 `content` 集合封装）；**消息通知** 路由页（分栏、分页、标已读/删除等本地交互）；**HomeNoticeComponent** 按三类 channel 展示预览（每类最多 5 条）；用户菜单增加「消息通知」入口。

## Capabilities

### New Capabilities

- `user-inbox-notifications`：登录用户站内消息列表 API、与前端铃铛/消息页一致的数据模型与分栏（`notice` / `message` / `todo`）。

### Modified Capabilities

- （无；仓库根目录无已归档 `openspec/specs` 基线需 delta。）

## Impact

- **后端**：`com.kiwi.project.notification`（Mongo `notification_message`）、`NotificationCtl` 挂载 `/notifications`。
- **前端**：`pages/personal/notifications/*`、`NotificationsService`、`home-notice`、`personal-routing`、`layout-head-right-menu`。
- **依赖**：MongoDB 可用；需登录态与 `Authorization` 头（与现有 `BaseHttpService` 一致）。
