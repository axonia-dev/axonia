import type { ReactNode } from "react";

interface GuardedQuery<T> {
  isPending: boolean;
  isError: boolean;
  data: T | undefined;
}

interface OrganizerPageProps<T> {
  isOrganizer: boolean;
  query: GuardedQuery<T>;
  errorText: string;
  children: (data: T) => ReactNode;
}

/**
 * Shared gate for organizer-only community subpages: shows an "Organizers only"
 * notice, a loading hint, or an error, and otherwise renders the loaded data.
 */
export function OrganizerPage<T>({
  isOrganizer,
  query,
  errorText,
  children,
}: Readonly<OrganizerPageProps<T>>) {
  if (!isOrganizer) {
    return <p className="text-muted-foreground text-sm">Organizers only.</p>;
  }
  if (query.isPending) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (query.isError || query.data == null) {
    return (
      <p role="alert" className="text-muted-foreground text-sm">
        {errorText}
      </p>
    );
  }
  return <>{children(query.data)}</>;
}
