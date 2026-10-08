import type {
  MailServerChange,
  MailServerView,
  TestAccepted,
} from "@/selfhosted/features/mail-server/domain/mail-server";

/** Configured, the sender name set by the deployment, last changed by Ana Ruiz at 14:03 on
 *  2 Oct 2026 in the suite's zone (America/Los_Angeles). */
export const CONFIGURED: MailServerView = {
  state: "configured",
  revision: 3,
  host: { value: "smtp.acme.com", lockedBy: null },
  port: { value: 587, lockedBy: null },
  security: { value: "STARTTLS", lockedBy: null },
  username: { value: "axonia", lockedBy: null },
  password: { set: true, readable: true, lockedBy: null },
  fromAddress: { value: "axonia@acme.com", lockedBy: null },
  fromName: { value: "Acme Communities", lockedBy: "AXONIA_MAIL_FROM_NAME" },
  changedSinceTest: false,
  lastFailure: null,
  lastChange: { occurredAt: "2026-10-02T21:03:00.000Z", actorName: "Ana Ruiz" },
};

const UNSET = { value: null, lockedBy: null };

export const NOT_CONFIGURED: MailServerView = {
  state: "not_configured",
  revision: 0,
  host: UNSET,
  port: UNSET,
  security: UNSET,
  username: UNSET,
  password: { set: false, readable: true, lockedBy: null },
  fromAddress: UNSET,
  fromName: UNSET,
  changedSinceTest: false,
  lastFailure: null,
  lastChange: null,
};

export function viewWith(overrides: Partial<MailServerView>): MailServerView {
  return { ...CONFIGURED, ...overrides };
}

/** Accepted at 14:02 in the suite's zone. */
export const ACCEPTED: TestAccepted = {
  accepted: true,
  host: "smtp.acme.com",
  acceptedAt: "2026-10-02T21:02:11.123Z",
  testToken: "v1.1790950331.qlAAYzgI0iY2zhAsxRT_eTov4_Gs8lSR_NHX18HN0Nw",
};

export const FROM_NAME_CHANGE: MailServerChange = {
  id: 12,
  occurredAt: "2026-10-02T21:03:00.000Z",
  actorName: "Ana Ruiz",
  changes: [
    { field: "fromName", kind: "set", from: "axonia", to: "Acme Communities" },
  ],
};

/** By an actor since erased: the server sends `null`, the card draws "Deleted member". */
export const FIRST_SETUP: MailServerChange = {
  id: 3,
  occurredAt: "2026-09-30T16:00:00.000Z",
  actorName: null,
  changes: [
    { field: "host", kind: "set", from: null, to: "smtp.acme.com" },
    { field: "password", kind: "replaced", from: null, to: null },
  ],
};
