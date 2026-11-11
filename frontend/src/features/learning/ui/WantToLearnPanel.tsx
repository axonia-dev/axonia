import { X } from "lucide-react";
import { useId } from "react";
import { Link } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { IconButton } from "@/components/ui/IconButton";
import { useRemoveIntent } from "@/features/learning/api/use-learning";
import type {
  HeldArea,
  LearningPage,
} from "@/features/learning/domain/learning";
import { OWN_SIGN_IN_REFUSAL } from "@/features/learning/learning-errors";
import { AddIntentField } from "@/features/learning/ui/AddIntentField";
import { declaredMonth } from "@/lib/declared-on";
import { userHue } from "@/lib/user-hue";

interface Props {
  slug: string;
  page: LearningPage;
}

interface RowProps {
  slug: string;
  area: HeldArea;
  isRemoving: boolean;
  /** Absent where the session may not change areas to learn. */
  onRemove?: ((tag: string) => void) | undefined;
}

const MAX_FACES = 4;

function OpenFaces({ slug, area }: Readonly<{ slug: string; area: HeldArea }>) {
  if (area.open.length === 0) return null;
  const search = new URLSearchParams({ term: area.tag, who: "open" });
  return (
    <Link
      to={`/communities/${slug}/expertise?${search.toString()}`}
      aria-label={`See who is open to questions on ${area.tag}`}
      className="focus-visible:ring-focus-ring shrink-0 rounded-full outline-none focus-visible:ring-2"
    >
      <MemberFacepile
        members={area.open.map((face) => ({
          id: face.userId,
          name: face.displayName ?? face.userId,
          hue: userHue(face.userId),
          src: face.avatarUrl,
        }))}
        max={MAX_FACES}
        size="size-7 text-[0.625rem]"
        open
      />
    </Link>
  );
}

function IntentRow({ slug, area, isRemoving, onRemove }: Readonly<RowProps>) {
  const nameId = useId();
  const { openCount } = area;
  return (
    <li
      aria-labelledby={nameId}
      className="flex items-center justify-between gap-3 px-4 py-3"
    >
      <div className="min-w-0 flex-1">
        <p
          id={nameId}
          className="text-foreground text-sm font-medium wrap-anywhere"
        >
          {area.tag}
        </p>
        {/* Each fact leads with its separator, and the one starting a line is clipped away. */}
        <p className="text-muted-foreground overflow-hidden text-xs">
          <span className="-ml-3 flex flex-wrap [&>span]:before:inline-block [&>span]:before:w-3 [&>span]:before:text-center [&>span]:before:not-italic [&>span]:before:content-['·'_/_'']">
            {openCount > 0 ? (
              <span>{openCount} open to questions</span>
            ) : (
              <span className="italic">Nobody open yet</span>
            )}
            <span>since {declaredMonth(area.declaredAt)}</span>
          </span>
        </p>
      </div>
      <OpenFaces slug={slug} area={area} />
      <IconButton
        label={`Remove ${area.tag}`}
        tone="danger"
        isDisabled={onRemove === undefined}
        isPending={isRemoving}
        onPress={() => onRemove?.(area.tag)}
      >
        <X aria-hidden />
      </IconButton>
    </li>
  );
}

/** A delegated session is refused every write, so its controls are disabled and it is told why.
 *  An archived community and a member at the cap take no new area, and removing stays open to both. */
export function WantToLearnPanel({ slug, page }: Readonly<Props>) {
  const remove = useRemoveIntent(slug);
  const writable = !useIsDelegated();
  const takesMore = !page.archived && page.mine.length < page.limit;

  return (
    <section className="border-border bg-surface flex flex-col gap-3 rounded-2xl border p-4 shadow-sm">
      <header className="flex items-baseline gap-2">
        <h2 className="font-display text-foreground text-base font-semibold">
          Want to learn
        </h2>
        <span className="text-muted-foreground text-xs">
          {page.mine.length} of {page.limit}
        </span>
      </header>
      {page.mine.length > 0 && (
        <ul className="border-border divide-border divide-y rounded-md border">
          {page.mine.map((area) => (
            <IntentRow
              key={area.tag}
              slug={slug}
              area={area}
              isRemoving={remove.isRemoving(area.tag)}
              onRemove={writable ? (tag) => remove.mutate(tag) : undefined}
            />
          ))}
        </ul>
      )}
      {!writable && (
        <p className="text-muted-foreground text-sm">{OWN_SIGN_IN_REFUSAL}</p>
      )}
      {writable && takesMore && (
        <AddIntentField
          slug={slug}
          held={page.mine.map((area) => area.tag)}
          wanted={page.wanted}
        />
      )}
    </section>
  );
}
