import { Bell, Bot, ShieldUser, UserRound } from "lucide-react";

import type {
  SettingsGroup,
  SettingsNavItem,
} from "@/components/patterns/settings-nav";
import { ACCOUNT_SETTINGS } from "@/components/patterns/settings-nav";

/** The account half of the settings catalogue, moved out from `settings-nav.tsx` when the two
 *  halves together crossed that file's 300-line ceiling. The shared model - the section and
 *  group types, `visibleGroups`, `toNavGroups` - stays there, alongside the organization half. */

const PROFILE: SettingsNavItem = {
  path: "profile",
  label: "Profile",
  icon: UserRound,
};

const API_KEYS: SettingsNavItem = {
  path: "api-keys",
  label: "API keys",
  icon: Bot,
};

const NOTIFICATIONS: SettingsNavItem = {
  path: "notifications",
  label: "Notifications",
  icon: Bell,
};

const PRIVACY: SettingsNavItem = {
  path: "privacy",
  label: "Privacy",
  icon: ShieldUser,
};

/** Relative, exactly like {@link ACCOUNT_SETTINGS}, and derived from the sections above rather
 *  than spelled out again: anything linking straight at one of these leaves - the notification
 *  panel's own footer - follows a rename of that path instead of quietly 404ing. */
export const ACCOUNT_NOTIFICATION_SETTINGS = `${ACCOUNT_SETTINGS}/${NOTIFICATIONS.path}`;

/** Privacy is offered only where the member, not the organization, controls the data:
 *  a hub session that is not delegated. Elsewhere the backend refuses both actions,
 *  so listing them would only invite a refusal. API keys is offered only where agents may
 *  connect at all, as the backend answers it:
 *  the backend refuses minting outright, so the row is dropped rather than rendered into a
 *  refusal, exactly as the rail has always dropped a destination a caller cannot reach. A page an
 *  edition adds can join this group instead of standing apart (`AccountPage.joins`); it sits after
 *  Notifications and ahead of Privacy, offered by its own `useOffer`.
 *  Profile and Notifications are both held per organization, so a person with no organization
 *  (`orgless`) is offered neither; Privacy and API keys do not need one.
 *
 *  An edition's own pages (`Edition.accountPages`) stand apart from these rows, each group behind
 *  a bare divider. Once one is offered, the Account group draws no header of its own either: the
 *  surface title above it already says "Account settings", the same reason
 *  `organizationSettingsGroups` mutes its own lead group's header below `tenantWord`. */
export function accountSettingsGroups(
  options: Readonly<{
    dataRights: boolean;
    agentAccess: boolean;
    orgless?: boolean;
  }>,
  contributed: ReadonlyArray<SettingsGroup>,
  joining: ReadonlyArray<SettingsNavItem> = [],
): ReadonlyArray<SettingsGroup> {
  const scoped = options.orgless !== true;
  const items = [
    ...(scoped ? [PROFILE] : []),
    ...(options.agentAccess ? [API_KEYS] : []),
    ...(scoped ? [NOTIFICATIONS] : []),
    ...joining,
    ...(options.dataRights ? [PRIVACY] : []),
  ];
  if (contributed.length === 0) return [{ label: "Account", items }];
  return [{ label: "Account", chrome: "none", items }, ...contributed];
}
