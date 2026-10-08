import { DELETED_MEMBER } from "@/features/identity/domain/user";
import { declaredOn } from "@/lib/declared-on";
import {
  MAIL_SECURITIES,
  type MailFieldChange,
  type MailRefusal,
  type MailSecurity,
  type MailServerView,
  type RefusedField,
  type TestAccepted,
} from "@/selfhosted/features/mail-server/domain/mail-server";

/** STARTTLS and TLS by their own names, no encryption as "None". */
export function securityLabel(security: MailSecurity): string {
  return security === "NONE" ? "None" : security;
}

export const REFUSAL_LABELS: Record<MailRefusal, string> = {
  connection_failed: "Connection failed",
  timeout: "No answer in time",
  tls_required: "The server requires TLS",
  tls_failed: "TLS handshake failed",
  authentication_failed: "Authentication failed",
  sender_refused: "Sender address refused",
  recipient_refused: "Recipient address refused",
};

/** What a refused call means, by the problem's code; a code missing here falls back to the
 *  product's generic failure line. */
export const REFUSED_CALLS: Readonly<Record<string, string>> = {
  untested: "Test again before saving.",
  stale_revision:
    "Changed by someone else since this form opened. Cancel to see the change.",
  mail_test_rate_limited: "Too many test mails. Try again in a few minutes.",
  field_invalid: "A value was refused.",
};

/** A stale clear, said inside the dialog, whose own Cancel leaves the form open. */
export const STALE_CLEAR =
  "Changed by someone else since this form opened. Close the form to see the change.";

/** A one-line input already drops a pasted line break, so what a reader can get in is a tab or
 *  another character that draws nothing. */
export const USERNAME_HIDDEN_CHARACTER =
  "Username holds a tab or another hidden character.";
export const PASSWORD_HIDDEN_CHARACTER =
  "Password holds a tab or another hidden character.";
export const FROM_NAME_HIDDEN_CHARACTER =
  "From name holds a tab or another hidden character.";
export const HOST_FORMAT =
  "Host is a server name with no spaces or hidden characters.";

/**
 * A value the installation refused, by its field and what the form holds there. The problem names
 * the field, never which rule it broke, so a username or a password tells by whether the form sent
 * one: a sent one broke its format, an absent one was needed.
 */
export type Refusal = Readonly<{
  field: RefusedField;
  setByDeployment: boolean;
  sent: boolean;
}>;

function credentialRefusalLine(
  field: "username" | "password",
  sent: boolean,
): string {
  if (field === "username") {
    return sent ? USERNAME_HIDDEN_CHARACTER : "A password needs a username.";
  }
  return sent ? PASSWORD_HIDDEN_CHARACTER : "Enter the password again.";
}

function valueRefusalLine(
  field: Exclude<RefusedField, "username" | "password">,
): string {
  switch (field) {
    case "host":
      return HOST_FORMAT;
    case "port":
      return "Port is a number from 1 to 65535.";
    case "fromAddress":
      return "From address is not a mail address.";
    case "fromName":
      return FROM_NAME_HIDDEN_CHARACTER;
    case "recipient":
      return "Recipient is not a mail address.";
  }
}

/** A refused value, said on its own field. One the deployment sets is the deployment's to
 *  correct, so the line says whose it is. */
export function refusedValueLine({
  field,
  setByDeployment,
  sent,
}: Refusal): string {
  if (setByDeployment) {
    return field === "password"
      ? "The deployment sets this password for the current server."
      : "Set by the deployment and refused.";
  }
  return field === "username" || field === "password"
    ? credentialRefusalLine(field, sent)
    : valueRefusalLine(field);
}

const FIELD_LABELS: Record<MailFieldChange["field"], string> = {
  host: "Host",
  port: "Port",
  security: "Security",
  username: "Username",
  password: "Password",
  fromAddress: "From address",
  fromName: "From name",
};

const CLOCK = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

function clockTime(iso: string): string {
  return CLOCK.format(new Date(iso));
}

/** How the facts line names a security: "None" stands beside its label in the form, and alone
 *  it would not say what is missing. */
function securityFact(security: MailSecurity): string {
  return security === "NONE" ? "No encryption" : security;
}

/** "smtp.acme.com:587, STARTTLS", or "smtp.acme.com:25, No encryption". */
export function serverFacts(view: MailServerView): string | null {
  const { host, port, security } = view;
  if (host.value === null) return null;
  const address =
    port.value === null ? host.value : `${host.value}:${port.value}`;
  return security.value === null
    ? address
    : `${address}, ${securityFact(security.value)}`;
}

/** "From Acme Communities <axonia@acme.com>". */
export function senderFacts(view: MailServerView): string | null {
  const address = view.fromAddress.value;
  if (address === null) return null;
  const name = view.fromName.value;
  return name === null ? `From ${address}` : `From ${name} <${address}>`;
}

/** Who made a change: `null` is an actor since erased or no longer a member. */
export function actorLabel(actorName: string | null): string {
  return actorName ?? DELETED_MEMBER;
}

export function lastChangeSummary(
  change: Readonly<{ occurredAt: string; actorName: string | null }>,
): string {
  return `History, last changed by ${actorLabel(change.actorName)} on ${declaredOn(change.occurredAt)}`;
}

export function changedAt(iso: string): string {
  return `${declaredOn(iso)} at ${clockTime(iso)}`;
}

export function acceptedLine(outcome: TestAccepted): string {
  return `Test mail accepted by ${outcome.host} at ${clockTime(outcome.acceptedAt)}.`;
}

/** What the card warns of at rest, in the order an Owner would act on it. */
export function warningsOf(view: MailServerView): string[] {
  const failure = view.lastFailure;
  return [
    ...(view.changedSinceTest
      ? ["Changed by the deployment since the last test."]
      : []),
    ...(failure === null
      ? []
      : [
          `Last refusal: ${REFUSAL_LABELS[failure.reason]}, ${changedAt(failure.at)}.`,
        ]),
    ...(view.state === "secret_unreadable"
      ? ["Mail is not sent until the password is entered again."]
      : []),
  ];
}

type Edit = MailFieldChange & Readonly<{ from: string; to: string }>;

/** Security is stored by its name, "NONE"; the card says "None" everywhere. */
function valueLabel(field: MailFieldChange["field"], value: string): string {
  const security =
    field === "security"
      ? MAIL_SECURITIES.find((option) => option === value)
      : undefined;
  return security === undefined ? value : securityLabel(security);
}

function isEdit(change: MailFieldChange): change is Edit {
  return change.kind === "set" && change.from !== null && change.to !== null;
}

function listed(
  prefix: string,
  changes: ReadonlyArray<MailFieldChange>,
): string[] {
  if (changes.length === 0) return [];
  const fields = changes.map((change) =>
    FIELD_LABELS[change.field].toLowerCase(),
  );
  return [`${prefix}: ${fields.join(", ")}`];
}

/** One history row's lines: each edit with its previous value, then the fields set where none
 *  was (and a replaced password), then the fields cleared. Never a secret: the server sends none. */
export function changeLines(changes: ReadonlyArray<MailFieldChange>): string[] {
  return [
    ...changes
      .filter(isEdit)
      .map(
        (change) =>
          `${FIELD_LABELS[change.field]}: ${valueLabel(change.field, change.from)} → ${valueLabel(change.field, change.to)}`,
      ),
    ...listed(
      "Set",
      changes.filter(
        (change) =>
          change.kind === "replaced" ||
          (change.kind === "set" && !isEdit(change)),
      ),
    ),
    ...listed(
      "Cleared",
      changes.filter((change) => change.kind === "cleared"),
    ),
  ];
}
