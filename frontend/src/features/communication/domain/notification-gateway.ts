import type { NotificationCursor, NotificationPage } from "./notification";

/** Port for a member's own notification panel. Implemented by the HTTP adapter. */
export interface NotificationGateway {
  page(cursor: NotificationCursor | undefined): Promise<NotificationPage>;
  unreadCount(): Promise<number>;
  markRead(id: number): Promise<void>;
  markAllRead(): Promise<void>;
}
