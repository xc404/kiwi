## 1. 后端

- [x] 1.1 新增 `NotificationMessage` 文档模型与 `NotificationMessageDao`（按 `userId` 统计与倒序）
- [x] 1.2 实现 `NotificationService`（首访种子数据、`NotificationDto` 映射）
- [x] 1.3 新增 `NotificationCtl`：`GET /notifications`，`@SaCheckLogin`，继承 `BaseCtl`

## 2. 前端

- [x] 2.1 `NotificationsService.list()` 调用 `/notifications` 并解包 `content`
- [x] 2.2 个人中心路由 `notifications` 懒加载消息通知页（列表/分栏/分页/本地操作）
- [x] 2.3 `HomeNoticeComponent` 使用服务数据，按 channel 分 Tab，每类预览条数上限
- [x] 2.4 顶部用户菜单增加「消息通知」入口；铃铛下拉保留「查看全部」

## 3. 验证

- [x] 3.1 登录后打开铃铛下拉，确认三类 tab 有数据或空态；进入消息通知页列表与接口一致（需本地启动前后端与 Mongo）
