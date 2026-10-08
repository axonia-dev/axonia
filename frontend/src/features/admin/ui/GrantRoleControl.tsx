import { useState } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { useGrantRole } from "@/features/admin/api/use-grant-role";
import { useRoles } from "@/features/admin/api/use-roles";
import { roleOptions } from "@/features/admin/ui/role-options";
import { useCeiling } from "@/features/admin/ui/use-ceiling";
import { ApiError } from "@/lib/api-client";
import { failedToLoad, stillLoading } from "@/lib/failed-to-load";
import { useAnsweredAfterFailing } from "@/lib/use-answered-after-failing";

interface GrantRoleControlProps {
  userId: string;
}

export function GrantRoleControl({ userId }: Readonly<GrantRoleControlProps>) {
  const roles = useRoles();
  const rolesFailed = failedToLoad(roles);
  const rolesRecovered = useAnsweredAfterFailing(rolesFailed);
  const ceiling = useCeiling();
  const grantRole = useGrantRole();
  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const options = roleOptions(roles.data ?? [], ceiling);

  function handleGrant() {
    if (selectedRoleId === null) return;
    setError(null);
    grantRole.mutate(
      {
        subjectType: "USER",
        subjectId: userId,
        roleId: selectedRoleId,
      },
      {
        onSuccess: () => setSelectedRoleId(null),
        onError: (err) =>
          setError(
            err instanceof ApiError ? err.message : "Failed to grant role.",
          ),
      },
    );
  }

  return (
    <section className="border-border flex flex-col gap-3 border-t pt-4">
      <h2 className="text-foreground text-sm font-semibold">Grant a role</h2>
      {rolesFailed ? (
        <LoadFailed inline message="Could not load the roles." query={roles} />
      ) : (
        <Select
          label="Role"
          value={selectedRoleId}
          options={options}
          onChange={setSelectedRoleId}
          placeholder="Choose a role…"
          isLoading={stillLoading(roles)}
          takesFocus={rolesRecovered}
        />
      )}
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
      <Button
        onPress={handleGrant}
        isDisabled={selectedRoleId === null || grantRole.isPending}
      >
        Grant role
      </Button>
    </section>
  );
}
