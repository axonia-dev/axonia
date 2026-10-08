import { ChevronDown } from "lucide-react";
import { useContext, useState } from "react";
import {
  Button as AriaButton,
  Disclosure,
  DisclosurePanel,
  DisclosureStateContext,
  Heading,
} from "react-aria-components";

import { AnimatedCount } from "@/components/ui/AnimatedCount";
import type { Role } from "@/features/admin/domain/admin-user";
import type { PermissionDef } from "@/features/admin/domain/permission-catalog";
import { holds } from "@/features/admin/domain/role-reach";
import { PermissionRow } from "@/features/admin/ui/PermissionRow";
import { cn } from "@/lib/cn";

interface PermissionGroup {
  key: string;
  label: string;
  permissions: PermissionDef[];
}

interface PermissionGroupSectionProps {
  group: PermissionGroup;
  role: Role;
  /** The pinned comparison role, or null when not comparing. */
  compareRole: Role | null;
  /** Toggling is allowed only for a non-system role when the viewer can write. */
  editable: boolean;
  /** While comparing: hide every row the two roles agree on. */
  differencesOnly: boolean;
  /** Permission codes the current search matched, so a matched row is tinted and marked. */
  matchingCodes: Set<string>;
  /** The raw search text, so a matched row can mark the run that matched. */
  query: string;
  /** Held by the pane, not here, so one control can shut every group at once. */
  isExpanded: boolean;
  onExpandedChange: (expanded: boolean) => void;
  onToggle: (code: string) => void;
  /** The caller's own permission codes: the ceiling a row may not move past. Account-scoped,
   *  unlike `role` above and `heldIn` below, which read the open role. */
  heldPermissions: ReadonlySet<string>;
}

/** How many of the group's permissions a role grants. */
export function heldIn(role: Role, permissions: PermissionDef[]): number {
  return permissions.filter((p) => role.permissions.includes(p.code)).length;
}

/** The rows this section shows, after the differences-only filter. */
function visibleRows(
  props: Readonly<PermissionGroupSectionProps>,
): PermissionDef[] {
  const { group, role, compareRole, differencesOnly } = props;
  if (!differencesOnly || compareRole === null) return group.permissions;
  return group.permissions.filter(
    (p) => holds(role, p.code) !== holds(compareRole, p.code),
  );
}

/** `data-expanded` lands only on the `Disclosure` root, never on the trigger `Button`, so the
 *  chevron reads the shared expanded state from context rather than a `group-data-[...]`
 *  selector the trigger element can never satisfy. */
function GroupChevron() {
  const isExpanded = useContext(DisclosureStateContext)?.isExpanded ?? false;
  return (
    <ChevronDown
      aria-hidden
      className={cn(
        "text-muted-foreground size-3.5 shrink-0 -rotate-90 motion-safe:transition-transform",
        isExpanded && "rotate-0",
      )}
    />
  );
}

function Tally({
  role,
  permissions,
  className,
}: Readonly<{ role: Role; permissions: PermissionDef[]; className: string }>) {
  return (
    <span className={className}>
      <AnimatedCount value={heldIn(role, permissions)} /> of{" "}
      {permissions.length}
    </span>
  );
}

export function PermissionGroupSection(
  props: Readonly<PermissionGroupSectionProps>,
) {
  const {
    group,
    role,
    compareRole,
    editable,
    matchingCodes,
    query,
    isExpanded,
    onExpandedChange,
    onToggle,
    heldPermissions,
  } = props;
  const rows = visibleRows(props);
  const [pulsing, setPulsing] = useState<string | null>(null);
  // Differences-only can empty a group entirely. The pane states what it stripped on one
  // line rather than drawing a header over nothing.
  if (rows.length === 0) return null;

  return (
    <Disclosure
      // Controlled by the pane. Every group starts expanded: taking the shape of the role
      // instead - shut wherever it holds nothing - hid most of the catalog the moment a new
      // role was given its first permission, which is exactly when the rest of the catalog is
      // what the reader came for.
      isExpanded={isExpanded}
      onExpandedChange={onExpandedChange}
      // The rule separates one group from the next, so the last one on screen does not draw
      // it: a second line right above the card's own edge reads as a doubled border.
      className="border-border/70 border-b last:border-b-0"
    >
      <Heading level={3}>
        <AriaButton
          slot="trigger"
          className="focus-visible:ring-focus-ring flex w-full cursor-pointer items-center gap-2 py-3 text-left outline-none focus-visible:ring-2"
        >
          <GroupChevron />
          <span className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {group.label}
          </span>
          <span className="text-muted-foreground ml-auto flex gap-3.5 text-xs">
            {compareRole !== null && (
              <Tally
                role={compareRole}
                permissions={group.permissions}
                className="text-secondary w-16 text-center"
              />
            )}
            <Tally
              role={role}
              permissions={group.permissions}
              className={cn(
                compareRole !== null && "text-primary-strong w-16 text-center",
              )}
            />
          </span>
        </AriaButton>
      </Heading>
      {/* The padding sits inside the panel: a shut one is `hidden="until-found"`, which keeps its
          own padding on screen. */}
      <DisclosurePanel>
        <div className="flex flex-col pb-2">
          {rows.map((permission) => (
            <PermissionRow
              key={permission.code}
              permission={permission}
              role={role}
              compareRole={compareRole}
              editable={editable}
              matched={matchingCodes.has(permission.code)}
              query={query}
              isPulsing={pulsing === permission.code}
              onPulseEnd={() => setPulsing(null)}
              onToggle={() => {
                setPulsing(permission.code);
                onToggle(permission.code);
              }}
              heldPermissions={heldPermissions}
            />
          ))}
        </div>
      </DisclosurePanel>
    </Disclosure>
  );
}
