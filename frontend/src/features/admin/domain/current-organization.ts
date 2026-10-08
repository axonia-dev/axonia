import { z } from "zod";

// The Organization the signed-in caller is currently scoped into, as returned by
// `GET /axonia/organizations/current`: `kind` routes the app shell (ENTERPRISE vs HUB),
// `memberCount` backs the tenant settings hero, and `communicationsEnabled` is the
// organization-wide kill switch the same endpoint's `PUT .../communications-enabled`
// flips; `id`, `name` and `lifecycleState` are what else it answers.
// Not exported: only currentOrganizationSchema consumes it today (knip gate).
const organizationKindSchema = z.enum(["ENTERPRISE", "HUB"]);

export const currentOrganizationSchema = z.object({
  id: z.string(),
  name: z.string(),
  kind: organizationKindSchema,
  memberCount: z.number().optional(),
  // Optional rather than defaulted: the backend always sends it, but treating it as
  // optional here means an older fixture or test double that omits it still type-checks
  // rather than being forced to know about a switch it isn't exercising. Every reader
  // falls back to `true` (on), the domain's own on-by-default stance.
  communicationsEnabled: z.boolean().optional(),
});

export type CurrentOrganization = z.infer<typeof currentOrganizationSchema>;
