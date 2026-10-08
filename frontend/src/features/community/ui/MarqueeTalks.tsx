import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type { RecapTalk } from "@/features/community/domain/session-recap";
import { SectionTypeIcon } from "@/features/events/ui/section-type-visual";
import type { User } from "@/features/identity/domain/user";
import { userHue } from "@/lib/user-hue";

interface Props {
  talks: readonly RecapTalk[];
  moreTalks: number;
  users: ReadonlyMap<string, User>;
}

function Presenter({
  userId,
  users,
}: Readonly<{ userId: string; users: ReadonlyMap<string, User> }>) {
  const user = users.get(userId);
  return (
    <span className="flex min-w-0 items-center gap-1.5">
      <MemberAvatar
        name={user?.displayName ?? userId}
        hue={userHue(userId)}
        subject={userId}
        src={user?.avatarUrl ?? null}
        shape="circle"
        className="size-[18px] shrink-0 text-[0.6rem]"
      />
      <span className="text-muted-foreground truncate text-xs">
        {user?.displayName ?? userId}
      </span>
    </span>
  );
}

function TalkTile({
  talk,
  users,
}: Readonly<{ talk: RecapTalk; users: ReadonlyMap<string, User> }>) {
  const [first] = talk.presenterUserIds;
  return (
    <li className="border-border bg-surface/60 flex flex-col gap-2 rounded-md border p-3">
      <div className="flex items-start justify-between gap-2">
        <SectionTypeIcon
          typeId={talk.sectionTypeId}
          label={talk.sectionLabel}
          icon={talk.icon}
          accentColor={talk.accentColor}
          size={15}
          className="size-7"
        />
        <span className="text-muted-foreground shrink-0 text-xs">
          {talk.durationMinutes} min
        </span>
      </div>
      <p className="text-foreground line-clamp-2 text-sm leading-snug font-semibold">
        {talk.title}
      </p>
      {first === undefined ? (
        <span className="text-muted-foreground truncate text-xs">
          {talk.sectionLabel}
        </span>
      ) : (
        <Presenter userId={first} users={users} />
      )}
    </li>
  );
}

/**
 * One entry per tile count, spelt out whole: Tailwind only generates the class names it can read,
 * and a breakpoint's column count declared without its base leaves the phone on an implicit
 * content-sized track.
 */
const COLUMNS = [
  "grid-cols-1",
  "grid-cols-1 sm:grid-cols-2",
  "grid-cols-1 sm:grid-cols-3",
];

/**
 * The talks as they ran, one tile each.
 *
 * The grid takes a column per tile up to three, rather than always three: one tile alone in a
 * three-column grid reads as two tiles that failed to load.
 */
export function MarqueeTalks({ talks, moreTalks, users }: Readonly<Props>) {
  if (talks.length === 0) {
    return null;
  }
  const tiles = talks.length + (moreTalks > 0 ? 1 : 0);
  return (
    <ul className={`mt-4 grid gap-2 ${COLUMNS[Math.min(tiles, 3) - 1]}`}>
      {talks.map((talk) => (
        <TalkTile key={talk.key} talk={talk} users={users} />
      ))}
      {moreTalks > 0 && (
        <li className="border-border text-muted-foreground flex items-center justify-center rounded-md border border-dashed p-3 text-sm font-semibold">
          {moreTalks} more
        </li>
      )}
    </ul>
  );
}
