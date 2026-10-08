import { z } from "zod";

import { accessMeSchema } from "@/features/admin/domain/access";
import type {
  AdminGateway,
  BindGroupBody,
  CreateRoleBody,
  GrantRoleBody,
  UpdateRoleBody,
} from "@/features/admin/domain/admin-gateway";
import {
  adminUserPageSchema,
  adminUserSchema,
  idpGroupsResponseSchema,
  roleBindingSchema,
  roleSchema,
} from "@/features/admin/domain/admin-user";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

const roleListSchema = z.array(roleSchema);
const roleBindingListSchema = z.array(roleBindingSchema);

export const httpAdminGateway: AdminGateway = {
  async getMe() {
    const data = await apiFetch<unknown>("/axonia/access/me");
    return accessMeSchema.parse(data);
  },

  async listUsers(params) {
    const data = await apiFetch<unknown>(
      `/axonia/admin/users${query({ ...params })}`,
    );
    return adminUserPageSchema.parse(data);
  },

  async getUser(userId) {
    const data = await apiFetch<unknown>(`/axonia/admin/users/${userId}`);
    return adminUserSchema.parse(data);
  },

  async listRoles() {
    const data = await apiFetch<unknown>("/axonia/roles");
    return roleListSchema.parse(data);
  },

  async roleAssignees(roleId) {
    const data = await apiFetch<unknown>(`/axonia/roles/${roleId}/assignees`);
    return roleBindingListSchema.parse(data);
  },

  async grantRole(body: GrantRoleBody) {
    const data = await apiFetch<unknown>("/axonia/role-bindings", {
      method: "POST",
      body: JSON.stringify(body),
    });
    return roleBindingSchema.parse(data);
  },

  async revokeRole(id: number) {
    await apiFetchVoid(`/axonia/role-bindings/${id}`, {
      method: "DELETE",
    });
  },

  async listIdpGroups() {
    const data = await apiFetch<unknown>("/axonia/admin/idp-groups");
    return idpGroupsResponseSchema.parse(data);
  },

  async bindGroup(body: BindGroupBody) {
    const data = await apiFetch<unknown>("/axonia/role-bindings", {
      method: "POST",
      body: JSON.stringify({
        subjectType: "GROUP",
        subjectId: body.groupId,
        subjectLabel: body.displayName,
        roleId: body.roleId,
      }),
    });
    return roleBindingSchema.parse(data);
  },

  async unbindGroup(id: number) {
    await apiFetchVoid(`/axonia/role-bindings/${id}`, {
      method: "DELETE",
    });
  },

  async confirmGroupBinding(id: number) {
    await apiFetchVoid(`/axonia/role-bindings/${id}/confirm-group`, {
      method: "POST",
    });
  },

  async createRole(body: CreateRoleBody) {
    const data = await apiFetch<unknown>("/axonia/roles", {
      method: "POST",
      body: JSON.stringify(body),
    });
    return roleSchema.parse(data);
  },

  async updateRole(id: number, body: UpdateRoleBody) {
    const data = await apiFetch<unknown>(`/axonia/roles/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
    return roleSchema.parse(data);
  },

  async deleteRole(id: number) {
    await apiFetchVoid(`/axonia/roles/${id}`, { method: "DELETE" });
  },
};
