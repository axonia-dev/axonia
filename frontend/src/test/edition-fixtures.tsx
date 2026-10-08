import type { ReactNode } from "react";

import type { EditionSlots } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { openSlots } from "@/open-slots";

/** An edition whose one settings section contributes an Audit group after Members, the way the
 *  Cloud's single sign-on and billing sections contribute theirs. */
export const WITH_AUDIT: EditionSlots = {
  ...openSlots,
  settingsSections: [
    {
      group: { label: "Audit", items: [] },
      pages: {},
      permissionGroup: {
        follows: "Members",
        permissions: [
          {
            code: "audit:read",
            group: "Audit",
            action: "read",
            enterpriseOnly: true,
            label: "View the audit trail",
            description: "See who changed what.",
          },
        ],
      },
    },
  ],
};

function NotificationsCardProbe() {
  return <p>Edition card</p>;
}

/** An edition whose Notifications card is shown to holders of `ledger:read`, offered in the roles
 *  matrix as a Ledger group after Communication. */
export const WITH_NOTIFICATIONS_CARD: EditionSlots = {
  ...openSlots,
  notificationsCard: {
    Card: NotificationsCardProbe,
    reveals: ["ledger:read"],
    permissionGroup: {
      follows: "Communication",
      permissions: [
        {
          code: "ledger:read",
          group: "Ledger",
          action: "read",
          label: "View the ledger",
          description: "See what was sent.",
        },
      ],
    },
  },
};

export function inEdition(edition: EditionSlots) {
  return function EditionWrapper({
    children,
  }: Readonly<{ children: ReactNode }>) {
    return <EditionProvider edition={edition}>{children}</EditionProvider>;
  };
}
