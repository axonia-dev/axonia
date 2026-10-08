import type { usePermissions } from "@/features/admin/api/use-permissions";

type Permissions = ReturnType<typeof usePermissions>;

export function permissionState(
  can: Permissions["can"],
  state: Partial<Omit<Permissions, "can">> = {},
): Permissions {
  return {
    can,
    isLoading: false,
    isError: false,
    retry: { isFetching: false, refetch: () => Promise.resolve() },
    ...state,
  };
}
