import { useParams } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { useMyWords } from "@/features/mentoring/api/use-mentoring";
import type { Words } from "@/features/mentoring/domain/mentoring";
import { Envelope } from "@/features/mentoring/ui/Envelope";

interface Guild {
  slug: string;
  name: string;
  entries: Words[];
}

function byGuild(words: ReadonlyArray<Words>): Guild[] {
  const groups = new Map<string, Words[]>();
  for (const entry of words) {
    groups.set(entry.communitySlug, [
      ...(groups.get(entry.communitySlug) ?? []),
      entry,
    ]);
  }
  return [...groups.entries()]
    .map(([slug, entries]) => ({
      slug,
      name: entries[0]?.communityName ?? "",
      entries,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** What mentees wrote to the member about their pairings, as envelopes under each guild. Never counted, summed or summarised. Changed words are a new envelope, sealed again. */
export function WordsFromMentees() {
  const delegated = useIsDelegated();
  const words = useMyWords();
  const { slug: here } = useParams();
  if (delegated) return null;
  const shown = (words.data ?? []).filter(
    (entry) => here === undefined || entry.communitySlug === here,
  );
  if (shown.length === 0) return null;
  return (
    <section className="flex flex-col gap-4">
      <h2 className="font-display text-foreground text-base font-semibold">
        Words from mentees
      </h2>
      {byGuild(shown).map(({ slug, name, entries }) => (
        <div key={slug} className="flex flex-col gap-2">
          <h3 className="text-muted-foreground text-sm font-medium">{name}</h3>
          <ul className="bg-surface-sunken grid grid-cols-1 gap-6 rounded-2xl p-5 sm:grid-cols-2">
            {entries.map((entry) => (
              <Envelope
                key={`${entry.pairingId}:${entry.writtenAt}`}
                words={entry}
              />
            ))}
          </ul>
        </div>
      ))}
    </section>
  );
}
