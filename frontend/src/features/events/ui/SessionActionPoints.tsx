import { useActionItems } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { ActionPointRow } from "@/features/events/ui/ActionPointRow";
import { CaptureActionPointDialog } from "@/features/events/ui/CaptureActionPointDialog";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";

const PAGE_SIZE = 50;

function assigneeIdsOf(items: readonly ActionItem[]): string[] {
  const ids = items
    .map((item) => item.assigneeUserId)
    .filter((id): id is string => id !== null);
  return [...new Set(ids)];
}

interface HeadingProps {
  count: number;
}

function Heading({ count }: Readonly<HeadingProps>) {
  return (
    <h4 className="text-muted-foreground flex items-center gap-2 text-[11px] font-bold tracking-[0.11em] uppercase">
      Action points
      {count > 0 && (
        <span className="bg-surface-sunken border-border text-muted-foreground rounded-full border px-1.5 py-px text-[10.5px] font-bold tracking-normal normal-case">
          {count}
        </span>
      )}
    </h4>
  );
}

interface EmptyStateProps {
  slug: string;
  sessionId: number;
}

/** With nothing captured yet, this section is the entry point rather than an empty
 *  heading - the capture trigger no longer floats alone under the agenda. */
function EmptyState({ slug, sessionId }: Readonly<EmptyStateProps>) {
  return (
    <div className="border-border-strong flex flex-col items-center gap-3 rounded-md border border-dashed p-4 text-center">
      <p className="text-muted-foreground text-sm">
        Nothing was captured from this session yet.
      </p>
      <CaptureActionPointDialog slug={slug} sessionId={sessionId} />
    </div>
  );
}

interface SectionBodyProps {
  slug: string;
  sessionId: number;
  isPending: boolean;
  isError: boolean;
  actionPoints: readonly ActionItem[];
  users: Record<string, User>;
}

/**
 * This section is the only surface where an action item is visible on the session that raised
 * it, so its empty state is load-bearing: it tells an organizer "capture something". A failed
 * fetch must never render as that same empty state - it would tell them the opposite of the
 * truth, with no other view to catch the mistake against.
 */
function SectionBody({
  slug,
  sessionId,
  isPending,
  isError,
  actionPoints,
  users,
}: Readonly<SectionBodyProps>) {
  if (isPending) {
    return null;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load action points.
      </p>
    );
  }
  if (actionPoints.length === 0) {
    return <EmptyState slug={slug} sessionId={sessionId} />;
  }
  return (
    <ul className="flex flex-col">
      {actionPoints.map((item) => (
        <ActionPointRow key={item.id} slug={slug} item={item} users={users} />
      ))}
    </ul>
  );
}

interface Props {
  slug: string;
  sessionId: number;
}

/**
 * Action items raised from this held session, read by their `originSessionId` - history, not
 * where they are answered. Renders where `CaptureActionPointDialog` used to sit alone in
 * `SessionCard`'s capture-mode footer - the trigger now lives inside this section instead
 * of floating under the agenda.
 */
export function SessionActionPoints({ slug, sessionId }: Readonly<Props>) {
  const { data, isPending, isError } = useActionItems(slug, {
    originSessionId: sessionId,
    page: 0,
    size: PAGE_SIZE,
  });
  const actionPoints = data?.content ?? [];
  const totalCount = data?.totalElements ?? actionPoints.length;
  const users = useUsers(assigneeIdsOf(actionPoints)).data ?? {};

  return (
    <section className="border-border flex flex-col gap-3 border-t pt-4">
      <div className="flex items-center justify-between gap-3">
        <Heading count={totalCount} />
        {actionPoints.length > 0 && (
          <CaptureActionPointDialog slug={slug} sessionId={sessionId} />
        )}
      </div>
      <SectionBody
        slug={slug}
        sessionId={sessionId}
        isPending={isPending}
        isError={isError}
        actionPoints={actionPoints}
        users={users}
      />
      {totalCount > actionPoints.length && (
        <p className="text-muted-foreground text-xs">
          Showing the first {actionPoints.length} of {totalCount} action points.
          The wrap-up mail lists every one of them.
        </p>
      )}
    </section>
  );
}
