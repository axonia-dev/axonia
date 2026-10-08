import { describe, expect, it } from "vitest";

import { organizationSettingsGroups } from "@/components/patterns/settings-nav";
import { accountSettingsGroups } from "@/components/patterns/settings-nav.account";
import { VOCABULARY_TAB_SPECS } from "@/features/admin/ui/vocabulary";
import {
  ACCOUNT_LEAF_PATHS,
  MOVED,
  ORGANIZATION_LEAF_PATHS,
  ORGANIZATION_SECTIONS,
  ROLES_PATH,
} from "@/routes/settings-routes.catalogue";

/** settings-nav.tsx's catalogue governs WHAT is listed; these routes decide WHERE each
 *  section actually lives. Nothing else checks the two agree - renaming a `path` in the
 *  catalogue without updating the route registration below is a dead rail link that every
 *  other check (typecheck, lint, coverage, knip, both builds) stays green on. */

// ENTERPRISE lists every section that exists; HUB lists a subset of it (see the group-access
// assertion below). Routes are registered for the superset, so ENTERPRISE is what the
// registration must match exactly.
function catalogueOrganizationPaths(): string[] {
  return organizationSettingsGroups("ENTERPRISE", []).flatMap((group) =>
    group.items.map((item) => item.path),
  );
}

function hubOrganizationPaths(): string[] {
  return organizationSettingsGroups("HUB", []).flatMap((group) =>
    group.items.map((item) => item.path),
  );
}

function catalogueAccountPaths(): string[] {
  // Every option on surfaces every leaf the catalogue can ever list, including the ones
  // (privacy, api-keys) a given session may not currently be entitled to.
  return accountSettingsGroups(
    { dataRights: true, agentAccess: true },
    [],
  ).flatMap((group) => group.items.map((item) => item.path));
}

function sorted(paths: ReadonlyArray<string>): string[] {
  return paths.slice().sort((a, b) => a.localeCompare(b));
}

describe("settings route registration matches the settings-nav catalogue", () => {
  it("registers exactly the organization sections the catalogue lists", () => {
    expect(sorted(ORGANIZATION_LEAF_PATHS)).toEqual(
      sorted(catalogueOrganizationPaths()),
    );
  });

  // The route stays registered for both kinds - OrganizationGroupAccessRoute redirects a hub
  // rather than the route disappearing - so the only thing that differs is what the rail offers.
  it("offers a hub every section but group access", () => {
    expect(hubOrganizationPaths()).not.toContain("group-access");
    expect(sorted([...hubOrganizationPaths(), "group-access"])).toEqual(
      sorted(catalogueOrganizationPaths()),
    );
  });

  it("registers exactly the account leaves the catalogue lists", () => {
    expect(sorted(ACCOUNT_LEAF_PATHS)).toEqual(sorted(catalogueAccountPaths()));
  });

  it("serves roles as a leaf with its own nested route, not as a bare section", () => {
    expect(Object.keys(ORGANIZATION_SECTIONS)).not.toContain(ROLES_PATH);
    expect(ORGANIZATION_LEAF_PATHS).toContain(ROLES_PATH);
  });

  it("only redirects to settings paths the catalogue actually serves", () => {
    const servedPaths = new Set([
      ...catalogueAccountPaths().map((path) => `settings/account/${path}`),
      ...catalogueOrganizationPaths().map(
        (path) => `settings/organization/${path}`,
      ),
      // Vocabulary is one leaf in the catalogue but serves one URL per tab, so its
      // sub-paths are served without being sections of their own.
      ...VOCABULARY_TAB_SPECS.map(
        (tab) => `settings/organization/vocabulary/${tab.path}`,
      ),
    ]);
    const unservedTargets = MOVED.filter(([, to]) => !servedPaths.has(to));
    expect(unservedTargets).toEqual([]);
  });
});
