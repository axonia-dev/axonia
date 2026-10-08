import { TriangleAlert } from "lucide-react";

import { Button } from "@/components/ui/Button";
import type { MailServerView } from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  senderFacts,
  serverFacts,
  warningsOf,
} from "@/selfhosted/features/mail-server/domain/mail-server-copy";
import { MailServerHeader } from "@/selfhosted/features/mail-server/ui/MailServerHeader";
import { MailServerHistory } from "@/selfhosted/features/mail-server/ui/MailServerHistory";

type Props = Readonly<{
  titleId: string;
  view: MailServerView;
  canWrite: boolean;
  onEdit: () => void;
}>;

/** The facts on one line when they fit, separated by a middle dot. Every fact carries its dot in
 *  front, and the line is drawn a dot's width to the left of a box that clips it, so a fact that
 *  opens a line, the first or one that wrapped, shows none. The dot is a box of its own beside the
 *  text rather than inline in it, so a fact that wraps inside itself starts each later line after
 *  the dot, never under the clip. */
function Facts({ view }: Readonly<{ view: MailServerView }>) {
  const facts = [serverFacts(view), senderFacts(view)].filter(
    (fact) => fact !== null,
  );
  return (
    <div className="overflow-hidden">
      <p className="text-muted-foreground -ml-4 flex flex-wrap gap-y-1 text-sm">
        {facts.map((fact) => (
          <span key={fact} className="flex min-w-0">
            <span aria-hidden className="w-4 shrink-0 text-center">
              ·
            </span>
            <span className="min-w-0 wrap-anywhere">{fact}</span>
          </span>
        ))}
      </p>
    </div>
  );
}

function Warnings({ view }: Readonly<{ view: MailServerView }>) {
  const lines = warningsOf(view);
  if (lines.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1">
      {lines.map((line) => (
        <li key={line} className="text-warning flex items-start gap-2 text-sm">
          <TriangleAlert aria-hidden size={16} className="mt-0.5 shrink-0" />
          <span className="min-w-0 wrap-anywhere">{line}</span>
        </li>
      ))}
    </ul>
  );
}

/** The card at rest: one line of facts, what to worry about, and the history shut. */
export function MailServerAtRest({ titleId, view, canWrite, onEdit }: Props) {
  const configured = view.state !== "not_configured";
  const action = configured ? (
    <Button variant="outline" size="sm" onPress={onEdit}>
      Edit
    </Button>
  ) : (
    <Button size="sm" onPress={onEdit}>
      Set up
    </Button>
  );
  return (
    <>
      <MailServerHeader
        titleId={titleId}
        state={view.state}
        {...(canWrite ? { action } : {})}
      />
      {configured && <Facts view={view} />}
      <Warnings view={view} />
      {view.lastChange !== null && (
        <MailServerHistory lastChange={view.lastChange} />
      )}
    </>
  );
}
