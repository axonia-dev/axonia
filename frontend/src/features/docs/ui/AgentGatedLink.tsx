import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";

/**
 * A link inside the agent guide that stays a link only where agents may actually connect.
 *
 * The guide is readable by every member, but its destinations are not reachable by every
 * member: /me/api-keys redirects twice and lands a refused reader on the account settings
 * overview, which says nothing about what just happened. So the instruction is rendered as
 * plain text instead, beside the notice that explains the absence.
 *
 * Reads the same `useAgentAccess` the notice and the route guard read, so the rule lives in
 * one place. While the answer is unknown or failed it stays plain text, as the rail stays
 * closed: the route guard says its read failed, but a link must not promise a page that may
 * not be there.
 */
export function AgentGatedLink({
  to,
  children,
}: Readonly<{ to: string; children: ReactNode }>) {
  const { available } = useAgentAccess();

  if (!available) return <>{children}</>;

  return <Link to={to}>{children}</Link>;
}
