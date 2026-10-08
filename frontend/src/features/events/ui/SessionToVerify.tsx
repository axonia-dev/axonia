import { useActionItems } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { ActionPointRow } from "@/features/events/ui/ActionPointRow";
import { TruncationNotice } from "@/features/events/ui/TruncationNotice";
import { useUsers } from "@/features/identity/api/use-users";

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
      To verify
      <span className="bg-surface-sunken border-border text-muted-foreground rounded-full border px-1.5 py-px text-[10.5px] font-bold tracking-normal normal-case">
        {count}
      </span>
    </h4>
  );
}

interface Props {
  slug: string;
  sessionId: number;
}

const openFirst = (items: readonly ActionItem[]): ActionItem[] =>
  [...items].sort(
    (a, b) => Number(a.outcome !== "OPEN") - Number(b.outcome !== "OPEN"),
  );

/**
 * The checkpoint items this session owes an answer on, and the ones it has already answered.
 *
 * Open items lead; a settled one stays listed, struck through, so the press that settled it has an
 * undo in place rather than requiring the item be hunted down from the session that raised it. Only
 * items genuinely pinned to this session can come back settled: a settled item no longer late-binds
 * to the community's soonest upcoming session (see `checkpointSql`), which is what used to force an
 * `outcome: "OPEN"` filter here.
 */
export function SessionToVerify({ slug, sessionId }: Readonly<Props>) {
  const { data, isPending, isError } = useActionItems(slug, {
    checkpointSessionId: sessionId,
    page: 0,
    size: PAGE_SIZE,
  });
  const items = openFirst(data?.content ?? []);
  const openCount = items.filter((item) => item.outcome === "OPEN").length;
  const users = useUsers(assigneeIdsOf(items)).data ?? {};

  if (isError) {
    return (
      <section className="border-border mb-3 flex flex-col gap-3 border-b pb-4">
        <p role="alert" className="text-danger text-sm">
          Could not load what this session owes.
        </p>
      </section>
    );
  }
  if (isPending || items.length === 0) {
    return null;
  }

  const totalCount = data?.totalElements ?? items.length;

  return (
    <section className="border-border mb-3 flex flex-col gap-3 border-b pb-4">
      <Heading count={openCount} />
      <ul className="flex flex-col">
        {items.map((item) => (
          <ActionPointRow
            key={item.id}
            slug={slug}
            item={item}
            users={users}
            hideCheckpoint
          />
        ))}
      </ul>
      <TruncationNotice
        shown={items.length}
        total={totalCount}
        rest="The wrap-up mail lists every one of them."
      />
    </section>
  );
}
