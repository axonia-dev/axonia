import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";
import { httpMailServerGateway } from "@/selfhosted/features/mail-server/adapters/http-mail-server-gateway";
import type { MailServerValues } from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  ACCEPTED,
  CONFIGURED,
  FROM_NAME_CHANGE,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import { bodyOf, jsonResponse, urlOf } from "@/test/fetch";

const gateway = httpMailServerGateway;

const VALUES: MailServerValues = {
  host: "smtp.acme.com",
  port: 587,
  security: "STARTTLS",
  username: "axonia",
  password: null,
  fromAddress: "axonia@acme.com",
  fromName: "Acme Communities",
};

function answer(body: unknown, status = 200) {
  return vi
    .spyOn(globalThis, "fetch")
    .mockResolvedValue(jsonResponse(body, status));
}

function problem(status: number, code: string, detail?: string) {
  return answer(
    {
      type: "about:blank",
      title: "Refused",
      status,
      code,
      ...(detail === undefined ? {} : { detail }),
    },
    status,
  );
}

function callOf(spy: ReturnType<typeof answer>) {
  const [input, init] = spy.mock.calls[0] ?? [];
  return { url: urlOf(input), method: init?.method ?? "GET" };
}

afterEach(() => {
  vi.restoreAllMocks();
});

/** Part B's three `MailServerView` examples (`phase-6-replan-b.md`, C-3), verbatim: the seam's one copy. */
const CONTRACT_EXAMPLES = {
  configured: {
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
    lastChange: {
      occurredAt: "2026-10-02T14:02:11.123Z",
      actorName: "Ana Ruiz",
    },
  },
  "failing, saved by an erased member": {
    state: "delivery_failing",
    revision: 3,
    host: { value: "smtp.acme.com", lockedBy: null },
    port: { value: 587, lockedBy: null },
    security: { value: "STARTTLS", lockedBy: null },
    username: { value: "axonia", lockedBy: null },
    password: { set: true, readable: true, lockedBy: null },
    fromAddress: { value: "axonia@acme.com", lockedBy: null },
    fromName: { value: "Acme Communities", lockedBy: "AXONIA_MAIL_FROM_NAME" },
    changedSinceTest: false,
    lastFailure: {
      reason: "authentication_failed",
      at: "2026-10-03T09:15:00.250Z",
    },
    lastChange: { occurredAt: "2026-10-02T14:02:11.123Z", actorName: null },
  },
  "never saved": {
    state: "not_configured",
    revision: 0,
    host: { value: null, lockedBy: null },
    port: { value: null, lockedBy: null },
    security: { value: null, lockedBy: null },
    username: { value: null, lockedBy: null },
    password: { set: false, readable: true, lockedBy: null },
    fromAddress: { value: null, lockedBy: null },
    fromName: { value: null, lockedBy: null },
    changedSinceTest: false,
    lastFailure: null,
    lastChange: null,
  },
};

describe("the mail server over HTTP", () => {
  it.each(Object.entries(CONTRACT_EXAMPLES))(
    "reads a %s mail server exactly as the contract writes it",
    async (_, example) => {
      // given
      const spy = answer(example);

      // when
      const actual = await gateway.read();

      // then
      expect(actual).toEqual(example);
      expect(callOf(spy).url).toMatch(/\/axonia\/mail-server$/);
      expect(callOf(spy).method).toBe("GET");
    },
  );

  it("asks whether mail is configured", async () => {
    // given
    const spy = answer({ configured: false });

    // when
    const actual = await gateway.isConfigured();

    // then
    expect(actual).toBe(false);
    expect(callOf(spy).url).toMatch(/\/axonia\/mail-server\/status$/);
  });

  it("tests the candidate values against a recipient", async () => {
    // given
    const spy = answer({ ...ACCEPTED, expiresAt: "2026-10-02T21:12:11Z" });

    // when
    const actual = await gateway.test(VALUES, "ana.ruiz@acme.com");

    // then
    expect(actual).toEqual(ACCEPTED);
    expect(callOf(spy).url).toMatch(/\/axonia\/mail-server\/test$/);
    expect(callOf(spy).method).toBe("POST");
    expect(bodyOf(spy)).toEqual({
      values: VALUES,
      recipient: "ana.ruiz@acme.com",
    });
  });

  it("answers a refusal as an outcome carrying the server's reply line", async () => {
    // given
    problem(
      422,
      "authentication_failed",
      "535 5.7.8 Authentication credentials invalid",
    );

    // when
    const actual = await gateway.test(VALUES, "ana.ruiz@acme.com");

    // then
    expect(actual).toEqual({
      accepted: false,
      reason: "authentication_failed",
      reply: "535 5.7.8 Authentication credentials invalid",
    });
  });

  it("answers a refusal with no reply line as an outcome with none", async () => {
    // given
    problem(422, "timeout");

    // when
    const actual = await gateway.test(VALUES, "ana.ruiz@acme.com");

    // then
    expect(actual).toEqual({ accepted: false, reason: "timeout", reply: null });
  });

  it("lets a rate-limited test fail as an error", async () => {
    // given
    problem(
      429,
      "mail_test_rate_limited",
      "Too many test mails. Try again later.",
    );

    // when
    const actual = gateway.test(VALUES, "ana.ruiz@acme.com");

    // then
    await expect(actual).rejects.toBeInstanceOf(ApiError);
    await expect(actual).rejects.toMatchObject({
      code: "mail_test_rate_limited",
    });
  });

  it("saves the tested values with the test's token and the revision they were read at", async () => {
    // given
    const spy = answer(CONFIGURED);

    // when
    const actual = await gateway.save(VALUES, {
      testToken: ACCEPTED.testToken,
      baseRevision: 3,
    });

    // then
    expect(actual).toEqual(CONFIGURED);
    expect(callOf(spy).method).toBe("PUT");
    expect(bodyOf(spy)).toEqual({
      values: VALUES,
      testToken: ACCEPTED.testToken,
      baseRevision: 3,
    });
  });

  it("clears the mail server at the revision it was read at", async () => {
    // given
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    // when
    await gateway.clear(3);

    // then
    expect(callOf(spy).url).toMatch(/\/axonia\/mail-server\?baseRevision=3$/);
    expect(callOf(spy).method).toBe("DELETE");
  });

  it("lets a clear of a mail server changed since it was read fail as stale", async () => {
    // given
    problem(
      409,
      "stale_revision",
      "The mail server was changed since it was read. Reload it.",
    );

    // when
    const actual = gateway.clear(3);

    // then
    await expect(actual).rejects.toMatchObject({
      status: 409,
      code: "stale_revision",
    });
  });

  it("reads the history a page of twenty at a time", async () => {
    // given
    const spy = answer({
      content: [FROM_NAME_CHANGE],
      page: { size: 20, number: 1, totalElements: 21, totalPages: 2 },
    });

    // when
    const actual = await gateway.changes(1);

    // then
    expect(actual.content).toEqual([FROM_NAME_CHANGE]);
    expect(callOf(spy).url).toMatch(
      /\/axonia\/mail-server\/changes\?page=1&size=20$/,
    );
  });
});
