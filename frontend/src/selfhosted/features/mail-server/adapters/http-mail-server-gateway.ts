import { ApiError, apiFetch, apiFetchVoid } from "@/lib/api-client";
import {
  MAIL_REFUSALS,
  type MailRefusal,
  mailServerChangesSchema,
  mailServerStatusSchema,
  mailServerViewSchema,
  MailValueRefused,
  type RefusedField,
  testAcceptedSchema,
  type TestOutcome,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import type { MailServerGateway } from "@/selfhosted/features/mail-server/domain/mail-server-gateway";

const PATH = "/axonia/mail-server";
const HISTORY_PAGE_SIZE = 20;
const REFUSALS: ReadonlySet<string> = new Set(MAIL_REFUSALS);

/** The `field` of a 400 `field_invalid`, as the installation spells it, for a field the form marks. */
const NAMED_FIELDS: ReadonlyMap<string, RefusedField> = new Map([
  ["values.host", "host"],
  ["values.port", "port"],
  ["values.username", "username"],
  ["values.password", "password"],
  ["values.fromAddress", "fromAddress"],
  ["values.fromName", "fromName"],
  ["recipient", "recipient"],
]);

function isRefusal(code: string | undefined): code is MailRefusal {
  return code !== undefined && REFUSALS.has(code);
}

/** A 400 naming, in its `field` property, a value the form can mark. */
function refusedValue(error: unknown): MailValueRefused | undefined {
  const field =
    error instanceof ApiError &&
    error.status === 400 &&
    error.code === "field_invalid"
      ? NAMED_FIELDS.get(error.field ?? "")
      : undefined;
  return field === undefined ? undefined : new MailValueRefused(field);
}

/** A value refused by name becomes `MailValueRefused`; anything else fails as it came. */
function failed(error: unknown): never {
  throw refusedValue(error) ?? error;
}

/** A 422 naming a refusal is the mail server's answer; anything else is a failed request. */
function refusalOf(error: unknown): TestOutcome {
  if (
    error instanceof ApiError &&
    error.status === 422 &&
    isRefusal(error.code)
  ) {
    return { accepted: false, reason: error.code, reply: error.detail ?? null };
  }
  return failed(error);
}

export const httpMailServerGateway: MailServerGateway = {
  async read() {
    return mailServerViewSchema.parse(await apiFetch<unknown>(PATH));
  },

  async isConfigured() {
    const data = await apiFetch<unknown>(`${PATH}/status`);
    return mailServerStatusSchema.parse(data).configured;
  },

  async test(values, recipient) {
    try {
      const data = await apiFetch<unknown>(`${PATH}/test`, {
        method: "POST",
        body: JSON.stringify({ values, recipient }),
      });
      const { host, acceptedAt, testToken } = testAcceptedSchema.parse(data);
      return { accepted: true, host, acceptedAt, testToken };
    } catch (error) {
      return refusalOf(error);
    }
  },

  async save(values, proof) {
    try {
      const data = await apiFetch<unknown>(PATH, {
        method: "PUT",
        body: JSON.stringify({ values, ...proof }),
      });
      return mailServerViewSchema.parse(data);
    } catch (error) {
      return failed(error);
    }
  },

  async clear(baseRevision) {
    await apiFetchVoid(`${PATH}?baseRevision=${baseRevision}`, {
      method: "DELETE",
    });
  },

  async changes(page) {
    const data = await apiFetch<unknown>(
      `${PATH}/changes?page=${page}&size=${HISTORY_PAGE_SIZE}`,
    );
    return mailServerChangesSchema.parse(data);
  },
};
