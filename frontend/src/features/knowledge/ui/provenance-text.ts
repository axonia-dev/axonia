import type { Session } from "@/features/events/domain/session";

function formatDate(iso: string): string {
  // occurrenceDate is a bare calendar date that parses as UTC midnight; render
  // it (and scheduledAt) in UTC so the day never rolls back a frame west of UTC,
  // matching how the agenda renders its dates.
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * The separator of a label this app no longer writes. Capture used to append the
 * presenters to the slot title, joined by an em dash; it now snapshots the title
 * alone, so a label written today has no suffix to strip.
 *
 * The em dash lives on here, and only here, for the rows already in the database.
 * It is deliberately not widened to a hyphen: `"Kafka - the good parts"` is an
 * ordinary title, and splitting on " - " would render its provenance as `"Kafka"`.
 * A U+2014 inside a title is vanishingly rare, a hyphen is not, which is exactly
 * why the old label could be parsed and a hyphenated one cannot. Written as an
 * escape so no literal em dash appears in the source.
 */
// eslint-disable-next-line no-restricted-syntax -- matches an em dash already stored in legacy rows, not new copy
const LEGACY_PRESENTER_SEPARATOR = " \u2014 ";

/**
 * The topic, taken from the immutable provenance snapshot: the slot's title. A legacy
 * label carries a presenter suffix, which is dropped (the contributor already shows in
 * the footer); anything else is the topic, whole, hyphens and all.
 */
function topicOf(label: string | null): string {
  if (label === null || label.trim() === "") {
    return "a session";
  }
  const head = label.split(LEGACY_PRESENTER_SEPARATOR)[0]?.trim();
  return head !== undefined && head !== "" ? head : label.trim();
}

/**
 * "series · topic (date)" for an artifact's provenance: topic from the captured
 * snapshot, series title and date resolved live from the events surface. Falls
 * back to just the topic when the session is beyond the resolved window or gone.
 */
export function provenanceText(
  label: string | null,
  session: Session | undefined,
  seriesTitle: string | undefined,
): string {
  const topic = topicOf(label);
  if (!session) {
    return topic;
  }
  const date = formatDate(session.occurrenceDate ?? session.scheduledAt);
  const head = seriesTitle !== undefined ? `${seriesTitle} · ${topic}` : topic;
  return `${head} (${date})`;
}
