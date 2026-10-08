import { EmptyState } from "@/components/patterns/EmptyState";
import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { LoveCount } from "@/components/ui/LoveCount";
import { SectionCard } from "@/components/ui/SectionCard";
import { SectionRow } from "@/components/ui/SectionRow";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  latestArtifacts,
  type LibraryRow,
} from "@/features/community/domain/library";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";
import { userHue } from "@/lib/user-hue";

interface TypeVisual {
  icon?: string | null;
  accentColor?: string | null;
}

interface Contributor {
  name: string;
  hue: string;
  avatarUrl: string | null;
  subject: string;
}

function contributorOf(
  userId: string | null | undefined,
  users: ReadonlyMap<string, User>,
): Contributor | null {
  if (!userId) return null;
  const user = users.get(userId);
  return {
    name: user?.displayName ?? userId,
    hue: userHue(userId),
    avatarUrl: user?.avatarUrl ?? null,
    subject: userId,
  };
}

function RowSubtitle({
  contributor,
  when,
}: Readonly<{ contributor: Contributor | null; when: string }>) {
  return (
    <>
      {contributor && (
        <MemberAvatar
          name={contributor.name}
          hue={contributor.hue}
          subject={contributor.subject}
          src={contributor.avatarUrl}
          shape="circle"
          className="size-[18px] shrink-0 text-[0.6rem]"
        />
      )}
      <span className="truncate">
        <span className="max-sm:hidden">by </span>
        {contributor?.name}
      </span>
      <span className="shrink-0">· {when}</span>
    </>
  );
}

function LibraryRowItem({
  row,
  users,
  visual,
}: Readonly<{
  row: LibraryRow;
  users: ReadonlyMap<string, User>;
  visual?: TypeVisual | undefined;
}>) {
  const when = relativeTime(row.occurredAt, useNow());
  const contributor = contributorOf(row.contributorUserId, users);

  return (
    <SectionRow
      to={row.href}
      leading={
        <TypeIcon
          typeId={row.typeId}
          label={row.type}
          icon={visual?.icon}
          accentColor={visual?.accentColor}
          size={20}
          className="size-10"
        />
      }
      title={row.title}
      subtitle={<RowSubtitle contributor={contributor} when={when} />}
      {...(row.loveCount > 0
        ? { meta: <LoveCount count={row.loveCount} /> }
        : {})}
    />
  );
}

interface FeedBodyProps {
  isPending: boolean;
  isError: boolean;
  items: LibraryRow[];
  users: ReadonlyMap<string, User>;
  typeById: Map<number, TypeVisual>;
}

/** Renders exactly one of: loading skeletons, error line, empty state, or the row list. */
function FeedBody({
  isPending,
  isError,
  items,
  users,
  typeById,
}: Readonly<FeedBodyProps>) {
  if (isPending) {
    return (
      <div aria-label="Loading the Library" className="flex flex-col gap-3 p-2">
        <Skeleton className="h-12" />
        <Skeleton className="h-12" />
        <Skeleton className="h-12" />
      </div>
    );
  }
  if (isError) {
    return (
      <p role="alert" className="text-muted-foreground p-2 text-sm">
        Could not load the Library.
      </p>
    );
  }
  if (items.length === 0) {
    return (
      <EmptyState
        title="Quiet for now"
        description="Nothing has been shared to the Library yet."
      />
    );
  }
  return (
    <ul className="flex flex-col">
      {items.map((item) => (
        <li key={item.key}>
          <LibraryRowItem
            row={item}
            users={users}
            visual={typeById.get(item.typeId)}
          />
        </li>
      ))}
    </ul>
  );
}

/**
 * The Library's newest, contributors credited by name inside the rows: attribution, never ranking.
 *
 * The marquee above owns everything about sessions, past and next, so this card says one thing.
 */
export function LibraryFeed({ slug }: Readonly<{ slug: string }>) {
  const artifacts = useArtifacts(slug, { page: 0, size: 5 });
  const types = useArtifactTypes();
  const typeById = new Map<number, TypeVisual>(
    (types.data ?? []).map((type) => [type.id, type]),
  );
  const items = latestArtifacts(slug, artifacts.data?.content ?? []);
  const users = useUsers(
    items.flatMap((item) =>
      item.contributorUserId ? [item.contributorUserId] : [],
    ),
  );

  return (
    <SectionCard
      title="From the Library"
      icon="library"
      accent="var(--primary)"
      action={{ label: "View Library", to: `/communities/${slug}/library` }}
    >
      <FeedBody
        isPending={artifacts.isPending}
        isError={artifacts.isError}
        items={items}
        users={new Map(Object.entries(users.data ?? {}))}
        typeById={typeById}
      />
    </SectionCard>
  );
}
