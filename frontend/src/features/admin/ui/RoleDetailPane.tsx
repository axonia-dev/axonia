import { AnimatedCount } from "@/components/ui/AnimatedCount";
import type { Role } from "@/features/admin/domain/admin-user";
import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";
import { isEditable } from "@/features/admin/domain/role-editability";
import {
  differingPermissions,
  heldCount,
  isReadOnly,
} from "@/features/admin/domain/role-reach";
import { CeilingLine } from "@/features/admin/ui/CeilingLine";
import { CompareBar } from "@/features/admin/ui/CompareBar";
import {
  CompareColumnHeads,
  DifferencesRemainder,
} from "@/features/admin/ui/CompareColumn";
import {
  heldIn,
  PermissionGroupSection,
} from "@/features/admin/ui/PermissionGroupSection";
import { RoleActionsMenu } from "@/features/admin/ui/RoleActionsMenu";
import { SystemBadge } from "@/features/admin/ui/SystemBadge";
import { useGroupCollapse } from "@/features/admin/ui/use-group-collapse";
import { cn } from "@/lib/cn";

export interface RoleDetailPaneProps {
  role: Role;
  compareRole: Role | null;
  roles: Role[];
  catalog: PermissionCatalog;
  canWrite: boolean;
  heldPermissions: ReadonlySet<string>;
  differencesOnly: boolean;
  matchingCodes: Set<string>;
  /** The raw search text, so a matched permission row can mark the run that matched. */
  query: string;
  onCompare: (roleId: number | null) => void;
  onDifferencesOnly: (on: boolean) => void;
  onTogglePermission: (code: string) => void;
  onRename: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}

/** The trailing sentence under the role's name. Extracted so the nullish handling stays out
 *  of the component, where it counts against the complexity cap. */
function trailingFact(
  role: Role,
  catalog: PermissionCatalog,
  compareRole: Role | null,
): string {
  const groups = catalog.groups;
  if (compareRole !== null) {
    const n = differingPermissions(role, compareRole, catalog).length;
    return `${n} ${n === 1 ? "differs" : "differ"} from ${compareRole.name}`;
  }
  const touched = groups.filter((g) => heldIn(role, g.permissions) > 0).length;
  return `${touched} of ${groups.length} domains touched`;
}

/** The role's identity and the acts available on it. Held apart from the pane so the
 *  pane's own body stays under the complexity cap. Takes the pane's whole props because the
 *  compare control it hosts is driven by all of them. */
function PaneHeader(props: Readonly<RoleDetailPaneProps>) {
  const {
    role,
    roles,
    compareRole,
    catalog,
    differencesOnly,
    canWrite,
    onCompare,
    onDifferencesOnly,
    onRename,
    onDuplicate,
    onDelete,
  } = props;
  return (
    // The controls sit beside the identity from `sm` up and under it below that: on a phone
    // the two of them take so much of the row that the name and description are left a column
    // barely one word wide.
    <div className="flex flex-col gap-2.5 sm:flex-row sm:items-start">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="font-display text-foreground text-lg font-semibold">
            {role.name}
          </h2>
          {role.system && <SystemBadge />}
          {isReadOnly(role, catalog) && (
            <span className="border-secondary/40 bg-secondary/15 text-secondary rounded-full border px-2 py-0.5 text-[10px] font-semibold">
              Read-only
            </span>
          )}
        </div>
        <p className="text-muted-foreground mt-1 text-sm">
          {role.description ?? "No description."}
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-2.5">
        <CompareBar
          role={role}
          roles={roles}
          compareRole={compareRole}
          differencesOnly={differencesOnly}
          onCompare={onCompare}
          onDifferencesOnly={onDifferencesOnly}
        />
        <RoleActionsMenu
          role={role}
          canWrite={canWrite}
          onRename={onRename}
          onDuplicate={onDuplicate}
          onDelete={onDelete}
        />
      </div>
    </div>
  );
}

/** The one control over every group at once. Its own row directly above the groups rather
 *  than the end of the tally line, where whether it lands beside the tallies or wraps under
 *  them would be decided by the viewport. Bordered rather than bare text, which said nothing
 *  about being clickable until hover. No icon: the two chevrons that mean fold resolve into a
 *  dismiss cross at this size. */
function CollapseAllButton({
  allCollapsed,
  onPress,
}: Readonly<{ allCollapsed: boolean; onPress: () => void }>) {
  return (
    <button
      type="button"
      onClick={onPress}
      className="text-muted-foreground hover:text-foreground hover:border-border-strong hover:bg-surface-sunken focus-visible:ring-focus-ring border-border mb-2 cursor-pointer self-end rounded-md border px-2.5 py-1 text-xs font-medium transition-colors outline-none focus-visible:ring-2"
    >
      {allCollapsed ? "Expand all" : "Collapse all"}
    </button>
  );
}

export function RoleDetailPane(props: Readonly<RoleDetailPaneProps>) {
  const {
    role,
    compareRole,
    catalog,
    canWrite,
    heldPermissions,
    differencesOnly,
    matchingCodes,
    query,
    onDifferencesOnly,
    onTogglePermission,
  } = props;
  const editable = isEditable(role, canWrite);
  const total = catalog.entries.length;
  const grantedCount = heldCount(role, catalog);
  const groups = catalog.groups;
  const collapse = useGroupCollapse(groups, matchingCodes);

  return (
    <div className="flex min-w-0 flex-col gap-4 p-5">
      <PaneHeader {...props} />

      <p className="text-muted-foreground flex flex-wrap items-center gap-3 text-sm">
        <span>
          <AnimatedCount
            value={grantedCount}
            className="text-primary-strong font-semibold"
          />{" "}
          of {total} permissions
        </span>
        <span className="bg-surface-sunken h-1.5 w-40 overflow-hidden rounded-full">
          {/* Solid: a gradient anchored to the bar's own width changes hue with the value, and
              its violet end is the colour this page uses for the compared role. */}
          <span
            className="bg-primary block h-full rounded-full motion-safe:transition-[width] motion-safe:duration-300"
            style={{
              width: `${total === 0 ? 0 : (grantedCount / total) * 100}%`,
            }}
          />
        </span>
        <span>{trailingFact(role, catalog, compareRole)}</span>
      </p>

      {editable && (
        <CeilingLine catalog={catalog} heldPermissions={heldPermissions} />
      )}

      {compareRole !== null && (
        <CompareColumnHeads role={role} compareRole={compareRole} />
      )}

      <div className={cn("flex flex-col", compareRole !== null && "-mt-2")}>
        <CollapseAllButton
          allCollapsed={collapse.allCollapsed}
          onPress={collapse.toggleAll}
        />
        {groups.map((group) => (
          <PermissionGroupSection
            key={group.key}
            group={group}
            role={role}
            compareRole={compareRole}
            editable={editable}
            heldPermissions={heldPermissions}
            differencesOnly={differencesOnly}
            matchingCodes={matchingCodes}
            query={query}
            isExpanded={collapse.isExpanded(group)}
            onExpandedChange={(expanded) => {
              collapse.setExpanded(group.key, expanded);
            }}
            onToggle={onTogglePermission}
          />
        ))}
        {differencesOnly && compareRole !== null && (
          <DifferencesRemainder
            role={role}
            compareRole={compareRole}
            catalog={catalog}
            onShowAll={() => onDifferencesOnly(false)}
          />
        )}
      </div>
    </div>
  );
}
