import { useParams } from "react-router-dom";

/** Where a pairing's journal lives: under the community when My mentoring was opened inside one, else at the top. */
export function useJournalPath(): (pairingId: number) => string {
  const { slug } = useParams();
  return (pairingId) =>
    slug === undefined
      ? `/me/mentoring/${pairingId}`
      : `/communities/${slug}/me/mentoring/${pairingId}`;
}

/** My impact, where a mentor reads the words mentees wrote: inside the community when the journal was opened in one. */
export function useImpactPath(): string {
  const { slug } = useParams();
  return slug === undefined ? "/me/impact" : `/communities/${slug}/me/impact`;
}

/** A community's Mentoring page, where its mentors and mentees are listed. */
export function mentoringPagePath(slug: string): string {
  return `/communities/${slug}/mentoring`;
}

/** Where a member says what they can mentor on: their open to questions areas in this community. */
export function expertisePath(slug: string): string {
  return `/communities/${slug}/me/expertise`;
}

/** Where a member says what they want to grow in: their learning intents in this community. */
export function learningPath(slug: string): string {
  return `/communities/${slug}/learning`;
}
