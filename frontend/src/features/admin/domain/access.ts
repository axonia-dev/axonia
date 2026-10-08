import { z } from "zod";

// Id and name, never the permissions: what the caller's roles grant is answered once, by
// `permissions` above. The id is what tints the chip, so the same role reads in the same
// colour on the profile as on the admin roster.
const heldRoleSchema = z.object({
  id: z.number(),
  name: z.string(),
});
export type HeldRole = z.infer<typeof heldRoleSchema>;

export const accessMeSchema = z.object({
  permissions: z.array(z.string()),
  // Absent on a backend that does not answer with roles, and empty for an enterprise member,
  // where Member is implicit and no binding row exists.
  roles: z.array(heldRoleSchema).default([]),
});

export type AccessMe = z.infer<typeof accessMeSchema>;
