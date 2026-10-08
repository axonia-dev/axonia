import { X } from "lucide-react";

import { useIsDelegated } from "@/auth/delegated-session";
import { IconButton } from "@/components/ui/IconButton";
import { useRemoveIntent } from "@/features/learning/api/use-learning";
import type {
  HeldArea,
  LearningPage,
} from "@/features/learning/domain/learning";
import { OWN_SIGN_IN_REFUSAL } from "@/features/learning/learning-errors";
import { AddIntentField } from "@/features/learning/ui/AddIntentField";
import { declaredMonth } from "@/lib/declared-on";

interface Props {
  slug: string;
  page: LearningPage;
}

interface ChipProps {
  area: HeldArea;
  isRemoving: boolean;
  /** Absent where the session may not change areas to learn. */
  onRemove?: ((tag: string) => void) | undefined;
}

function IntentChip({ area, isRemoving, onRemove }: Readonly<ChipProps>) {
  return (
    <li className="border-secondary/35 bg-secondary/5 inline-flex max-w-full items-center gap-2 rounded-full border py-0.5 pr-0.5 pl-3 text-sm">
      <span className="text-foreground min-w-0 font-medium wrap-anywhere">
        {area.tag}
      </span>
      <span className="text-muted-foreground shrink-0 text-xs">
        since {declaredMonth(area.declaredAt)}
      </span>
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
        <ul className="flex flex-wrap gap-2">
          {page.mine.map((area) => (
            <IntentChip
              key={area.tag}
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
