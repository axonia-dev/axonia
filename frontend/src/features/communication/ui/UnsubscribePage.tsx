import { CircleAlert, CircleCheck, Clock, type LucideIcon } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";

import { useAuth } from "@/auth/session";
import { PreviewFrame } from "@/components/patterns/PreviewFrame";

import {
  type UnsubscribeStatus,
  useUnsubscribeStatus,
} from "../api/use-unsubscribe";

type SettledStatus = Exclude<UnsubscribeStatus, "pending">;

const ICON: Record<SettledStatus, LucideIcon> = {
  done: CircleCheck,
  invalid: CircleAlert,
  "rate-limited": Clock,
  error: CircleAlert,
};

// "done" is the only outcome worth celebrating; the rest share a non-primary tone so the
// page never paints a genuine problem the same colour as success. Rate-limited gets its
// own (warning, not danger) shade: it isn't broken, it just needs a minute.
const ICON_TONE: Record<SettledStatus, string> = {
  done: "text-success",
  invalid: "text-danger",
  "rate-limited": "text-warning",
  error: "text-danger",
};

const HEADING: Record<SettledStatus, string> = {
  done: "Unsubscribed",
  invalid: "This link is not valid",
  "rate-limited": "Too many attempts",
  error: "Something went wrong",
};

// "done" reads the same for a first-time and a repeat visit, and even for a forged token -
// UnsubscribeController answers 204 for all three so none of them is distinguishable from
// the outside, and the honest thing to tell a repeat visitor is exactly this. "invalid" only
// ever names its one reachable cause (a missing or malformed token): an old or already-used
// token verifies fine server-side and lands on "done" instead, so this copy must not claim
// either of those as a possibility it never actually reaches.
const DETAIL: Record<SettledStatus, string> = {
  done: "These emails will no longer be sent.",
  invalid:
    "The link looks incomplete. Nothing changed - open the link from the email again.",
  "rate-limited": "Wait a minute, then open the link from the email again.",
  error: "Open the link from the email again.",
};

function StatusMessage({ status }: Readonly<{ status: UnsubscribeStatus }>) {
  if (status === "pending") {
    return <p className="text-muted-foreground text-sm">Working…</p>;
  }
  const Icon = ICON[status];
  return (
    <div className="flex flex-col gap-3">
      <h1 className="font-display text-foreground flex items-start gap-2 text-2xl font-bold">
        <Icon
          aria-hidden
          className={`${ICON_TONE[status]} mt-1 size-6 shrink-0`}
        />
        {HEADING[status]}
      </h1>
      <p className="text-muted-foreground text-sm">{DETAIL[status]}</p>
    </div>
  );
}

/**
 * The page every unsubscribe link in axonia mail lands on: holding the
 * token is what authorizes the write, so there is no sign-in here and none is ever
 * prompted - see `UnsubscribeController`. This route is registered outside the
 * authenticated tree entirely (App.tsx), because the guard that protects it there would
 * turn a signed-out visitor's mail-client click into a login prompt instead of the
 * confirmation they came for.
 *
 * Deliberately bare: this is the last thing a member sees after choosing to stop
 * receiving mail, so there is nothing here to sell them on staying. `aria-live="polite"`
 * on the wrapper is what lets a screen reader hear the outcome at all - the content it
 * announces is swapped in asynchronously, well after the page has already been read once.
 */
export function UnsubscribePage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const status = useUnsubscribeStatus(token);
  const auth = useAuth();

  return (
    <PreviewFrame>
      <div aria-live="polite" className="flex flex-col gap-4">
        <StatusMessage status={status} />
        {auth.isAuthenticated && (
          <Link
            className="text-primary text-sm underline"
            to="/settings/account/notifications"
          >
            Manage notification settings
          </Link>
        )}
      </div>
    </PreviewFrame>
  );
}
