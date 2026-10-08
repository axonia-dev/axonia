import type {
  MailSecurity,
  MailServerValues,
  MailServerView,
  MailValueRefused,
  RefusedField,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  FROM_NAME_HIDDEN_CHARACTER,
  HOST_FORMAT,
  PASSWORD_HIDDEN_CHARACTER,
  refusedValueLine,
  USERNAME_HIDDEN_CHARACTER,
} from "@/selfhosted/features/mail-server/domain/mail-server-copy";

const DEFAULT_PORT = 587;
const DEFAULT_SECURITY: MailSecurity = "STARTTLS";
const HOST = /^[^\s\p{Cc}]+$/u;
const MAIL_ADDRESS = /^[^\s@]+@[^\s@]+$/;
const PORT = /^\d{1,5}$/;
const CONTROL_CHARACTER = /\p{Cc}/u;

/**
 * The open form: text as typed, and whether a new password replaces the stored one. It does when
 * Replace was pressed (`replaceRequested`, set from the start when nothing readable is stored), or
 * while the draft names another server than the stored password's (`replacingPassword` follows).
 */
export type MailServerDraft = Readonly<{
  host: string;
  port: string;
  security: MailSecurity;
  username: string;
  password: string;
  replaceRequested: boolean;
  replacingPassword: boolean;
  fromAddress: string;
  fromName: string;
}>;

type DraftField = Exclude<RefusedField, "recipient">;
export type DraftProblems = Partial<Record<DraftField, string>>;

const DRAFT_FIELDS: ReadonlyArray<DraftField> = [
  "host",
  "port",
  "username",
  "password",
  "fromAddress",
  "fromName",
];

/** A stored password that can be read is kept until replaced; none, or one that cannot be read,
 *  is asked for; one the deployment sets never is. */
function startsReplacing(view: MailServerView): boolean {
  const { set, readable, lockedBy } = view.password;
  return lockedBy === null && (!set || !readable);
}

export function draftOf(view: MailServerView): MailServerDraft {
  return {
    host: view.host.value ?? "",
    port: String(view.port.value ?? DEFAULT_PORT),
    security: view.security.value ?? DEFAULT_SECURITY,
    username: view.username.value ?? "",
    password: "",
    replaceRequested: startsReplacing(view),
    replacingPassword: startsReplacing(view),
    fromAddress: view.fromAddress.value ?? "",
    fromName: view.fromName.value ?? "",
  };
}

function blankToNull(text: string): string | null {
  const trimmed = text.trim();
  return trimmed === "" ? null : trimmed;
}

function portOf(text: string): number | null {
  const trimmed = text.trim();
  return PORT.test(trimmed) ? Number(trimmed) : null;
}

/** What a test or a save sends. The password travels exactly as typed, and only while it replaces
 *  the stored one. */
export function valuesOf(draft: MailServerDraft): MailServerValues {
  return {
    host: blankToNull(draft.host),
    port: portOf(draft.port),
    security: draft.security,
    username: blankToNull(draft.username),
    password:
      draft.replacingPassword && draft.password !== "" ? draft.password : null,
    fromAddress: blankToNull(draft.fromAddress),
    fromName: blankToNull(draft.fromName),
  };
}

/** Only a move to no encryption weakens: STARTTLS and TLS both encrypt. */
function weakens(stored: MailSecurity | null, drafted: MailSecurity): boolean {
  return stored !== null && stored !== "NONE" && drafted === "NONE";
}

/** The installation keeps a stored password only for the server it was stored for: the same host,
 *  port and username, and no weaker security. A draft with no username sends no password at all. */
function movesStoredPassword(
  draft: MailServerDraft,
  view: MailServerView,
): boolean {
  const { set, readable, lockedBy } = view.password;
  const values = valuesOf(draft);
  if (!set || !readable || lockedBy !== null || values.username === null) {
    return false;
  }
  return (
    values.host !== view.host.value ||
    values.port !== view.port.value ||
    values.username !== view.username.value ||
    weakens(view.security.value, draft.security)
  );
}

/** The draft after an edit. A move to another server asks for the password; moving back draws
 *  the stored one again and forgets what was typed. */
export function edited(
  draft: MailServerDraft,
  patch: Partial<MailServerDraft>,
  view: MailServerView,
): MailServerDraft {
  const next = { ...draft, ...patch };
  const replacing = next.replaceRequested || movesStoredPassword(next, view);
  return {
    ...next,
    replacingPassword: replacing,
    password: replacing ? next.password : "",
  };
}

/** Whether Replace can be undone: the stored password is set and readable, the deployment does
 *  not set it, and the draft still names the server it was stored for. */
