import type { ComponentType } from "react";

import { GROUP_ACCESS_PATH } from "@/components/patterns/settings-nav";
import { DirectorySyncPage } from "@/features/admin/ui/DirectorySyncPage";
import { OrganizationNotificationsPage } from "@/features/admin/ui/OrganizationNotificationsPage";
import { TenantSettingsPage } from "@/features/admin/ui/TenantSettingsPage";
import { UsersPage } from "@/features/admin/ui/UsersPage";
import { VOCABULARY_PATH } from "@/features/admin/ui/vocabulary";
import { NotificationPreferencesPage } from "@/features/communication/ui/NotificationPreferencesPage";
import { AccountProfilePage } from "@/features/identity/ui/AccountProfilePage";
import { DataExportPage } from "@/features/privacy/ui/DataExportPage";

/** The route catalogue `settings-routes.tsx` renders: every path settings absorbed, and the
 *  pages the account and organization leaves resolve to. Held here, apart from the routing
 *  logic itself, so a test can import the data without dragging in `react-router-dom` - and,
 *  because `VOCABULARY_PATH` comes from the pure `vocabulary.ts` rather than
 *  `vocabulary-tabs.tsx`, without dragging in the vocabulary managers' component trees
 *  either. */

// Every path settings absorbed, old to new. Rendered twice - once at the top level and
// once nested under a hub's community - from one list, so the two can never disagree.
// Exported so a test can assert every destination is a path the catalogue actually serves.
// A type path (the three `/admin/*` type redirects and their `settings/organization/*`
// predecessors) points straight at its tab, so no URL takes two redirects to land.
export const MOVED: ReadonlyArray<readonly [string, string]> = [
  ["me/api-keys", "settings/account/api-keys"],
  ["admin/kinds", "settings/organization/vocabulary/community-types"],
  ["admin/library-types", "settings/organization/vocabulary/library-types"],
  ["admin/section-types", "settings/organization/vocabulary/section-types"],
  [
    "settings/organization/community-types",
    "settings/organization/vocabulary/community-types",
  ],
  [
    "settings/organization/library-types",
    "settings/organization/vocabulary/library-types",
  ],
  [
    "settings/organization/section-types",
    "settings/organization/vocabulary/section-types",
  ],
  ["admin/users", "settings/organization/users"],
  ["admin/roles", "settings/organization/roles"],
  ["admin/group-access", "settings/organization/group-access"],
  ["admin/directory-sync", "settings/organization/directory-sync"],
  ["admin/data-export", "settings/organization/data-export"],
];

/** The account leaves held per organization, each rendered behind `AccountOrganizationRoute`, keyed the same way {@link ORGANIZATION_SECTIONS}
 *  keys the organization ones. Privacy and API keys are not among them: each needs its own gated
 *  route (`AccountPrivacyRoute`, `AccountApiKeysRoute`), so both are registered by hand in
 *  `settings-routes.tsx` instead. */
export const ACCOUNT_PAGES: Record<string, ComponentType> = {
  profile: AccountProfilePage,
  notifications: NotificationPreferencesPage,
};

export const PRIVACY_PATH = "privacy";
export const API_KEYS_PATH = "api-keys";

/** Keyed the same way as {@link ORGANIZATION_SECTIONS} so a test can assert this list matches
 *  the catalogue's account paths. */
export const ACCOUNT_LEAF_PATHS: ReadonlyArray<string> = [
  ...Object.keys(ACCOUNT_PAGES),
  PRIVACY_PATH,
  API_KEYS_PATH,
];

/** Keyed by path, the same key the catalogue (`organizationSettingsGroups`) uses, so a test
 *  can assert `Object.keys(...)` against it - renaming a `path` in the catalogue without
 *  updating this map fails that test instead of silently 404ing the rail link. */
export const ORGANIZATION_SECTIONS: Record<string, ComponentType> = {
  general: TenantSettingsPage,
  notifications: OrganizationNotificationsPage,
  users: UsersPage,
  "directory-sync": DirectorySyncPage,
  "data-export": DataExportPage,
};

/** Roles carries a nested `:roleId` route so a role, a diff and a search are all linkable,
 *  which a bare `ComponentType` in ORGANIZATION_SECTIONS cannot express. */
export const ROLES_PATH = "roles";

/** Every organization leaf the catalogue lists, keyed the same way {@link ACCOUNT_LEAF_PATHS}
 *  is. Three are not in {@link ORGANIZATION_SECTIONS}, because a bare `ComponentType` there
 *  cannot carry any of the things they need: Vocabulary needs `base` passed to it, Group access
 *  needs a kind gate (a hub has no directory to read a group from), and Roles needs a nested
 *  `:roleId` route beneath its own. settings-routes registers all three by hand, exactly as it
 *  does the account's privacy leaf. An edition's own sections register through
 *  `Edition.settingsSections`. */
export const ORGANIZATION_LEAF_PATHS: ReadonlyArray<string> = [
  ...Object.keys(ORGANIZATION_SECTIONS),
  GROUP_ACCESS_PATH,
  ROLES_PATH,
  VOCABULARY_PATH,
];
