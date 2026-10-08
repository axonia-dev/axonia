import type { NotificationsCard } from "@/edition";
import { mailServerPermissionGroup } from "@/selfhosted/features/mail-server/permission-group";
import { MailServerCard } from "@/selfhosted/features/mail-server/ui/MailServerCard";

/** The mail server card leads the organization Notifications page, which a holder of
 *  `mail_server:read` is offered as a holder of `communication:write` is. */
export const mailServerNotificationsCard: NotificationsCard = {
  Card: MailServerCard,
  reveals: ["mail_server:read"],
  permissionGroup: mailServerPermissionGroup,
};
