import { Navigate } from "react-router-dom";

import { useEdition } from "@/edition-context";

/**
 * Stands in for a page that needs an organization while the session has none, and sends the
 * caller to `fallback`. The edition's shell gate comes first: it may hold the caller while it
 * enters an organization and comes back to the same link, and then the fallback never applies.
 */
export function OrganizationEntry({
  fallback,
}: Readonly<{ fallback: string }>) {
  const { ShellGate } = useEdition();
  return (
    <ShellGate>
      <Navigate to={fallback} replace />
    </ShellGate>
  );
}
