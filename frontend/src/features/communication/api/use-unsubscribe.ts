import { useMutation } from "@tanstack/react-query";
import { useEffect } from "react";

import { ApiError } from "@/lib/api-client";

import { httpUnsubscribeGateway } from "../adapters/http-unsubscribe-gateway";

/** The outcomes a visitor can actually be shown - see `UnsubscribeController`. A 204 covers
 *  a first-time unsubscribe, a repeat visit and a forged token alike: the backend answers
 *  the same way for all three, so none is distinguishable from outside and "done" is the
 *  honest read of every one of them. A 4xx is a malformed request ("invalid"); a 429 is the
 *  rate limiter ("rate-limited"); anything else - a 5xx, a dropped connection - is
 *  retryable ("error"), never evidence the link itself is dead. */
export type UnsubscribeStatus =
  "pending" | "done" | "invalid" | "rate-limited" | "error";

function statusFromError(
  error: unknown,
): Exclude<UnsubscribeStatus, "pending" | "done"> {
  if (!(error instanceof ApiError)) return "error";
  if (error.status === 429) return "rate-limited";
  if (error.status >= 500) return "error";
  return "invalid";
}

/** Fires the unsubscribe on mount, and again only if `token` itself changes (it never does
 *  in practice - the page reads it once from the URL). No guard against React StrictMode's
 *  development double-invoke: a ref-based "already fired" guard was tried and rejected -
 *  it calls `mutate` during the first of StrictMode's two effect passes, the one whose
 *  subscription gets torn down before the fetch resolves, and the second (surviving) pass
 *  then skips its own call and never subscribes to a mutation in flight - so the page hangs
 *  on "pending" forever, in development only. Two POSTs per development mount is the
 *  accepted cost instead: one extra unit of the 10/minute rate-limit budget, still ten
 *  times headroom below it, and production - which never double-invokes - sends exactly
 *  one. */
export function useUnsubscribeStatus(token: string | null): UnsubscribeStatus {
  const { mutate, status, error } = useMutation({
    mutationFn: (mutationToken: string | null) =>
      httpUnsubscribeGateway.unsubscribe(mutationToken),
  });
  useEffect(() => {
    mutate(token);
  }, [token, mutate]);

  if (status === "success") return "done";
  if (status === "error") return statusFromError(error);
  return "pending";
}
