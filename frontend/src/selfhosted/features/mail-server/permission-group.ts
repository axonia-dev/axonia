import type { PermissionGroupContribution } from "@/features/admin/domain/edition-permissions";
import type { PermissionDef } from "@/features/admin/domain/permission-catalog";

const MAIL_SERVER_PERMISSIONS: ReadonlyArray<PermissionDef> = [
  {
    code: "mail_server:read",
    group: "Mail server",
    action: "read",
    label: "View mail server",
    description: "See the mail server and the sender address.",
  },
  {
    code: "mail_server:write",
    group: "Mail server",
    action: "write",
    label: "Change mail server",
    description: "Set up, test, change and clear the mail server.",
  },
];

/** The installation's mail server permissions, drawn in the roles matrix right after
 *  Communication. The backend's SelfHostedFrontendPermissionCatalogTest reads this file by its name. */
export const mailServerPermissionGroup: PermissionGroupContribution = {
  follows: "Communication",
  permissions: MAIL_SERVER_PERMISSIONS,
};
