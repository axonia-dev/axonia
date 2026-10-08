import type { AccessMe } from "@/features/admin/domain/access";
import type {
  AdminUser,
  IdpGroupsResponse,
  Role,
  RoleBinding,
} from "@/features/admin/domain/admin-user";
import type { Page } from "@/lib/page";

export interface GrantRoleBody {
  subjectType: string;
  subjectId: string;
  roleId: number;
  subjectLabel?: string;
}

export interface BindGroupBody {
  groupId: string;
  displayName: string;
  roleId: number;
}

export interface CreateRoleBody {
  name: string;
  description: string | null;
  permissions: string[];
}

export type UpdateRoleBody = CreateRoleBody;

export interface AdminGateway {
  getMe(): Promise<AccessMe>;
  listUsers(params: {
    q?: string;
    page?: number;
    size?: number;
  }): Promise<Page<AdminUser>>;
  getUser(userId: string): Promise<AdminUser>;
  listRoles(): Promise<Role[]>;
  roleAssignees(roleId: number): Promise<RoleBinding[]>;
  grantRole(body: GrantRoleBody): Promise<RoleBinding>;
  revokeRole(id: number): Promise<void>;
  listIdpGroups(): Promise<IdpGroupsResponse>;
  bindGroup(body: BindGroupBody): Promise<RoleBinding>;
  unbindGroup(id: number): Promise<void>;
  confirmGroupBinding(id: number): Promise<void>;
  createRole(body: CreateRoleBody): Promise<Role>;
  updateRole(id: number, body: UpdateRoleBody): Promise<Role>;
  deleteRole(id: number): Promise<void>;
}
