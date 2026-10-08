import { ChevronRight } from "lucide-react";
import { Fragment } from "react";

import type { User } from "@/features/identity/domain/user";

interface Altitude {
  label: string;
  value: string;
}

/** Only the altitudes the directory actually supplied, coarse to fine. A hub member has none
 *  of them, and an empty cell reads as a field the directory failed to fill rather than one
 *  that does not apply. */
function altitudes(user: User): ReadonlyArray<Altitude> {
  const candidates: ReadonlyArray<Altitude | undefined> = [
    user.businessUnit
      ? { label: "BUSINESS UNIT", value: user.businessUnit }
      : undefined,
    user.department
      ? { label: "DEPARTMENT", value: user.department }
      : undefined,
    user.team ? { label: "TEAM", value: user.team } : undefined,
  ];
  return candidates.filter((altitude) => altitude !== undefined);
}

interface OrgUnitBandProps {
  user: User;
}

/** Where a member sits in the directory, shown as one coarse-to-fine path rather than three
 *  separate labelled fields. */
export function OrgUnitBand({ user }: Readonly<OrgUnitBandProps>) {
  const path = altitudes(user);
  if (path.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-3">
      {path.map((altitude, index) => (
        <Fragment key={altitude.label}>
          {index > 0 && (
            <ChevronRight
              aria-hidden
              size={16}
              className="text-muted-foreground shrink-0"
            />
          )}
          <div className="flex flex-col gap-0.5">
            <span className="text-foreground text-sm font-semibold">
              {altitude.value}
            </span>
            <span className="text-muted-foreground text-xs font-medium">
              {altitude.label}
            </span>
          </div>
        </Fragment>
      ))}
    </div>
  );
}
