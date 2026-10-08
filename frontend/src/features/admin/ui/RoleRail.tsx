import { Link } from "react-router-dom";

import { SearchField } from "@/components/ui/SearchField";
import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";
import type { RoleMatch } from "@/features/admin/domain/match-roles";
import { heldCount, roleReach } from "@/features/admin/domain/role-reach";
import { ReachGlyph } from "@/features/admin/ui/ReachGlyph";
import { SystemBadge } from "@/features/admin/ui/SystemBadge";
import { cn } from "@/lib/cn";

export interface RoleRailProps {
  matches: RoleMatch[];
  /** Every role before the search, so the rail can say what it hid. */
  totalRoles: number;
  q: string;
  onQ: (q: string) => void;
  catalog: PermissionCatalog;
  selectedId: number | null;
  /** Absolute path of the roles page, so each row is a real link. */
  rolesBase: string;
  /** The current query string, carried onto every row so a shared diff or search survives
   *  a click onto another role. */
  search: string;
}

function RailRow({
  match,
  catalog,
  total,
  selected,
  rolesBase,
  search,
}: Readonly<{
  match: RoleMatch;
  catalog: PermissionCatalog;
  total: number;
  selected: boolean;
  rolesBase: string;
  search: string;
}>) {
  const { role, grantsMatching } = match;
  const held = heldCount(role, catalog);
  return (
    <li>
      <Link
        to={`${rolesBase}/${role.id}${search}`}
        aria-label={`${role.name}, ${role.system ? "system" : "custom"} role, ${held} of ${total} permissions`}
        aria-current={selected ? "page" : undefined}
        className={cn(
          "focus-visible:ring-focus-ring flex items-center gap-2.5 rounded-md border px-2.5 py-2 outline-none focus-visible:ring-2",
          selected
            ? "border-primary/40 bg-primary/[.13] text-primary-strong"
            : "hover:bg-surface-sunken border-transparent",
        )}
      >
        <ReachGlyph levels={roleReach(role, catalog)} selected={selected} />
        {/* The name is the row's subject, and it is the row's only flexible item: a match
            note beside it takes its width out of the name, which truncated `SSO
            Administrator` to `SSO Administra…`. Stacked under it, the note costs the name
            nothing. */}
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-sm font-medium">{role.name}</span>
          {grantsMatching > 0 && (
            <span className="text-primary-strong text-[10px] whitespace-nowrap">
              {grantsMatching === 1 ? "1 match" : `${grantsMatching} matches`}
            </span>
          )}
        </span>
        {role.system && <SystemBadge />}
        <span className="text-muted-foreground text-xs tabular-nums">
          {held}
        </span>
      </Link>
    </li>
  );
}

function RailGroup({
  heading,
  matches,
  rowProps,
}: Readonly<{
  heading: string;
  matches: RoleMatch[];
  rowProps: Omit<Parameters<typeof RailRow>[0], "match" | "selected"> & {
    selectedId: number | null;
  };
}>) {
  if (matches.length === 0) return null;
  const { selectedId, ...rest } = rowProps;
  return (
    <>
      <li className="text-muted-foreground px-2 pt-2.5 pb-1 text-[10px] font-semibold tracking-wider uppercase">
        {heading} &middot; {matches.length}
      </li>
      {matches.map((match) => (
        <RailRow
          key={match.role.id}
          match={match}
          selected={match.role.id === selectedId}
          {...rest}
        />
      ))}
    </>
  );
}

export function RoleRail({
  matches,
  totalRoles,
  q,
  onQ,
  catalog,
  selectedId,
  rolesBase,
  search,
}: Readonly<RoleRailProps>) {
  const total = catalog.entries.length;
  const grantMatches = matches.filter((m) => m.grantsMatching > 0).length;
  const rowProps = { catalog, total, rolesBase, search, selectedId };
  return (
    // The border sits on the grid cell, so the rule between the two columns runs the pane's
    // full height. The rail itself sticks inside that cell: the page's one scroll container
    // is the settings shell's <main>, and without this the rail would scroll off and leave no
    // way to another role but scrolling back up.
    //
    // `max-h` and not `h`: a height made the rail that tall whatever it held, so its bottom edge
    // landed in the middle of a column as tall as the permission pane, and whatever sat on that
    // edge moved to a different place at every scroll offset. Sized to its content, the rail
    // simply ends where the list ends.
    //
    // The cap only bites on a list long enough to fill the screen, and then the rail scrolls
    // inside itself. It subtracts the desktop top bar above <main> plus the shell's bottom
    // padding, rounded up: a sticky box taller than the room left in its cell is pushed up as
    // the cell's bottom arrives, which clipped the search field at the foot of a long page.
    <div className="border-border border-b md:border-r md:border-b-0">
      <div className="flex flex-col md:sticky md:top-0 md:max-h-[calc(100dvh_-_7rem)] md:overflow-y-auto">
        <div className="border-border border-b p-3">
          <SearchField
            label="Search roles and permissions"
            value={q}
            onChange={onQ}
            placeholder="Search roles and permissions"
          />
        </div>
        {grantMatches > 0 && (
          <p className="text-muted-foreground px-3 pt-2.5 text-xs">
            <b className="text-primary-strong">{grantMatches}</b> of{" "}
            {totalRoles} roles {grantMatches === 1 ? "grants" : "grant"} a
            permission matching &ldquo;{q}&rdquo;.
          </p>
        )}
        {q.trim() !== "" && totalRoles > matches.length && (
          <p className="text-muted-foreground px-3 pt-1 text-xs">
            {totalRoles - matches.length === 1
              ? "1 role grants nothing matching."
              : `${totalRoles - matches.length} roles grant nothing matching.`}
          </p>
        )}
        <ul className="relative flex flex-col gap-1 p-2">
          <RailGroup
            heading="System"
            matches={matches.filter((m) => m.role.system)}
            rowProps={rowProps}
          />
          <RailGroup
            heading="Custom"
            matches={matches.filter((m) => !m.role.system)}
            rowProps={rowProps}
          />
        </ul>
      </div>
    </div>
  );
}
