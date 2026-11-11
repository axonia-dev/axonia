import { z } from "zod";

const statusLevelSchema = z.enum(["WORRY", "WATCH", "GOOD", "GREAT", "QUIET"]);
export type StatusLevel = z.infer<typeof statusLevelSchema>;

const statusCodeSchema = z.enum([
  "TOO_FEW_TALKS",
  "ONE_VOICE",
  "LEANS_ON_ONE",
  "SHARED",
  "SHARED_STAGE",
  "DORMANT",
  "STARTING_UP",
  "STALLED",
  "SLOWING",
  "PICKING_UP",
  "ON_RHYTHM",
  "PAYING_OFF",
  "STARTING_TO_PAY_OFF",
  "SAVED_NOT_CONFIRMED",
  "NO_PAYOFF_YET",
  "NOTHING_OPENED",
  "LEARNING_A_LOT",
  "LEARNING",
  "LITTLE_LEARNED",
  "LEARNING_TOO_FEW",
]);
export type StatusCode = z.infer<typeof statusCodeSchema>;

export const healthStatusSchema = z.object({
  code: statusCodeSchema,
  level: statusLevelSchema,
});
export type HealthStatus = z.infer<typeof healthStatusSchema>;

/**
 * A word or two per status, never a sentence: sentences belong to findings, and a pill that states
 * one would restate the finding beneath it.
 */
export const STATUS_WORD: Record<StatusCode, string> = {
  TOO_FEW_TALKS: "Too few talks",
  ONE_VOICE: "One voice",
  LEANS_ON_ONE: "Leans on one",
  SHARED: "Shared",
  SHARED_STAGE: "Shared stage",
  DORMANT: "Dormant",
  STARTING_UP: "Starting up",
  STALLED: "Stalled",
  SLOWING: "Slowing",
  PICKING_UP: "Picking up",
  ON_RHYTHM: "On rhythm",
  PAYING_OFF: "Paying off",
  STARTING_TO_PAY_OFF: "Starting to pay off",
  SAVED_NOT_CONFIRMED: "Saved, not confirmed",
  NO_PAYOFF_YET: "No payoff yet",
  NOTHING_OPENED: "Nothing opened yet",
  LEARNING_A_LOT: "Learning a lot",
  LEARNING: "Learning",
  LITTLE_LEARNED: "Little learned",
  LEARNING_TOO_FEW: "Too few answers",
};
