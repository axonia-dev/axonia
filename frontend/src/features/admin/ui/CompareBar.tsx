import { X } from "lucide-react";

import { Select } from "@/components/ui/Select";
import { Switch } from "@/components/ui/Switch";
import type { Role } from "@/features/admin/domain/admin-user";

interface CompareBarProps {
  role: Role;
  roles: Role[];
  compareRole: Role | null;
  differencesOnly: boolean;
  onCompare: (roleId: number | null) => void;
  onDifferencesOnly: (on: boolean) => void;
}

/** Pins a second role beside this one, and once pinned, offers to strip the page down to
 *  what the two disagree on. The pin is a URL parameter, so a diff is a link. */
export function CompareBar({
  role,
  roles,
  compareRole,
  differencesOnly,
  onCompare,
  onDifferencesOnly,
}: Readonly<CompareBarProps>) {
  if (compareRole === null) {
    const options = roles
      .filter((r) => r.id !== role.id)
      .map((r) => ({ id: r.id, label: r.name }));
    if (options.length === 0) return null;
    return (
      <Select
        label="Pin a second role to compare"
        hideLabel
        value={null}
        options={options}
        placeholder="Compare with"
        onChange={(id) => onCompare(id)}
        className="w-44"
      />
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-2 motion-safe:transition-opacity motion-safe:duration-300 motion-safe:starting:opacity-0">
      <span className="border-secondary/45 bg-secondary/15 text-secondary flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium">
        {compareRole.name}
        <button
          type="button"
          aria-label={`Stop comparing with ${compareRole.name}`}
          onClick={() => onCompare(null)}
          className="focus-visible:ring-focus-ring cursor-pointer rounded outline-none focus-visible:ring-2"
        >
          <X aria-hidden className="size-3" />
        </button>
      </span>
      <Switch
        label="Differences only"
        isSelected={differencesOnly}
        onChange={onDifferencesOnly}
      />
    </div>
  );
}
