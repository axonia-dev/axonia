import { Video } from "lucide-react";

import { buttonClass } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

const SHUT_HINT = "The way in opens five minutes before the session.";

interface Props {
  describedBy: string;
  hintId: string;
  /** Whether the surface has room to state the wait. Given room, the control takes the width it
   *  will have once it opens and the hint is printed rather than only announced: a reader looking
   *  at a session next week learns there is a way in and when it arrives. Without room, the hint
   *  stays for screen readers alone, because a narrow card cannot spend a line on it. */
  roomy: boolean;
  className: string | undefined;
}

/**
 * `aria-disabled` rather than the `disabled` attribute: the control has no click handler to
 * block, and a natively disabled button is neither focusable nor hoverable, so the one thing
 * worth saying here - why there is no way in yet - would reach nobody. Focusable, it announces
 * "Join, unavailable" and then the reason. The two styles the shared base hangs off `disabled:`
 * are restated here against `aria-disabled:` for the same reason.
 */
export function ShutJoin({
  describedBy,
  hintId,
  roomy,
  className,
}: Readonly<Props>) {
  return (
    <>
      <button
        type="button"
        aria-disabled="true"
        aria-describedby={describedBy}
        className={cn(
          buttonClass("outline", roomy ? "md" : "sm"),
          "aria-disabled:pointer-events-none aria-disabled:opacity-50",
          roomy && "w-full",
          className,
        )}
      >
        <Video aria-hidden size={roomy ? 15 : 13} />
        Join
      </button>
      <span
        id={hintId}
        className={cn(
          roomy ? "text-muted-foreground text-xs" : "sr-only",
          className,
        )}
      >
        {SHUT_HINT}
      </span>
    </>
  );
}
