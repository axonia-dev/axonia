export const accessKeys = {
  all: ["access"] as const,
  me: () => [...accessKeys.all, "me"] as const,
};

export const adminKeys = {
  all: ["admin"] as const,
  users: (params: unknown) => [...adminKeys.all, "users", params] as const,
  user: (id: string) => [...adminKeys.all, "user", id] as const,
  roles: () => [...adminKeys.all, "roles"] as const,
  roleAssignees: (id: number) =>
    [...adminKeys.all, "role-assignees", id] as const,
  idpGroups: () => [...adminKeys.all, "idp-groups"] as const,
  myOrganizations: () => [...adminKeys.all, "my-organizations"] as const,
  currentOrganization: () =>
    [...adminKeys.all, "current-organization"] as const,
  scimTokens: () => [...adminKeys.all, "scim-tokens"] as const,
  roundupSettings: () => [...adminKeys.all, "roundup-settings"] as const,
  roundupPreview: () => [...adminKeys.all, "roundup-preview"] as const,
};
