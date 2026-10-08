import { z } from "zod";

const onboardingStepKeySchema = z.enum([
  "LOOK_AND_FEEL",
  "FIRST_SESSION",
  "BACKLOG",
  "FIRST_PEOPLE",
  "LIBRARY",
  "HEALTH",
  "MOST_LOVED_ARTIFACT",
  "LAST_HELD_SESSION",
  "NEXT_SESSION",
  "TOP_TOPIC",
  "DECLARE_EXPERTISE",
]);

const onboardingStepSchema = z.object({
  key: onboardingStepKeySchema,
  route: z.string(),
  detail: z.string().nullable(),
  visited: z.boolean(),
});

const onboardingPathSchema = z.object({
  variant: z.enum(["ORGANIZER", "MEMBER"]),
  communitySlug: z.string(),
  steps: z.array(onboardingStepSchema),
});

export const onboardingViewSchema = z.object({
  state: z.enum(["NEVER_ASSIGNED", "OPEN", "COMPLETED", "CLOSED"]),
  path: onboardingPathSchema.nullable(),
});

export type OnboardingStepKey = z.infer<typeof onboardingStepKeySchema>;
export type OnboardingStep = z.infer<typeof onboardingStepSchema>;
export type OnboardingPath = z.infer<typeof onboardingPathSchema>;
export type OnboardingView = z.infer<typeof onboardingViewSchema>;

export function visitedCount(path: OnboardingPath): number {
  return path.steps.filter((step) => step.visited).length;
}
