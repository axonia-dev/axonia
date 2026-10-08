import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";
import type {
  CreateRoleBody,
  UpdateRoleBody,
} from "@/features/admin/domain/admin-gateway";
import type { Role } from "@/features/admin/domain/admin-user";

const gateway = httpAdminGateway;

export function useCreateRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateRoleBody) => gateway.createRole(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.roles() });
    },
  });
}

interface UpdateArgs {
  id: number;
  body: UpdateRoleBody;
}

export function useUpdateRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: UpdateArgs) => gateway.updateRole(id, body),
    onMutate: async ({ id, body }: UpdateArgs) => {
      await queryClient.cancelQueries({ queryKey: adminKeys.roles() });
      const previous = queryClient.getQueryData<Role[]>(adminKeys.roles());
      if (previous !== undefined) {
        queryClient.setQueryData<Role[]>(
          adminKeys.roles(),
          previous.map((r) =>
            r.id === id
              ? {
                  ...r,
                  name: body.name,
                  description: body.description,
                  permissions: body.permissions,
                }
              : r,
          ),
        );
      }
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(adminKeys.roles(), context.previous);
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.roles() });
    },
  });
}

export function useDeleteRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gateway.deleteRole(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.roles() });
    },
  });
}
