import { Flag } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { WORDS_CLOSED } from "@/features/mentoring/mentoring-errors";
import { addresseeOf } from "@/features/mentoring/ui/finale/Postcard";
import { meetingCount } from "@/features/mentoring/ui/journal-people";
import { pairingDates } from "@/features/mentoring/ui/mentoring-dates";
import { useImpactPath } from "@/features/mentoring/ui/mentoring-paths";
import { personName } from "@/features/mentoring/ui/PartnerFace";

interface Props {
  journal: Journal;
  onPostcard: () => void;
}

const WEEK_MS = 7 * 86_400_000;

function weeksOn(startedAt: string, endedAt: string): string {
  const weeks = Math.max(
    1,
    Math.round((Date.parse(endedAt) - Date.parse(startedAt)) / WEEK_MS),
  );
  return weeks === 1 ? "1 week on the trail" : `${weeks} weeks on the trail`;
}

function Recap({ journal }: Readonly<{ journal: Journal }>) {
  const done = journal.topics.filter((topic) => topic.state === "DONE").length;
  const facts = [
    [done, done === 1 ? "topic done" : "topics done"],
    [
      meetingCount(journal),
      meetingCount(journal) === 1 ? "meeting" : "meetings",
    ],
    [journal.notes.length, journal.notes.length === 1 ? "note" : "notes"],
  ] as const;
  return (
    <ul className="flex flex-wrap justify-center gap-x-6 gap-y-2">
      {facts.map(([value, label]) => (
        <li key={label} className="flex flex-col items-center">
          <span className="font-display text-foreground text-2xl leading-none font-bold tabular-nums">
            {value}
          </span>{" "}
          <span className="text-muted-foreground text-xs">{label}</span>
        </li>
      ))}
    </ul>
  );
}

function MenteeWords({ journal, onPostcard }: Readonly<Props>) {
  const { first } = addresseeOf(journal);
  if (journal.myWords !== null) {
    return (
      <div className="flex flex-col items-center gap-2">
        <p className="text-muted-foreground text-sm">
          A postcard went to {first}.
        </p>
        <Button variant="secondary" size="sm" onPress={onPostcard}>
          Open the postcard
        </Button>
      </div>
    );
  }
  if (!journal.canWriteWords) {
    return <p className="text-muted-foreground text-sm">{WORDS_CLOSED}</p>;
  }
  return (
    <div className="flex flex-col items-center gap-2">
      <p className="text-muted-foreground text-sm">
        A few words for {first} travel as a postcard.
      </p>
      <Button onPress={onPostcard}>Write a postcard</Button>
    </div>
  );
}

function MentorWords({ journal }: Readonly<{ journal: Journal }>) {
  const impact = useImpactPath();
  if (!journal.wordsWritten) return null;
  const { pairing } = journal;
  return (
    <Link
      to={impact}
      className="text-secondary text-sm font-semibold hover:underline"
    >
      Words from {personName(pairing.partnerName, pairing.partnerId)} wait on My
      impact
    </Link>
  );
}

/** Words written before the end, which the backend still keeps editable while the pairing runs. */
function WrittenEarly({ journal, onPostcard }: Readonly<Props>) {
  if (journal.pairing.myRole !== "MENTEE" || journal.myWords === null)
    return null;
  return (
    <section
      aria-label="Postcard"
      className="bg-surface-sunken rounded-2xl px-4 py-4"
    >
      <MenteeWords journal={journal} onPostcard={onPostcard} />
    </section>
  );
}

/** The last flag of an ended pairing: how long the trail ran, what it held, and the postcard. */
export function Finale({ journal, onPostcard }: Readonly<Props>) {
  const { pairing } = journal;
  if (pairing.endedAt === null) {
    return <WrittenEarly journal={journal} onPostcard={onPostcard} />;
  }
  return (
    <section
      aria-labelledby="journal-finale"
      className="border-border flex flex-col items-center gap-3 rounded-3xl border bg-[radial-gradient(circle_at_50%_0%,color-mix(in_srgb,var(--success)_28%,var(--surface)),var(--surface)_70%)] px-4 py-6 text-center sm:px-6"
    >
      <span
        aria-hidden
        className="bg-success text-success-foreground grid size-16 place-items-center rounded-full shadow-[0_8px_24px_color-mix(in_srgb,var(--success)_40%,transparent)]"
      >
        <Flag className="size-8 origin-[20%_90%] motion-safe:animate-[flag-wave_2.4s_ease-in-out_infinite]" />
      </span>
      <h2
        id="journal-finale"
        className="font-display text-foreground text-xl font-semibold"
      >
        {weeksOn(pairing.startedAt, pairing.endedAt)}
      </h2>
      <p className="text-muted-foreground -mt-2 text-sm">
        {pairingDates(pairing.startedAt, pairing.endedAt)}
      </p>
      <Recap journal={journal} />
      {pairing.myRole === "MENTEE" ? (
        <MenteeWords journal={journal} onPostcard={onPostcard} />
      ) : (
        <MentorWords journal={journal} />
      )}
    </section>
  );
}
