import { useId, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useRevokeRole } from "@/features/admin/api/use-revoke-role";
import type { AdminUser } from "@/features/admin/domain/admin-user";
import { codesWithinReach } from "@/features/admin/domain/ceiling";
import { permissionIn } from "@/features/admin/domain/edition-permissions";
import { formatLastActive } from "@/features/admin/domain/format-last-active";
import { REACHES_BEYOND } from "@/features/admin/ui/ceiling-copy";
import { GrantRoleControl } from "@/features/admin/ui/GrantRoleControl";
import { RoleChip } from "@/features/admin/ui/RoleChip";
import { useCeiling } from "@/features/admin/ui/use-ceiling";
import { usePermissionCatalog } from "@/features/admin/ui/use-permission-catalog";
import { MemberLabel } from "@/features/identity/ui/MemberLabel";
import { ApiError } from "@/lib/api-client";

interface UserDetailDrawerProps {
  user: AdminUser | null;
  isOpen: boolean;
  onClose: () => void;
}

interface RoleItemProps {
  role: string;
  roleId: number;
  via: string;
  bindingId: number | null;
  canWrite: boolean;
  isPending: boolean;
  onRevoke: (bindingId: number) => void;
  permissions: string[];
  outOfReach: boolean;
}

type RoleItemActionProps = Readonly<
  Omit<RoleItemProps, "role" | "roleId" | "via" | "permissions"> & {
    reasonId: string;
  }
>;

function RoleItemAction({
  bindingId,
  canWrite,
  isPending,
  onRevoke,
  outOfReach,
  reasonId,
}: RoleItemActionProps) {
  if (bindingId !== null && canWrite) {
    return (
      <Button
        variant="danger"
        size="sm"
        isDisabled={isPending || outOfReach}
        {...(outOfReach ? { "aria-describedby": reasonId } : {})}
        onPress={() => onRevoke(bindingId)}
      >
        Revoke
      </Button>
    );
  }
  return null;
}

/** A single effective permission, rendered by its human label + description.
 *  Falls back to the raw code when the catalog doesn't know it (defensive). */
function PermissionLine({ code }: Readonly<{ code: string }>) {
  // Unknown codes (e.g. a permission retired from the catalog) fall back to the
  // raw code in the label slot, keeping the same layout as known permissions.
  const catalog = usePermissionCatalog();
  const def = permissionIn(catalog, code);
  return (
    <li className="flex flex-col">
      <span className="text-foreground text-xs font-medium">
        {def?.label ?? code}
      </span>
      {def !== undefined && (
        <span className="text-muted-foreground text-xs">{def.description}</span>
      )}
    </li>
  );
}

function RoleItem({
  role,
  roleId,
  via,
  bindingId,
  canWrite,
  isPending,
  onRevoke,
  permissions,
  outOfReach,
}: Readonly<RoleItemProps>) {
  const reasonId = useId();
  // Same guard RoleItemAction uses to decide whether a Revoke button renders at all: the
  // reason accompanies a control, so it never appears where there is no button to explain.
  const showReason = outOfReach && bindingId !== null && canWrite;
  return (
    <li className="border-border bg-surface flex flex-col gap-1.5 rounded-md border px-3 py-2">
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
          <RoleChip roleName={role} roleId={roleId} via={via} />
          {via !== "GROUP" && (
            <p className="text-muted-foreground text-xs">
              via {via.toLowerCase()}
            </p>
          )}
          {permissions.length > 0 && (
            <details>
              <summary className="text-muted-foreground cursor-pointer text-xs">
                {permissions.length} permission
                {permissions.length !== 1 ? "s" : ""}
              </summary>
              <ul className="mt-1 flex flex-col gap-1">
                {permissions.map((code) => (
                  <PermissionLine key={code} code={code} />
                ))}
              </ul>
            </details>
          )}
        </div>
        <RoleItemAction
          bindingId={bindingId}
          canWrite={canWrite}
          isPending={isPending}
          onRevoke={onRevoke}
          outOfReach={outOfReach}
          reasonId={reasonId}
        />
      </div>
      {showReason && (
        <p id={reasonId} className="text-muted-foreground text-xs">
          {REACHES_BEYOND}
        </p>
      )}
    </li>
  );
}

export function UserDetailDrawer({
  user,
  isOpen,
  onClose,
}: Readonly<UserDetailDrawerProps>) {
  const { can } = usePermissions();
  const canWrite = can("role:write");
  const ceiling = useCeiling();
  const revokeRole = useRevokeRole();
  const [lastAdminError, setLastAdminError] = useState(false);

  function handleRevoke(bindingId: number) {
    setLastAdminError(false);
    revokeRole.mutate(bindingId, {
      onError: (err) => {
        if (err instanceof ApiError && err.code === "LAST_ADMIN") {
          setLastAdminError(true);
        }
      },
    });
  }

  function handleOpenChange(open: boolean) {
    if (!open) {
      setLastAdminError(false);
      onClose();
    }
  }

  if (user === null) {
    return null;
  }

  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={handleOpenChange}
      title={<MemberLabel name={user.displayName} status={user.memberStatus} />}
    >
      <div className="flex flex-col gap-4">
        {/* Omitted rather than rendered empty: a member with no email would otherwise
            leave a blank paragraph holding the flex gap open under the title. */}
        {user.email !== null && (
          <p className="text-muted-foreground text-sm">{user.email}</p>
        )}
        {user.userId === null && (
          <p className="border-warning/40 bg-warning/10 text-warning rounded-md border px-3 py-2 text-xs">
            Provisioned from the directory, but has not signed in yet. Roles can
            be granted once they complete their first sign-in.
          </p>
        )}
        <dl className="text-muted-foreground flex flex-col gap-1 text-xs">
          <div className="flex gap-2">
            <dt className="font-medium">Member since</dt>
            <dd>
              {user.joinedAt
                ? new Intl.DateTimeFormat("en-GB", {
                    month: "short",
                    year: "numeric",
                  }).format(new Date(user.joinedAt))
                : "-"}
            </dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-medium">Activity</dt>
            <dd>{formatLastActive(user.lastActiveAt).label}</dd>
          </div>
        </dl>

        <section className="flex flex-col gap-2">
          <h2 className="text-foreground text-sm font-semibold">Roles</h2>
          {user.effectiveRoles.length === 0 ? (
            <p className="text-muted-foreground text-sm">No roles assigned.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {user.effectiveRoles.map((er) => (
                <RoleItem
                  key={`${er.role}-${er.via}-${String(er.bindingId)}`}
                  role={er.role}
                  roleId={er.roleId}
                  via={er.via}
                  bindingId={er.bindingId}
                  canWrite={canWrite}
                  isPending={revokeRole.isPending}
                  onRevoke={handleRevoke}
                  permissions={er.permissions}
                  outOfReach={!codesWithinReach(er.permissions, ceiling)}
                />
              ))}
            </ul>
          )}
          {lastAdminError && (
            <p role="alert" className="text-danger text-sm">
              This is the signed-in account&apos;s last admin access, and cannot
              be revoked here.
            </p>
          )}
        </section>

        {canWrite && user.userId !== null && (
          <GrantRoleControl userId={user.userId} />
        )}
      </div>
    </Dialog>
  );
}