export function canKeepStored(
  draft: MailServerDraft,
  view: MailServerView,
): boolean {
  const { set, readable, lockedBy } = view.password;
  return (
    draft.replaceRequested &&
    set &&
    readable &&
    lockedBy === null &&
    !movesStoredPassword(draft, view)
  );
}

/** What a test vouches for, or a refusal speaks of: exactly these values. */
export function keyOf(values: MailServerValues): string {
  return JSON.stringify(values);
}

export function testKey(draft: MailServerDraft): string {
  return keyOf(valuesOf(draft));
}

function hostProblem(host: string | null): string | undefined {
  if (host === null) return "Host is required.";
  return HOST.test(host) ? undefined : HOST_FORMAT;
}

function portProblem(port: number | null): string | undefined {
  return port !== null && port >= 1 && port <= 65_535
    ? undefined
    : "Port is a number from 1 to 65535.";
}

function fromAddressProblem(address: string | null): string | undefined {
  if (address === null) return "From address is required.";
  return MAIL_ADDRESS.test(address)
    ? undefined
    : "From address is not a mail address.";
}

/** The installation keeps a username, a sender name and a password to one line. The password's
 *  1024 character limit is the input's own `maxLength`, so no form can break it. */
function hiddenCharacterProblem(
  value: string | null,
  problem: string,
): string | undefined {
  return value !== null && CONTROL_CHARACTER.test(value) ? problem : undefined;
}

/** As the installation reads them: a password the deployment sets needs a username too. */
function credentialProblems(
  draft: MailServerDraft,
  values: MailServerValues,
  view: MailServerView,
): DraftProblems {
  if (
    values.username !== null &&
    draft.replacingPassword &&
    values.password === null
  ) {
    return {
      password: draft.replaceRequested
        ? "A username needs a password."
        : "Enter the password again for another server.",
    };
  }
  const hasPassword =
    values.password !== null || view.password.lockedBy !== null;
  if (values.username === null && hasPassword) {
    return { username: "A password needs a username." };
  }
  return {};
}

/** Who sets a field: the deployment's variable, or `null` for the form. */
function lockedBy(view: MailServerView, field: DraftField): string | null {
  switch (field) {
    case "host":
      return view.host.lockedBy;
    case "port":
      return view.port.lockedBy;
    case "username":
      return view.username.lockedBy;
    case "password":
      return view.password.lockedBy;
    case "fromAddress":
      return view.fromAddress.lockedBy;
    case "fromName":
      return view.fromName.lockedBy;
  }
}

type Found = readonly [DraftField, string | undefined];

function isProblem(found: Found): found is readonly [DraftField, string] {
  return found[1] !== undefined;
}

/** What stops a test, by field. A field the deployment sets is the deployment's to get right. */
export function draftProblems(
  draft: MailServerDraft,
  view: MailServerView,
): DraftProblems {
  const values = valuesOf(draft);
  const credentials = credentialProblems(draft, values, view);
  const found: ReadonlyArray<Found> = [
    ["host", hostProblem(values.host)],
    ["port", portProblem(values.port)],
    [
      "username",
      credentials.username ??
        hiddenCharacterProblem(values.username, USERNAME_HIDDEN_CHARACTER),
    ],
    [
      "password",
      credentials.password ??
        hiddenCharacterProblem(values.password, PASSWORD_HIDDEN_CHARACTER),
    ],
    ["fromAddress", fromAddressProblem(values.fromAddress)],
    [
      "fromName",
      hiddenCharacterProblem(values.fromName, FROM_NAME_HIDDEN_CHARACTER),
    ],
  ];
  return Object.fromEntries(
    found.filter(isProblem).filter(([field]) => lockedBy(view, field) === null),
  );
}

export function recipientProblem(recipient: string): string | undefined {
  return MAIL_ADDRESS.test(recipient.trim())
    ? undefined
    : "Recipient is not a mail address.";
}

/** Whether the form sent a value for the field: only a username or a password can be absent
 *  when the installation refuses it. */
function sentFor(field: DraftField, values: MailServerValues): boolean {
  if (field === "username") return values.username !== null;
  if (field === "password") return values.password !== null;
  return true;
}

/** A value the installation refused, marked on its field, for the values that were sent. */
export function refusalProblems(
  refused: MailValueRefused | undefined,
  sent: MailServerValues | undefined,
  view: MailServerView,
): DraftProblems {
  const field = DRAFT_FIELDS.find((name) => name === refused?.field);
  if (field === undefined || sent === undefined) return {};
  const line = refusedValueLine({
    field,
    setByDeployment: lockedBy(view, field) !== null,
    sent: sentFor(field, sent),
  });
  return { [field]: line };
}
