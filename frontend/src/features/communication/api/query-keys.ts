export const communicationKeys = {
  all: ["communication"] as const,
  notificationPreferences: () =>
    [...communicationKeys.all, "notification-preferences"] as const,
  notifications: () => [...communicationKeys.all, "notifications"] as const,
  unreadCount: () => [...communicationKeys.all, "unread-count"] as const,
};
