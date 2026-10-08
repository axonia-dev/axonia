import { z } from "zod";

import { memberStatusSchema } from "@/features/identity/domain/user";
import { pageSchema } from "@/lib/page";

const effectiveRoleSchema = z.object({
  role: z.string(),
  via: z.string(),
  bindingId: z.number().nullable(),
  roleId: z.number(),
  permissions: z.array(z.string()).default([]),
});
export const adminUserSchema = z.object({
  // null until the user's first SSO sign-in attaches their Keycloak subject. A
  // SCIM-provisioned member exists in the directory before they ever log in, and
  // must still list (as pending) rather than poison the whole list parse.
  userId: z.string().nullable(),
  displayName: z.string(),
  // Nullable for the same reason as userId: identity_user.email is nullable and SCIM accepts
  // a user with no userName, so one such member must render as a row rather than fail the
  // parse and take the whole console down with them.
  email: z.string().nullable(),
  effectiveRoles: z.array(effectiveRoleSchema),
  joinedAt: z.string().nullable().default(null),
  lastActiveAt: z.string().nullable().default(null),
  memberStatus: memberStatusSchema,
});
export type AdminUser = z.infer<typeof adminUserSchema>;

// A page, not a bare array: the console tallies "N people" beside the list, and only the
// envelope's total distinguishes the organization's headcount from the length of one page.
export const adminUserPageSchema = pageSchema(adminUserSchema);

export const roleBindingSchema = z.object({
  id: z.number(),
  subjectType: z.string(),
  subjectId: z.string(),
  roleId: z.number(),
  subjectLabel: z.string().nullable(),
});
export type RoleBinding = z.infer<typeof roleBindingSchema>;

const idpGroupSchema = z.object({
  groupId: z.string(),
  displayName: z.string(),
  inDirectory: z.boolean(),
  // True when the directory pushed this group under a key a binding was already waiting on, so
  // it is held and its bindings grant nothing until somebody with role:assign confirms it.
  awaitingConfirmation: z.boolean(),
  bindings: z.array(roleBindingSchema),
});
export type IdpGroup = z.infer<typeof idpGroupSchema>;

const groupSourceSchema = z.enum(["SCIM", "GRAPH", "NONE"]);
export type GroupSource = z.infer<typeof groupSourceSchema>;

export const idpGroupsResponseSchema = z.object({
  source: groupSourceSchema,
  groups: z.array(idpGroupSchema),
});
export type IdpGroupsResponse = z.infer<typeof idpGroupsResponseSchema>;

export const roleSchema = z.object({
  id: z.number(),
  name: z.string(),
  description: z.string().nullable().default(null),
  system: z.boolean().default(false),
  permissions: z.array(z.string()),
});
export type Role = z.infer<typeof roleSchema>;
