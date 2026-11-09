import { EmptyState } from "@/components/patterns/EmptyState";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { useMySaved } from "@/features/bookmarks/api/use-save";
import type {
  SavedItem,
  SavedSessionView,
} from "@/features/bookmarks/domain/saved";
import { SavedSessionRow } from "@/features/bookmarks/ui/SavedSessionRow";
import { useUsers } from "@/features/identity/api/use-users";
import { failedToLoad } from "@/lib/failed-to-load";

function savedSessionsIn(
  items: ReadonlyArray<SavedItem>,
  slug: string,
): SavedSessionView[] {
  const sessions = items.filter(
    (item): item is SavedSessionView =>
      item.kind === "session" && item.community?.slug === slug,
  );
  sessions.sort((a, b) =>
    b.session.scheduledAt.localeCompare(a.session.scheduledAt),
  );
  return sessions;
}

function facilitatorIdsOf(sessions: ReadonlyArray<SavedSessionView>) {
  return [
    ...new Set(
      sessions
        .map((item) => item.session.facilitatorUserId)
        .filter((id): id is string => id !== null),
    ),
  ];
}

/** A community's saved sessions, latest first: what the Schedule shows with "Saved" on. */
export function CommunitySavedSessions({ slug }: Readonly<{ slug: string }>) {
  const saved = useMySaved();
  const sessions = savedSessionsIn(saved.data ?? [], slug);
  const users = useUsers(facilitatorIdsOf(sessions)).data;

  if (failedToLoad(saved)) {
    return (
      <LoadFailed
        inline
        message="Could not load the saved sessions."
        query={saved}
      />
    );
  }
  if (saved.isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (sessions.length === 0) {
    return (
      <EmptyState
        title="No saved sessions"
        description="Bookmarked sessions from this community appear here."
      />
    );
  }
  return (
    <ul className="flex flex-col gap-3">
      {sessions.map((item) => (
        <SavedSessionRow
          key={item.session.id}
          item={item}
          facilitatorName={
            item.session.facilitatorUserId
              ? users?.[item.session.facilitatorUserId]?.displayName
              : undefined
          }
          showCommunity={false}
        />
      ))}
    </ul>
  );
}
