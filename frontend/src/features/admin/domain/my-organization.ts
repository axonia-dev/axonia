import { z } from "zod";

// A single organization the signed-in caller may operate within, as returned by
// `GET /axonia/me/organizations`. Organization ids are strings on this contract.
// `kind` is null when the decoration read (RLS-scoped, unlike the membership list
// itself) can't see the tenant's registry row - a legitimate outcome, not an error.
export const myOrganizationSchema = z.object({
  id: z.string(),
  name: z.string(),
  badgeUrl: z.string().nullable().optional(),
  accentColor: z.string().nullable().optional(),
  kind: z.enum(["HUB", "ENTERPRISE"]).nullable().optional(),
});

export type MyOrganization = z.infer<typeof myOrganizationSchema>;
