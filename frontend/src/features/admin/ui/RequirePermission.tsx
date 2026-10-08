import type { ReactNode } from "react";

import { PageLoadFailed } from "@/components/patterns/PageLoadFailed";
import { usePermissions } from "@/features/admin/api/use-permissions";
import type { RetryableRead } from "@/lib/failed-to-load";

interface RequirePermissionProps {
  permission: string;
  /** The guarded page's own heading, kept on screen when the permissions could not load. */
  title?: string;
  children: ReactNode;
}

/** Retries the one `/access/me` read every permission check answers from, through the
 *  observer of the gate that found it failed. */
export function PermissionsLoadFailed({
  title,
  read,
}: Readonly<{ title?: string | undefined; read: RetryableRead }>) {
  return (
    <PageLoadFailed
      title={title}
      message="Could not load the permissions."
      query={read}
    />
  );
}

export function RequirePermission({
  permission,
  title,
  children,
}: Readonly<RequirePermissionProps>) {
  const { can, isLoading, isError, retry } = usePermissions();
  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (isError) return <PermissionsLoadFailed title={title} read={retry} />;
  if (!can(permission)) {
    return (
      <p role="alert" className="text-muted-foreground text-sm">
        No access to this.
      </p>
    );
  }
  return <>{children}</>;
}
