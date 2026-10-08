import type { Edition } from "@/edition";
import { openSlots, PassThrough } from "@/open-slots";
import { mailServerNotificationsCard } from "@/selfhosted/features/mail-server/notifications-card";

export const selfHostedEdition: Edition = {
  ...openSlots,
  organizationScope: "installation",
  publicRoutes: [],
  authenticatedRoutes: [],
  AppFrame: PassThrough,
  sessionlessPaths: [],
  notificationsCard: mailServerNotificationsCard,
};
