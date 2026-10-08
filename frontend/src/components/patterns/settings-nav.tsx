import type { LucideIcon } from "lucide-react";
import {
  Bell,
  BookMarked,
  Building2,
  Download,
  FolderSync,
  Network,
  ShieldCheck,
  Users,
} from "lucide-react";

import type { GroupChrome, NavGroup } from "@/components/patterns/nav-types";
import type { SettingsSection } from "@/edition";
import type { OrganizationKind } from "@/features/admin/domain/permission-catalog";
import {
  VOCABULARY_PATH,
  VOCABULARY_PERMISSIONS,
} from "@/features/admin/ui/vocabulary";

/** Both relative: the caller supplies the leading slash, or the `/communities/:slug/` prefix
 *  for a hub session, when calling {@link toNavGroups}. */
export const ACCOUNT_SETTINGS = "settings/account";
export const ORGANIZATION_SETTINGS = "settings/organization";

export interface SettingsNavItem {
  /** Relative to the settings base: a hub session's settings sit under its community. */
  path: string;
  label: string;
  icon: LucideIcon;
  /** Visible when the caller holds ANY of these. Absent means ungated. A single-element
   *  array rather than a bare string so no entry in this catalogue can be shaped two
   *  ways, which is exactly where a filtering bug would hide. */
  permissions?: ReadonlyArray<string>;
  /** True when the section holds sub-pages of its own, so its rail item stays lit while one
   *  of them is open. A leaf section matches its own path exactly, which is what keeps
   *  `/general` dark on a path that merely starts with it. */
  nested?: boolean;
}

export interface SettingsGroup {
  label: string;
  /** Mirrors {@link NavGroup.chrome}; defaults to a drawn header. */
  chrome?: GroupChrome;
  /** Refused for a delegated session regardless of permission, so the list must not
   *  offer it at all. Keyed on this flag rather than on `label`, which is user-visible
   *  copy a rename could silently detach from the filter. */
  requiresOwnSession?: boolean;
  items: ReadonlyArray<SettingsNavItem>;
}

/** A nested section: `/vocabulary` immediately redirects to a per-catalogue tab, so the
 *  rail never sees the bare path it would have to match exactly. */
const VOCABULARY: SettingsNavItem = {
  path: VOCABULARY_PATH,
  label: "Vocabulary",
  icon: BookMarked,
  permissions: VOCABULARY_PERMISSIONS,
  nested: true,
};

/** The tenant's own word: a hub organization holds exactly one community, so for a hub
 *  the tenant IS the community, and every label follows that word rather than
 *  "organization". Exported so the tenant settings page's own heading derives the same
 *  word rather than re-branching on kind. A switch panel, where an edition supplies one,
 *  groups by each row's own kind instead of the current tenant's. */
export function tenantWord(kind: OrganizationKind): { label: string } {
  return kind === "HUB" ? { label: "Community" } : { label: "Organization" };
}

export const GROUP_ACCESS_PATH = "group-access";

/** A directory group can confer a role only where a directory exists to assert it. A hub brokers
 *  none: its members arrive by social or native sign-in, so no token reaching it carries a
 *  `groups` claim and the backend refuses both the listing and the binding. Dropped by kind rather
 *  than by permission, unlike Directory sync, which disappears on its own because `scim:read` is
 *  enterprise-only. This one rides `role:read`, which a hub
 *  legitimately holds for the Roles page, so the permission filter cannot reach it. */
const GROUP_ACCESS: SettingsNavItem = {
  path: GROUP_ACCESS_PATH,
  label: "Group access",
  icon: Network,
  permissions: ["role:read"],
};

/** Ungated by design: switching tenants is moving here off the rail's logo chip, which
 *  becomes identity-only, so every member - not just an admin - must be able to reach
 *  this section or lose the ability to switch at all. Labelled "General" rather than the
 *  tenant word: the group it sits in is already headed by that word, and a row repeating
 *  its own group's heading is the row this rename exists to fix. */
const TENANT_SECTION: SettingsNavItem = {
  path: "general",
  label: "General",
  icon: Building2,
};

type ContributedSettings = Pick<SettingsSection, "group" | "joins">;

