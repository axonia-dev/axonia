import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

import type { Role } from "@/features/admin/domain/admin-user";
import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";
import {
  matchingPermissionCodes,
  matchRoles,
  type RoleMatch,
} from "@/features/admin/domain/match-roles";
import { RoleDetailPane } from "@/features/admin/ui/RoleDetailPane";
import { RoleRail } from "@/features/admin/ui/RoleRail";

/** Everything the workbench reads, derived in one place: the component that renders it is
 *  already at the complexity cap and every one of these lines carries a nullish branch. */
interface WorkbenchModel {
  catalog: PermissionCatalog;
  all: Role[];
  q: string;
  matches: RoleMatch[];
  matchingCodes: Set<string>;
  selected: Role | null;
  compareRole: Role | null;
}

/** The pinned role, or null. A role never compares against itself: opening the pinned role
 *  drops the pin rather than drawing a column identical to the one beside it. */
function resolveCompare(
  roles: Role[],
  raw: string | null,
  open: Role | null,
): Role | null {
  const pinned = roles.find((r) => String(r.id) === raw) ?? null;
  if (pinned === null || open === null) return pinned;
  return pinned.id === open.id ? null : pinned;
}

export function workbenchModel(
  roles: Role[] | undefined,
  catalog: PermissionCatalog,
  params: URLSearchParams,
  roleId: string | undefined,
): WorkbenchModel {
  const all = roles ?? [];
  const q = params.get("q") ?? "";
  const selected = all.find((r) => String(r.id) === roleId) ?? null;
  return {
    catalog,
    all,
    q,
    matches: matchRoles(all, q, catalog),
    matchingCodes: matchingPermissionCodes(q, catalog),
    selected,
    compareRole: resolveCompare(all, params.get("compare"), selected),
  };
}

/* The shell of the mockup: a 288px rail beside the pane. Neither column is bounded and
 * neither scrolls: the settings shell's own <main> is the page's one scroll container, and a
 * second one inside it puts two scrollbars on screen for the same gesture. The rail stays
 * readable through that scroll by sticking, which needs no clipped ancestor here. The base
 * `grid-cols-1` is inert under `flex`, and it states the phone-width track the grid-tracks
 * guard requires of every breakpoint-scoped column count. */
const WORKBENCH_SHELL =
  "border-border bg-surface flex grid-cols-1 flex-col rounded-xl border shadow-sm md:grid md:grid-cols-[18rem_minmax(0,1fr)]";

interface WorkbenchProps {
  model: WorkbenchModel;
  isDesktop: boolean;
  rolesBase: string;
  search: string;
  canWrite: boolean;
  /** The caller's own permission codes: the ceiling a row may not move past. */
  heldPermissions: ReadonlySet<string>;
  differencesOnly: boolean;
  onDifferencesOnly: (on: boolean) => void;
  setParam: (key: string, value: string | null) => void;
  onTogglePermission: (code: string) => void;
  onRename: (role: Role) => void;
  onDuplicate: (role: Role) => void;
  onDelete: (role: Role) => void;
}

/** The way back to the rail below the breakpoint, where the rail and the pane never share a
 *  screen. It carries the query string, so a search or a pinned comparison survives it. */
function BackToRail({ to }: Readonly<{ to: string }>) {
  return (
    <Link
      to={to}
      className="text-muted-foreground hover:text-foreground focus-visible:ring-focus-ring border-border flex items-center gap-2 border-b px-5 py-3 text-sm outline-none focus-visible:ring-2"
    >
      <ArrowLeft size={16} aria-hidden />
      All roles
    </Link>
  );
}

/** The pane column above the breakpoint with no role open, which a search matching nothing
 *  leaves behind. The rail already states why it is empty, so this states only the fact.
 *  Below the breakpoint the rail is the whole page and there is no second column to fill. */
function EmptyPane() {
  return <p className="text-muted-foreground p-5 text-sm">No role is open.</p>;
}

export function RoleWorkbench({
  model,
  isDesktop,
  rolesBase,
  search,
  canWrite,
  heldPermissions,
  differencesOnly,
  onDifferencesOnly,
  setParam,
  onTogglePermission,
  onRename,
  onDuplicate,
  onDelete,
}: Readonly<WorkbenchProps>) {
  const { selected, compareRole } = model;
  // Above the breakpoint both columns are on screen. Below it the rail is the whole page
  // until a role is opened, and then the pane is.
  const showRail = isDesktop || selected === null;
  return (
    <div className={WORKBENCH_SHELL}>
      {showRail && (
        <RoleRail
          matches={model.matches}
          totalRoles={model.all.length}
          q={model.q}
          onQ={(next) => setParam("q", next)}
          catalog={model.catalog}
          selectedId={selected === null ? null : selected.id}
          rolesBase={rolesBase}
          search={search}
        />
      )}
      {selected === null ? (
        isDesktop && <EmptyPane />
      ) : (
        <div className="flex min-w-0 flex-col">
          {!isDesktop && <BackToRail to={`${rolesBase}${search}`} />}
          <RoleDetailPane
            role={selected}
            compareRole={compareRole}
            roles={model.all}
            catalog={model.catalog}
            canWrite={canWrite}
            heldPermissions={heldPermissions}
            differencesOnly={differencesOnly && compareRole !== null}
            matchingCodes={model.matchingCodes}
            query={model.q}
            onCompare={(id) => {
              // Pinning, swapping or dropping a comparison asks a new question, so the
              // differences-only answer to the last one does not carry over.
              onDifferencesOnly(false);
              setParam("compare", id === null ? null : String(id));
            }}
            onDifferencesOnly={onDifferencesOnly}
            onTogglePermission={onTogglePermission}
            onRename={() => onRename(selected)}
            onDuplicate={() => onDuplicate(selected)}
            onDelete={() => onDelete(selected)}
          />
        </div>
      )}
    </div>
  );
}
