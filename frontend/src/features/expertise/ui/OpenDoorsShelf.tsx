import { useId, useState } from "react";

import { useOpenDoors } from "@/features/expertise/api/use-open-doors";
import { OpenDoorCard } from "@/features/expertise/ui/OpenDoorCard";
import { useNow } from "@/lib/use-wake-at";

const SHOWN = 4;
const NEW_FOR_MS = 7 * 24 * 60 * 60 * 1000;

/** The areas people are open to questions on, before anything is typed. Absent until there is one. */
export function OpenDoorsShelf({
  slug,
  onOpen,
}: Readonly<{ slug: string; onOpen: (tag: string) => void }>) {
  const headingId = useId();
  const now = useNow().getTime();
  const [showAll, setShowAll] = useState(false);
  const { data } = useOpenDoors(slug);
  if (data === undefined || data.length === 0) return null;

  const shown = showAll ? data : data.slice(0, SHOWN);
  return (
    <section
      aria-labelledby={headingId}
      className="border-border flex flex-col gap-3 border-t p-5"
    >
      <div className="flex items-baseline justify-between gap-3">
        <h2 id={headingId} className="text-foreground font-semibold">
          Open to questions
        </h2>
        {!showAll && data.length > SHOWN && (
          <button
            type="button"
            onClick={() => setShowAll(true)}
            className="text-primary-strong focus-visible:ring-focus-ring shrink-0 cursor-pointer rounded-lg text-sm font-medium outline-none hover:underline focus-visible:ring-2"
          >
            All {data.length} areas
          </button>
        )}
      </div>
      {/* As many columns as fit cards wide enough for four ringed faces, never more than four. */}
      <ul className="grid grid-cols-[repeat(auto-fill,minmax(max(8.25rem,calc((100%_-_2.25rem)/4)),1fr))] gap-3">
        {shown.map((area) => (
          <li key={area.canonicalTag} className="flex min-w-0">
            <OpenDoorCard
              area={area}
              isNew={now - Date.parse(area.latestDeclaredAt) < NEW_FOR_MS}
              onOpen={() => onOpen(area.canonicalTag)}
            />
          </li>
        ))}
      </ul>
    </section>
  );
}
