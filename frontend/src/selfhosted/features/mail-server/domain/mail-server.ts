import { z } from "zod";

import { pageSchema } from "@/lib/page";

export const MAIL_SECURITIES = ["STARTTLS", "TLS", "NONE"] as const;
export type MailSecurity = (typeof MAIL_SECURITIES)[number];

export const MAIL_REFUSALS = [
  "connection_failed",
  "timeout",
  "tls_required",
  "tls_failed",
  "authentication_failed",
  "sender_refused",
  "recipient_refused",
] as const;
export type MailRefusal = (typeof MAIL_REFUSALS)[number];

const MAIL_FIELDS = [
  "host",
  "port",
  "security",
  "username",
  "password",
  "fromAddress",
  "fromName",
] as const;

/** One value as the installation resolves it: the deployment's when `lockedBy` names its
 *  variable, the stored one otherwise. */
function settingSchema<T extends z.ZodType>(value: T) {
  return z.object({ value: value.nullable(), lockedBy: z.string().nullable() });
}

export const mailServerViewSchema = z.object({
  state: z.enum([
    "not_configured",
    "configured",
    "changed_since_test",
    "delivery_failing",
    "secret_unreadable",
  ]),
  revision: z.number().int(),
  host: settingSchema(z.string()),
  port: settingSchema(z.number().int()),
  security: settingSchema(z.enum(MAIL_SECURITIES)),
  username: settingSchema(z.string()),
  password: z.object({
    set: z.boolean(),
    readable: z.boolean(),
    lockedBy: z.string().nullable(),
  }),
  fromAddress: settingSchema(z.string()),
  fromName: settingSchema(z.string()),
  changedSinceTest: z.boolean(),
  lastFailure: z
    .object({ reason: z.enum(MAIL_REFUSALS), at: z.string() })
    .nullable(),
  /** `actorName` is `null` for an actor since erased or gone, drawn as `DELETED_MEMBER`. */
  lastChange: z
    .object({ occurredAt: z.string(), actorName: z.string().nullable() })
    .nullable(),
});
export type MailServerView = z.infer<typeof mailServerViewSchema>;
export type MailServerState = MailServerView["state"];

export const mailServerStatusSchema = z.object({ configured: z.boolean() });

const mailServerChangeSchema = z.object({
  id: z.number(),
  occurredAt: z.string(),
  actorName: z.string().nullable(),
  changes: z.array(
    z.object({
      field: z.enum(MAIL_FIELDS),
      kind: z.enum(["set", "replaced", "cleared"]),
      from: z.string().nullable(),
      to: z.string().nullable(),
    }),
  ),
});
export type MailServerChange = z.infer<typeof mailServerChangeSchema>;
export type MailFieldChange = MailServerChange["changes"][number];
export const mailServerChangesSchema = pageSchema(mailServerChangeSchema);

export const testAcceptedSchema = z.object({
  accepted: z.literal(true),
  host: z.string(),
  acceptedAt: z.string(),
  testToken: z.string(),
  expiresAt: z.string(),
});

/** What a test or a save sends: every key, `null` for an empty field, and a `null` password to
 *  keep the stored one. */
export type MailServerValues = Readonly<{
  host: string | null;
  port: number | null;
  security: MailSecurity | null;
  username: string | null;
  password: string | null;
  fromAddress: string | null;
  fromName: string | null;
}>;

export type TestAccepted = Readonly<{
  accepted: true;
  host: string;
  acceptedAt: string;
  testToken: string;
}>;

/** The mail server's refusal of a test: an answer about the server, not a failed request. */
type TestRefused = Readonly<{
  accepted: false;
  reason: MailRefusal;
  reply: string | null;
}>;

export type TestOutcome = TestAccepted | TestRefused;

export type SaveProof = Readonly<{ testToken: string; baseRevision: number }>;

/** The fields a refused value can name that the form can mark: a value, or the test's recipient. */
export type RefusedField =
  Exclude<(typeof MAIL_FIELDS)[number], "security"> | "recipient";

/** A value the installation refused, named by its field. */
export class MailValueRefused extends Error {
  readonly field: RefusedField;

  constructor(field: RefusedField) {
    super(`The ${field} value was refused.`);
    this.name = "MailValueRefused";
    this.field = field;
  }
}
