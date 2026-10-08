import type {
  OnboardingStep,
  OnboardingStepKey,
} from "@/features/onboarding/domain/onboarding";

/** Every step is named with the word the menu uses for the place it opens, so the panel and the
 *  rail never call the same page two different things. Several member steps land in the same
 *  place, which is why the title alone does not tell them apart - see {@link stepDescription}. */
export const STEP_LABELS: Record<OnboardingStepKey, string> = {
  LOOK_AND_FEEL: "Look & feel",
  FIRST_SESSION: "Schedule",
  BACKLOG: "Backlog",
  FIRST_PEOPLE: "Members & roles",
  LIBRARY: "Library",
  HEALTH: "Health",
  MOST_LOVED_ARTIFACT: "Library",
  LAST_HELD_SESSION: "Schedule",
  NEXT_SESSION: "Schedule",
  TOP_TOPIC: "Backlog",
  DECLARE_EXPERTISE: "My expertise",
};

// An organizer step names a place and carries no server detail, so the line under its title says
// what is kept there.
const PLACE_DESCRIPTIONS: Partial<Record<OnboardingStepKey, string>> = {
  LOOK_AND_FEEL: "Hero image, badge and accent color",
  FIRST_SESSION: "One-off sessions and recurring series",
  BACKLOG: "Requests, offers and actions, ranked by votes",
  FIRST_PEOPLE: "Invite by link, or from the directory",
  LIBRARY: "Artifacts the community keeps",
  HEALTH: "What the numbers say once there is activity",
  DECLARE_EXPERTISE: "How people find a person here",
};

// A member step names a thing the server froze into `detail` at assignment. The prefix is what
// keeps two steps that both open the Schedule apart, since their titles cannot.
const DETAIL_PREFIXES: Partial<Record<OnboardingStepKey, string>> = {
  MOST_LOVED_ARTIFACT: "Most loved",
  LAST_HELD_SESSION: "Last session",
  NEXT_SESSION: "Next session",
  TOP_TOPIC: "Top voted",
};

/** The smaller, factual line under a step's title, or null when there is nothing to say. */
export function stepDescription(step: OnboardingStep): string | null {
  if (step.detail === null) return PLACE_DESCRIPTIONS[step.key] ?? null;
  const prefix = DETAIL_PREFIXES[step.key];
  return prefix === undefined ? step.detail : `${prefix}: ${step.detail}`;
}
