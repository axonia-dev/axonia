import { type ReactNode, useId, useState } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import { PageHeader } from "@/components/patterns/PageHeader";
import { PageLoadFailed } from "@/components/patterns/PageLoadFailed";
import { Button } from "@/components/ui/Button";
import { type ComboBoxOption } from "@/components/ui/ComboBox";
import { StatusChip } from "@/components/ui/StatusChip";
import { useIdpGroups } from "@/features/admin/api/use-idp-groups";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useRoles } from "@/features/admin/api/use-roles";
import { useUnbindGroup } from "@/features/admin/api/use-unbind-group";
import type {
  IdpGroup,
  Role,
  RoleBinding,
} from "@/features/admin/domain/admin-user";
import { bindingReach } from "@/features/admin/domain/ceiling";
import { AddBindingPanel } from "@/features/admin/ui/AddBindingPanel";
import { REACHES_BEYOND } from "@/features/admin/ui/ceiling-copy";
import { SOURCE_ICON } from "@/features/admin/ui/group-source-icon";
import { SOURCE_LABEL } from "@/features/admin/ui/group-source-label";
import { HeldGroupNotice } from "@/features/admin/ui/HeldGroupNotice";
import { ProvisioningScopeWarning } from "@/features/admin/ui/ProvisioningScopeWarning";
import { RequirePermission } from "@/features/admin/ui/RequirePermission";
import { useCeiling } from "@/features/admin/ui/use-ceiling";
import { ApiError } from "@/lib/api-client";
import { failedToLoad } from "@/lib/failed-to-load";

interface BindingRowProps {
  binding: RoleBinding;
  roleName: string;
  outOfReach: boolean;
  roleResolved: boolean;
  canWrite: boolean;
  isPending: boolean;
  onUnbind: (id: number) => void;
}

function BindingRow({
  binding,
  roleName,
  outOfReach,
  roleResolved,
  canWrite,
  isPending,
  onUnbind,
}: Readonly<BindingRowProps>) {
  const reasonId = useId();
  // One predicate for the paragraph and for the association that points at it: an
  // aria-describedby naming an id nothing rendered describes the button with nothing.
  const showReason = canWrite && outOfReach && roleResolved;
  return (
    <li className="border-border bg-surface flex flex-col gap-1.5 rounded-md border px-3 py-2">
      <div className="flex items-center justify-between gap-2">
        <div className="flex flex-col gap-0.5">
          <span className="text-foreground text-sm font-medium">
            {roleName}
          </span>
        </div>
        {canWrite && (
          <Button
            variant="danger"
            size="sm"
            isDisabled={isPending || outOfReach}
            {...(showReason ? { "aria-describedby": reasonId } : {})}
            onPress={() => onUnbind(binding.id)}
          >
            Unbind
          </Button>
        )}
      </div>
      {showReason && (
        <p id={reasonId} className="text-muted-foreground text-xs">
          {REACHES_BEYOND}
        </p>
      )}
    </li>
  );
}

interface GroupCardProps {
  group: IdpGroup;
  roles: Role[];
  canWrite: boolean;
  sourceLabel: string;
  sourceIcon: ReactNode;
}

function GroupCard({
  group,
  roles,
  canWrite,
  sourceLabel,
  sourceIcon,
}: Readonly<GroupCardProps>) {
  const ceiling = useCeiling();
  const roleMap = new Map(roles.map((r) => [r.id, r.name]));
  const unbindGroup = useUnbindGroup();
  const [unbindError, setUnbindError] = useState<string | null>(null);

  function handleUnbind(id: number) {
    setUnbindError(null);
    unbindGroup.mutate(id, {
      onError: (err) => {
        setUnbindError(
          err instanceof ApiError ? err.message : "Failed to unbind group.",
        );
      },
    });
  }

  return (
    <li className="border-border bg-surface hover:border-border-strong flex flex-col gap-4 rounded-md border px-4 py-4 transition-shadow hover:shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-foreground font-semibold">{group.displayName}</h2>
        <div className="flex flex-none items-center gap-2">
          {group.awaitingConfirmation && (
            <StatusChip tone="warning" label="Awaiting confirmation" />
          )}
          <StatusChip tone="neutral" label={sourceLabel} icon={sourceIcon} />
        </div>
      </div>

      {group.awaitingConfirmation && (
        <HeldGroupNotice
          bindings={group.bindings}
          roles={roles}
          canWrite={canWrite}
        />
      )}

      <section className="flex flex-col gap-2">
        <h3 className="text-foreground text-sm font-semibold">
          Current bindings
        </h3>
        {group.bindings.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            No role is bound to this group yet.
          </p>
        ) : (
          <ul
            className="flex flex-col gap-2"
            aria-label={`bindings for ${group.displayName}`}
          >
            {group.bindings.map((b) => {
              const reach = bindingReach(b.roleId, roles, ceiling);
              return (
                <BindingRow
                  key={b.id}
                  binding={b}
                  roleName={
                    roleMap.get(b.roleId) ?? b.subjectLabel ?? String(b.roleId)
                  }
                  outOfReach={reach.outOfReach}
                  roleResolved={reach.roleResolved}
                  canWrite={canWrite}
                  isPending={unbindGroup.isPending}
                  onUnbind={handleUnbind}
                />
              );
            })}
          </ul>
        )}
      </section>

      {unbindError !== null && (
        <p role="alert" className="text-danger text-sm">
          {unbindError}
        </p>
      )}
    </li>
  );
}

function toSuggestion(group: IdpGroup): ComboBoxOption {
  const base = { id: group.groupId, label: group.displayName };
  return group.groupId === group.displayName
    ? base
    : { ...base, hint: group.groupId };
}

function GroupAccessPageContent() {
  const groups = useIdpGroups();
  const { data, isPending } = groups;
  const roles = useRoles();
  const { can } = usePermissions();
  const canWrite = can("role:assign");

  if (failedToLoad(groups)) {
    return (
      <PageLoadFailed
        title="Group access"
        message="Could not load the directory groups."
        query={groups}
      />
    );
  }
  if (isPending || data === undefined) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }

  const suggestions = data.groups
    .filter((g) => g.inDirectory)
    .map(toSuggestion);
  // A held group is listed even with no bindings left: it is a state an administrator has to be
  // able to see, and a group that drops its last binding while held would otherwise vanish from
  // the page still held.
  const bound = data.groups.filter(
    (g) => g.bindings.length > 0 || g.awaitingConfirmation,
  );
  const roleList = roles.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Group access" />

      <ProvisioningScopeWarning source={data.source} />

      {canWrite && (
        <AddBindingPanel
          source={data.source}
          suggestions={suggestions}
          roles={roles}
        />
      )}

      {bound.length === 0 ? (
        <EmptyState
          title="No bindings yet"
          description="Bind a group above to grant a role."
        />
      ) : (
        <ul className="flex flex-col gap-4">
          {bound.map((g) => (
            <GroupCard
              key={g.groupId}
              group={g}
              roles={roleList}
              canWrite={canWrite}
              sourceLabel={g.inDirectory ? SOURCE_LABEL[data.source] : "Manual"}
              sourceIcon={
                g.inDirectory ? SOURCE_ICON[data.source] : SOURCE_ICON.NONE
              }
            />
          ))}
        </ul>
      )}
    </div>
  );
}

export function GroupAccessPage() {
  return (
    <RequirePermission permission="role:read" title="Group access">
      <GroupAccessPageContent />
    </RequirePermission>
  );
}
