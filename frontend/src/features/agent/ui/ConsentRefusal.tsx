import type { ReactNode } from "react";

import {
  refusalLine,
  type RefusalReason,
} from "../domain/authorization-request";
import { ConsentOrganization } from "./ConsentOrganization";

interface ConsentNoticeProps {
  title: string;
  line: string;
  children?: ReactNode;
}

/** A card that stands in for the consent card when there is nothing to allow. */
export function ConsentNotice({
  title,
  line,
  children,
}: Readonly<ConsentNoticeProps>) {
  return (
    <div className="border-border bg-surface flex flex-col gap-3 rounded-xl border p-6">
      <h1 className="font-display text-foreground text-2xl font-bold">
        {title}
      </h1>
      <p className="text-muted-foreground text-sm">{line}</p>
      {children}
    </div>
  );
}

export function OrganizationChoice() {
  return (
    <div className="flex items-center justify-between gap-3 text-sm">
      <span className="text-muted-foreground">Organization</span>
      <ConsentOrganization />
    </div>
  );
}

export function ConsentRefusal({
  reason,
}: Readonly<{ reason: RefusalReason }>) {
  return (
    <ConsentNotice title="Connection refused" line={refusalLine(reason)}>
      {reason === "NO_AGENT_ACCESS" && <OrganizationChoice />}
    </ConsentNotice>
  );
}
