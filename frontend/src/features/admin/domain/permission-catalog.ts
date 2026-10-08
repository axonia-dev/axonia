export interface PermissionDef {
  code: string;
  label: string;
  description: string;
  group: string;
  action: "read" | "write" | "manage";
  ownerOnly?: boolean;
  enterpriseOnly?: boolean;
}

export const PERMISSION_CATALOG: ReadonlyArray<PermissionDef> = [
  // Workspace
  {
    code: "organization:write",
    group: "Workspace",
    action: "write",
    label: "Edit workspace settings",
    description: "Change the organization's name, logo, and profile.",
  },
  {
    code: "organization:delete",
    group: "Workspace",
    action: "manage",
    ownerOnly: true,
    label: "Delete the organization",
    description: "Permanently delete this workspace and all its data.",
  },
  {
    code: "ownership:transfer",
    group: "Workspace",
    action: "manage",
    ownerOnly: true,
    label: "Transfer ownership",
    description: "Hand the Owner role to someone else.",
  },
  // Communication
  // No enterpriseOnly flag: the roundup half of this is enterprise-only, but the
  // organization-wide mail kill switch it also governs applies to a hub just as much.
  {
    code: "communication:write",
    group: "Communication",
    action: "write",
    label: "Manage communication",
    description:
      "Switch outbound email on or off, and in an enterprise configure the quarterly roundup.",
  },
  // Members
  {
    code: "member:read",
    group: "Members",
    action: "read",
    label: "View members",
    description: "See the people in the organization.",
  },
  {
    code: "member:write",
    group: "Members",
    action: "write",
    label: "Manage members",
    description: "Suspend and reactivate members.",
  },
  // Roles & access
  {
    code: "role:read",
    group: "Roles & access",
    action: "read",
    label: "View roles",
    description: "See roles and who holds them.",
  },
  {
    code: "role:assign",
    group: "Roles & access",
    action: "write",
    label: "Assign roles",
    description: "Grant and revoke roles for people and groups.",
  },
  {
    code: "role:write",
    group: "Roles & access",
    action: "manage",
    label: "Manage custom roles",
    description: "Create, edit, and delete custom roles.",
  },
  // Directory sync
  {
    code: "scim:read",
    group: "Directory sync",
    action: "read",
    enterpriseOnly: true,
    label: "View directory sync",
    description: "See SCIM provisioning tokens and status.",
  },
  {
    code: "scim:write",
    group: "Directory sync",
    action: "write",
    enterpriseOnly: true,
    label: "Manage directory sync",
    description: "Issue and revoke SCIM provisioning tokens.",
  },
  // Catalog
  {
    code: "community_kind:read",
    group: "Catalog",
    action: "read",
    enterpriseOnly: true,
    label: "View community kinds",
    description: "See the types of community that can exist.",
  },
  {
    code: "community_kind:write",
    group: "Catalog",
    action: "write",
    enterpriseOnly: true,
    label: "Manage community kinds",
    description: "Define the types of community that can exist.",
  },
  {
    code: "library_type:read",
    group: "Catalog",
    action: "read",
    label: "View library types",
    description: "See the types of library resource.",
  },
  {
    code: "library_type:write",
    group: "Catalog",
    action: "write",
    label: "Manage library types",
    description: "Define the types of library resource.",
  },
  {
    code: "section_type:read",
    group: "Catalog",
    action: "read",
    label: "View section types",
    description: "See the types of session section.",
  },
  {
    code: "section_type:write",
    group: "Catalog",
    action: "write",
    label: "Manage section types",
    description: "Define the types of session section.",
  },
  {
    code: "reaction:read",
    group: "Catalog",
    action: "read",
    label: "View reactions",
    description: "See the reaction queue and how often each reaction is used.",
  },
  {
    code: "reaction:write",
    group: "Catalog",
    action: "write",
    label: "Manage reactions",
    description: "Approve, decline and remove custom reactions.",
  },
  // Data
  {
    code: "organization:export",
    group: "Data",
    action: "manage",
    enterpriseOnly: true,
    label: "Export organization data",
    description:
      "Download the personal data this organization holds about its members.",
  },
];

/** Codes the gate enforces that no roles matrix offers, so the ceiling still knows them.
 *  `FrontendPermissionCatalogTest` holds this to the backend's `NOT_SURFACED`. */
export const NOT_SURFACED: ReadonlyArray<string> = ["community:create"];

/** The open edition's permission groups, in the order the roles matrix draws them. A group an
 *  edition contributes names the open group it is drawn after. */
export const OPEN_GROUP_ORDER = [
  "Workspace",
  "Communication",
  "Members",
  "Roles & access",
  "Directory sync",
  "Catalog",
  "Data",
] as const;

export type OpenPermissionGroupLabel = (typeof OPEN_GROUP_ORDER)[number];

/** One domain of the catalog: the heading a reader collapses, and what it holds. */
export interface PermissionGroup {
  key: string;
  label: string;
  permissions: PermissionDef[];
}

export type OrganizationKind = "ENTERPRISE" | "HUB";

export function togglePermission(
  permissions: string[],
  code: string,
): string[] {
  return permissions.includes(code)
    ? permissions.filter((p) => p !== code)
    : [...permissions, code];
}
