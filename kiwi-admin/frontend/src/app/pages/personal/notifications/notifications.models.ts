/** 与顶部铃铛下拉 {@link HomeNoticeComponent} 的分栏一致 */
export type NotificationChannel = 'notice' | 'message' | 'todo';

export interface NotificationItem {
  id: string;
  channel: NotificationChannel;
  title: string;
  summary: string;
  createdAt: string;
  read: boolean;
  tag?: { text: string; color: string };
  extra?: string;
}
