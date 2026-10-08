import type { SectionTemplateInput } from "@/features/events/domain/events-gateway";
import type { MeetingPlaceDraft } from "@/features/events/domain/meeting-place";

/** Whether the organizer has typed nothing into any of the three place fields. Read off the draft
 *  rather than the parsed place, because a field holding only spaces is still an untouched one. */
export function placeIsBlank(draft: MeetingPlaceDraft): boolean {
  return (
    draft.location.trim() === "" &&
    draft.joinUrl.trim() === "" &&
    draft.joiningDetails.trim() === ""
  );
}

/** The host of a join link, for a place that has a link and no room name. A URL the field has not
 *  finished being typed into is not one to render half of, so anything unparseable is skipped. */
function hostOf(url: string): string | null {
  try {
    return new URL(url).host;
  } catch {
    return null;
  }
}

/**
 * What the shut "Where it meets" section holds, in one line.
 *
 * Named by the most locating part there is: a room, failing that the service a link points at,
 * failing that the fact that only the joining details were filled in. A summary that just said
 * "Set" would leave an organizer opening the section to find out which of three fields they used.
 */
export function placeSummary(draft: MeetingPlaceDraft): string {
  if (placeIsBlank(draft)) {
    return "Not set.";
  }
  const location = draft.location.trim();
  if (location !== "") return location;
  const host = hostOf(draft.joinUrl.trim());
  if (host !== null) return host;
  return "Joining details only.";
}

/** What the shut "Default structure" section holds: how many sections, and how much of an hour
 *  they already claim. A section with no length of its own counts for nothing rather than
 *  breaking the total. */
export function structureSummary(structure: SectionTemplateInput[]): string {
  if (structure.length === 0) return "No sections yet.";
  const total = structure.reduce(
    (sum, section) => sum + (section.durationMinutes ?? 0),
    0,
  );
  const sections = `${structure.length} section${structure.length === 1 ? "" : "s"}`;
  return total === 0 ? sections : `${sections}, ${total} min`;
}
