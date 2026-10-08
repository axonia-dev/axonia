import { Lock } from "lucide-react";
import { Fragment, useId } from "react";

import { Switch } from "@/components/ui/Switch";
import type { Role } from "@/features/admin/domain/admin-user";
import { markSegments } from "@/features/admin/domain/mark-match";
import type { PermissionDef } from "@/features/admin/domain/permission-catalog";
import { holds } from "@/features/admin/domain/role-reach";
import { REQUIRES_HOLDING } from "@/features/admin/ui/ceiling-copy";
import { PermissionCell } from "@/features/admin/ui/PermissionCell";
import { cn } from "@/lib/cn";

/** The label, with every run the search matched wrapped in a `mark`. Both the background and
 *  the foreground are set: a bare `mark` is yellow on black in every browser, which is wrong
 *  in dark mode. */
function MarkedLabel({
  label,
  query,
}: Readonly<{ label: string; query: string }>) {
  return (
    <>
      {markSegments(label, query).map((segment, index) => (
        <Fragment key={`${String(index)}:${segment.text}`}>
          {segment.matched ? (
            <mark className="bg-primary/30 text-foreground rounded-[3px] px-0.5">
              {segment.text}
            </mark>
          ) : (
            segment.text
          )}
        </Fragment>
      ))}
    </>
  );
}

interface PermissionRowProps {
  permission: PermissionDef;
  role: Role;
  compareRole: Role | null;
  editable: boolean;
  /** Whether the search matched this permission: the row is tinted and its label marked. */
  matched: boolean;
  /** The raw search text, read only while `matched`. */
  query: string;
  isPulsing: boolean;
  onPulseEnd: () => void;
  onToggle: () => void;
  /** The caller's own permission codes, not the role's: `holds` answers what the role grants,
   *  this answers what the caller may change. */
  heldPermissions: ReadonlySet<string>;
}

/** One permission: its name and description on the left, then the pinned role's read-only
 *  mark, then this role's switch. Both cells are the same fixed width as the column heads, so
 *  every grant reads under the head naming the role that holds it. The row renders the label
 *  itself and points the switch at it, which is what lets the label carry a `mark`. */
export function PermissionRow({
  permission,
  role,
  compareRole,
  editable,
  matched,
  query,
  isPulsing,
  onPulseEnd,
  onToggle,
  heldPermissions,
}: Readonly<PermissionRowProps>) {
  const labelId = useId();
  const descriptionId = useId();
  const reasonId = useId();
  const locked = !heldPermissions.has(permission.code);
  return (
    // No rule per row: twenty-odd hairlines plus a slightly darker one per domain, and neither
    // weight reads as hierarchy. Whitespace separates the rows, leaving the domain rule alone.
    <div
      className={cn(
        "flex items-start gap-3.5 py-2",
        matched && "bg-primary/10 -mx-2 rounded-md px-2",
      )}
    >
      <div className="min-w-0 flex-1">
        <p id={labelId} className="text-foreground text-sm font-medium">
          {matched ? (
            <MarkedLabel label={permission.label} query={query} />
          ) : (
            permission.label
          )}
        </p>
        {/* A step under the label: with the rules gone, weight alone was not enough of a head. */}
        <p id={descriptionId} className="text-muted-foreground text-[13px]">
          {permission.description}
        </p>
        {editable && locked && (
          <p
            id={reasonId}
            className="text-muted-foreground mt-1 flex items-center gap-1.5 text-[11.5px]"
          >
            <Lock aria-hidden className="size-3 shrink-0" />
            {REQUIRES_HOLDING}
          </p>
        )}
      </div>
      {compareRole !== null && (
        <span className="flex w-16 shrink-0 justify-center pt-0.5">
          <PermissionCell
            granted={holds(compareRole, permission.code)}
            label={permission.label}
            roleName={compareRole.name}
          />
        </span>
      )}
      <span className="flex w-16 shrink-0 justify-center">
        <Switch
          aria-labelledby={labelId}
          aria-describedby={
            editable && locked ? `${descriptionId} ${reasonId}` : descriptionId
          }
          isSelected={holds(role, permission.code)}
          isDisabled={!editable || locked}
          isPulsing={isPulsing}
          onPulseEnd={onPulseEnd}
          onChange={onToggle}
        />
      </span>
    </div>
  );
}
