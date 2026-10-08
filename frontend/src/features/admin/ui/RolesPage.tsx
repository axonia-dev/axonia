import { Plus } from "lucide-react";
import { useState } from "react";
import {
  Navigate,
  useLocation,
  useParams,
  useSearchParams,
} from "react-router-dom";

import { PageHeader } from "@/components/patterns/PageHeader";
import { PageLoadFailed } from "@/components/patterns/PageLoadFailed";
import { Button } from "@/components/ui/Button";
import { notifyError } from "@/components/ui/toast";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useCreateRole,
  useUpdateRole,
} from "@/features/admin/api/use-role-mutations";
import { useRoles } from "@/features/admin/api/use-roles";
import type { Role } from "@/features/admin/domain/admin-user";
import { togglePermission } from "@/features/admin/domain/permission-catalog";
import { DeleteRoleDialog } from "@/features/admin/ui/DeleteRoleDialog";
import { RequirePermission } from "@/features/admin/ui/RequirePermission";
import { RoleEditorDialog } from "@/features/admin/ui/RoleEditorDialog";
import {
  RoleWorkbench,
  workbenchModel,
} from "@/features/admin/ui/RoleWorkbench";
import { useApplicablePermissions } from "@/features/admin/ui/use-permission-catalog";
import { useRoleWriteQueue } from "@/features/admin/ui/use-role-write-queue";
import { failedToLoad } from "@/lib/failed-to-load";
import { useMediaQuery } from "@/lib/use-media-query";
import { ROLES_PATH } from "@/routes/settings-routes.catalogue";

type DialogState =
  { mode: "closed" } | { mode: "edit"; role: Role } | { mode: "create" };

function RolesPageContent({ base }: Readonly<{ base: string }>) {
  const { data: roles, isPending } = useRoles();
  const { data: organization, isPending: orgPending } =
    useCurrentOrganization();
  const { can } = usePermissions();
  const held = useHeldPermissions();
  const updateRole = useUpdateRole();
  const createRole = useCreateRole();
  const { enqueue } = useRoleWriteQueue();
  const [searchParams, setSearchParams] = useSearchParams();
  const { roleId } = useParams();
  const { search } = useLocation();
  const isDesktop = useMediaQuery("(min-width: 48rem)");
  const [dialog, setDialog] = useState<DialogState>({ mode: "closed" });
  const [deleteTarget, setDeleteTarget] = useState<Role | null>(null);
  const [differencesOnly, setDifferencesOnly] = useState(false);

  const catalog = useApplicablePermissions(organization?.kind);
  const model = workbenchModel(roles, catalog, searchParams, roleId);
  const rolesBase = `${base}/${ROLES_PATH}`;

  function setParam(key: string, value: string | null) {
    const next = new URLSearchParams(searchParams);
    if (value === null || value === "") {
      next.delete(key);
    } else {
      next.set(key, value);
    }
    setSearchParams(next, { replace: true });
  }

  function toggle(code: string) {
    const role = model.selected;
    if (role === null) return;
    // The queue serializes the writes, so they never overlap on the network. Each body is
    // built here, at click time, which leaves two gaps. Two toggles dispatched inside one
    // frame both read the pre-toggle role, and the second write drops the first one's change.
    // And a refused write is rolled back in the cache while a body already queued behind it
    // still carries the optimistic grants, so that one re-applies what was just refused.
    const body = {
      name: role.name,
      description: role.description,
      permissions: togglePermission(role.permissions, code),
    };
    const id = role.id;
    enqueue(async () => {
      try {
        await updateRole.mutateAsync({ id, body });
      } catch (cause) {
        notifyError(
          cause instanceof Error ? cause.message : "Could not save the role.",
        );
      }
    });
  }

  function duplicate(role: Role) {
    createRole.mutate({
      name: `${role.name} (copy)`,
      description: role.description,
      permissions: role.permissions,
    });
  }

  // The heading is held through the load, so the page title does not pop in under the reader.
  if (isPending || orgPending) {
    return (
      <div className="flex flex-col gap-6">
        <PageHeader title="Roles" />
        <p className="text-muted-foreground text-sm">Loading…</p>
      </div>
    );
  }
  // A wide viewport with no role open falls to the first role rather than an empty pane. The
  // condition is what is wrong, not how the URL looks, so it also recovers a /roles/:roleId
  // the role behind it no longer answers: a deleted role, or a stale shared link. Below the
  // breakpoint the rail is the whole page and picking a role is a real navigation.
  const first = model.matches[0];
  if (isDesktop && model.selected === null && first !== undefined) {
    return <Navigate replace to={`${rolesBase}/${first.role.id}${search}`} />;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Roles"
        action={
          can("role:write") && (
            <Button onPress={() => setDialog({ mode: "create" })}>
              <Plus size={16} aria-hidden /> New role
            </Button>
          )
        }
      />
      <RoleWorkbench
        model={model}
        isDesktop={isDesktop}
        rolesBase={rolesBase}
        search={search}
        canWrite={can("role:write")}
        heldPermissions={held}
        differencesOnly={differencesOnly}
        onDifferencesOnly={setDifferencesOnly}
        setParam={setParam}
        onTogglePermission={toggle}
        onRename={(role) => setDialog({ mode: "edit", role })}
        onDuplicate={duplicate}
        onDelete={setDeleteTarget}
      />
      <DeleteRoleDialog
        role={deleteTarget}
        isOpen={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
      />
      <RoleEditorDialog
        editRole={dialog.mode === "edit" ? dialog.role : null}
        mode={dialog.mode === "create" ? "create" : "edit"}
        isOpen={dialog.mode !== "closed"}
        onOpenChange={(open) => {
          if (!open) setDialog({ mode: "closed" });
        }}
      />
    </div>
  );
}

/** An unread role list says so under the heading, rather than reading as no role at all. */
function RolesRead({ base }: Readonly<{ base: string }>) {
  const roles = useRoles();
  if (failedToLoad(roles)) {
    return (
      <PageLoadFailed
        title="Roles"
        message="Could not load the roles."
        query={roles}
      />
    );
  }
  return <RolesPageContent base={base} />;
}

export function RolesPage({ base }: Readonly<{ base: string }>) {
  return (
    <RequirePermission permission="role:read" title="Roles">
      <RolesRead base={base} />
    </RequirePermission>
  );
}
