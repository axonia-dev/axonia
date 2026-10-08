import type { ComponentProps } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";

/** Stands in for a shell that cannot be drawn without the caller's organization. */
export function OrganizationLoadFailed({
  query,
}: Readonly<Pick<ComponentProps<typeof LoadFailed>, "query">>) {
  return (
    <LoadFailed message="Could not load the organization." query={query} />
  );
}
