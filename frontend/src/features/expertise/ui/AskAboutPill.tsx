import { MessageCircle } from "lucide-react";

import { monthName } from "@/lib/declared-on";
import { useNow } from "@/lib/use-wake-at";

/** The invitation a person open to questions carries on a card, in place of a date line. */
export function AskAboutPill({
  term,
  since,
}: Readonly<{ term: string; since: string | null }>) {
  const now = useNow();
  return (
    <span
      data-testid="ask-about"
      className="border-primary/25 bg-primary/10 text-primary-ink inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium"
    >
      <MessageCircle
        size={13}
        strokeWidth={2.4}
        aria-hidden="true"
        className="shrink-0"
      />
      <span className="min-w-0 break-words">
        Ask about <span className="text-foreground">{term}</span>
      </span>
      {since !== null && (
        <span className="text-muted-foreground shrink-0 font-normal">
          · since {monthName(since, now)}
        </span>
      )}
    </span>
  );
}
