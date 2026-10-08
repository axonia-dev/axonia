import type { ReactNode } from "react";

import { StatusChip, type StatusTone } from "@/components/ui/StatusChip";
import type { MailServerState } from "@/selfhosted/features/mail-server/domain/mail-server";

type Chip = Readonly<{ tone: StatusTone; label: string }>;

function chipOf(state: MailServerState): Chip {
  switch (state) {
    case "not_configured":
      return { tone: "neutral", label: "Not configured" };
    case "configured":
    case "changed_since_test":
      return { tone: "success", label: "Configured" };
    case "delivery_failing":
      return { tone: "warning", label: "Delivery failing" };
    case "secret_unreadable":
      return { tone: "warning", label: "Password can’t be read" };
  }
}

export function MailServerTitle({ id }: Readonly<{ id: string }>) {
  return (
    <h2
      id={id}
      className="font-display text-foreground text-base font-semibold"
    >
      Mail server
    </h2>
  );
}

type HeaderProps = Readonly<{
  titleId: string;
  state: MailServerState;
  action?: ReactNode;
}>;

/** The title, the state, and on the right the one action the reader may take. Mail still leaves
 *  in `changed_since_test`, so its chip says Configured and the card says what changed. */
export function MailServerHeader({ titleId, state, action }: HeaderProps) {
  const chip = chipOf(state);
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
      <MailServerTitle id={titleId} />
      <StatusChip tone={chip.tone} label={chip.label} />
      {action !== undefined && (
        <div data-card-action className="ml-auto">
          {action}
        </div>
      )}
    </div>
  );
}
