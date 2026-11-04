import { useSessionHeading } from "@/features/events/api/use-session";

/**
 * The morph id a card may give its title: none when the page it opens is known to head the session
 * with another name, so the card's words never morph into different ones. Until the session is
 * loaded nothing can morph anyway, as a morph waits for its destination.
 */
export function useTitleMorphId(
  sessionId: number | null,
  label: string,
  morphId: string | undefined,
): string | undefined {
  const heading = useSessionHeading(sessionId);
  return heading === undefined || heading === label ? morphId : undefined;
}