function joiningPeopleAndAccess(
  contributed: ReadonlyArray<ContributedSettings>,
): SettingsNavItem[] {
  return contributed
    .filter((section) => section.joins === "people-and-access")
    .flatMap((section) => section.group.items);
}

function ownGroups(
  contributed: ReadonlyArray<ContributedSettings>,
): SettingsGroup[] {
  return contributed
    .filter((section) => section.joins === undefined)
    .map((section) => section.group);
}

/** Grouped by the question an admin is answering, not by what is left over. `tenantWord`
 *  still names the lead group, which is the rail nav's accessible name and this
 *  catalogue's React key even though no header is drawn for it. An edition's own groups sit
 *  after People & access and before Data, which closes the list behind its divider in every
 *  edition. A section that `joins` People & access adds its items there instead, after Group
 *  access and ahead of Directory sync, which places them for a Hub too. `notificationsReveal`
 *  widens the Notifications entry to the permissions an edition's Notifications card is gated on. */
export function organizationSettingsGroups(
  kind: OrganizationKind,
  contributed: ReadonlyArray<ContributedSettings>,
  notificationsReveal: ReadonlyArray<string> = [],
): ReadonlyArray<SettingsGroup> {
  return [
    {
      label: tenantWord(kind).label,
      chrome: "none",
      items: [
        TENANT_SECTION,
        VOCABULARY,
        {
          path: "notifications",
          label: "Notifications",
          icon: Bell,
          permissions: ["communication:write", ...notificationsReveal],
        },
      ],
    },
    {
      label: "People & access",
      items: [
        {
          path: "users",
          label: "Users",
          icon: Users,
          permissions: ["member:read"],
        },
        {
          path: "roles",
          label: "Roles",
          icon: ShieldCheck,
          permissions: ["role:read"],
          // A role's own URL sits below this path, and a leaf section matches its path exactly.
          nested: true,
        },
        ...(kind === "HUB" ? [] : [GROUP_ACCESS]),
        ...joiningPeopleAndAccess(contributed),
        {
          path: "directory-sync",
          label: "Directory sync",
          icon: FolderSync,
          permissions: ["scim:read"],
        },
      ],
    },
    ...ownGroups(contributed),
    {
      label: "Data",
      chrome: "divider",
      requiresOwnSession: true,
      items: [
        {
          path: "data-export",
          label: "Data export",
          icon: Download,
          permissions: ["organization:export"],
        },
      ],
    },
  ];
}

/** The one phrase naming the organization settings surface, kind-aware so the shells and
 *  the account menu never hand-write "Organization settings" where a hub session means
 *  "Community settings". */
export function organizationSettingsTitle(kind: OrganizationKind): string {
  return `${tenantWord(kind).label} settings`;
}

export interface SettingsAccess {
  can: (permission: string) => boolean;
  delegated: boolean;
}

/** Filters the catalogue down to what this caller may actually reach: own-session groups
 *  drop wholesale for a delegated tab, then each section is checked, then groups left
 *  with nothing in them disappear rather than render an empty header. */
export function visibleGroups(
  groups: ReadonlyArray<SettingsGroup>,
  access: Readonly<SettingsAccess>,
): ReadonlyArray<SettingsGroup> {
  return groups
    .filter((group) => !(group.requiresOwnSession && access.delegated))
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) =>
          item.permissions === undefined ||
          item.permissions.some((permission) => access.can(permission)),
      ),
    }))
    .filter((group) => group.items.length > 0);
}

/** Adapts the catalogue to the rail's own type. A group states its own chrome; failing
 *  that, a sole group draws no mark at all: its header would only restate the shell title
 *  directly above it, and a bare divider would separate it from nothing, a line under the
 *  workspace card that the organization rail, whose lead group draws none, never shows. */
export function toNavGroups(
  groups: ReadonlyArray<SettingsGroup>,
  base: string,
): ReadonlyArray<NavGroup> {
  const soleGroup = groups.length === 1;
  return groups.map((group) => ({
    label: group.label,
    chrome: group.chrome ?? (soleGroup ? "none" : "header"),
    items: group.items.map((item) => ({
      to: `${base}/${item.path}`,
      label: item.label,
      icon: <item.icon size={20} />,
      end: !(item.nested ?? false),
    })),
  }));
}
