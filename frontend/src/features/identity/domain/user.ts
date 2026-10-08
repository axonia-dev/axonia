import { z } from "zod";

// Whether a person is still here, mirroring the backend's derived signal: PRESENT and
// DEACTIVATED both keep their real name, ERASED substitutes the backend's neutral label.
export const memberStatusSchema = z.enum(["PRESENT", "DEACTIVATED", "ERASED"]);
export type MemberStatus = z.infer<typeof memberStatusSchema>;

/** The backend's neutral label for an erased member, for the places that have to name one the directory cannot. */
export const DELETED_MEMBER = "Deleted member";

// A profile as the identity context asserts it; `id` is the JWT `sub`.
export const userSchema = z.object({
  id: z.string(),
  displayName: z.string(),
  email: z.string().nullish(),
  avatarUrl: z.string().nullish(),
  department: z.string().nullish(),
  businessUnit: z.string().nullish(),
  team: z.string().nullish(),
  memberStatus: memberStatusSchema,
  memberSince: z.string().nullish(),
  lastActive: z.string().nullish(),
  signInProvider: z.string().nullish(),
  // Not nullish: the backend always states it, and defaulting a missing value to `true`
  // would offer a Remove that clears nothing.
  avatarIsOwn: z.boolean().default(false),
  // The effective clock, declared over observed, and null when the member has neither: unknown,
  // never defaulted. `observedTimeZone` travels beside it so the browser can tell whether its
  // current reading has drifted from the last one recorded and report only then.
  timeZone: z.string().nullish(),
  observedTimeZone: z.string().nullish(),
  // Not nullish, for the reason `avatarIsOwn` is not: defaulting a missing value to `true` would
  // offer an Automatic that clears nothing.
  timeZoneIsOwn: z.boolean().default(false),
});

export type User = z.infer<typeof userSchema>;

/** The finest unit set, the one the backend names a practitioner by. */
export function primaryOrgUnit(
  user: Pick<User, "team" | "department" | "businessUnit">,
): string | null {
  return (
    [user.team, user.department, user.businessUnit].find(
      (unit): unit is string => typeof unit === "string" && unit !== "",
    ) ?? null
  );
}
