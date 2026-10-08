import type { Role } from "@/features/admin/domain/admin-user";
import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";
import { differingPermissions } from "@/features/admin/domain/role-reach";

/** The two column labels, drawn only while comparing. Without them the ghost marks are a
 *  column of symbols belonging to nothing named. Fades in with the rest of the ghost column,
 *  rather than snapping into place, when a compare is first pinned. */
export function CompareColumnHeads({
  role,
  compareRole,
}: Readonly<{ role: Role; compareRole: Role }>) {
  return (
    <div className="border-border flex items-center gap-3.5 border-b pb-1.5 motion-safe:transition-opacity motion-safe:duration-300 motion-safe:starting:opacity-0">
      <span className="flex-1" />
      <span
        data-testid="compare-head"
        className="text-secondary w-16 text-center text-[10px] font-semibold tracking-wide uppercase"
      >
        {compareRole.name}
      </span>
      <span
        data-testid="compare-head"
        className="text-primary-strong w-16 text-center text-[10px] font-semibold tracking-wide uppercase"
      >
        {role.name}
      </span>
    </div>
  );
}

/** One line standing in for content the pane collapsed away, with the single control that
 *  brings it back. */
function CollapsedNote({
  text,
  actionLabel,
  onAction,
}: Readonly<{ text: string; actionLabel: string; onAction: () => void }>) {
  return (
    <p className="text-muted-foreground py-3 text-sm">
      {text}{" "}
      <button
        type="button"
        onClick={onAction}
        className="text-primary-strong focus-visible:ring-focus-ring cursor-pointer rounded outline-none focus-visible:ring-2"
      >
        {actionLabel}
      </button>
    </p>
  );
}

/** The applicable permissions the two roles agree on, once differences-only strips the rest
 *  away. Stated rather than left silent: the page says what it removed. */
function agreeingCount(
  role: Role,
  compareRole: Role,
  catalog: PermissionCatalog,
): number {
  return (
    catalog.entries.length -
    differingPermissions(role, compareRole, catalog).length
  );
}

/** The line differences-only ends on: what it stripped away, and the way back. Renders
 *  nothing when the two roles agree on nothing, since there is then no remainder to name. */
export function DifferencesRemainder({
  role,
  compareRole,
  catalog,
  onShowAll,
}: Readonly<{
  role: Role;
  compareRole: Role;
  catalog: PermissionCatalog;
  onShowAll: () => void;
}>) {
  const n = agreeingCount(role, compareRole, catalog);
  if (n === 0) return null;
  const noun = n === 1 ? "permission" : "permissions";
  const verb = n === 1 ? "is" : "are";
  return (
    <CollapsedNote
      text={`The other ${n} ${noun} ${verb} identical in both roles.`}
      actionLabel="Show them"
      onAction={onShowAll}
    />
  );
}
