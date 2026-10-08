import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";
import { httpMailServerGateway } from "@/selfhosted/features/mail-server/adapters/http-mail-server-gateway";
import {
  type MailServerValues,
  MailValueRefused,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import { CONFIGURED } from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import { jsonResponse } from "@/test/fetch";

const gateway = httpMailServerGateway;

const VALUES: MailServerValues = {
  host: "smtp.acme.com",
  port: 587,
  security: "STARTTLS",
  username: "axonia",
  password: null,
  fromAddress: "axonia@acme,com",
  fromName: null,
};

const PROOF = { testToken: "v1.token", baseRevision: 3 };

function answer(body: unknown, status = 200) {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(body, status));
}

/** The installation's 400 for a refused value, naming it in `field`; `field: undefined` leaves
 *  that property out, with the detail still spelling a field. */
function fieldInvalid(field: string | undefined, rule: string) {
  answer(
    {
      type: "about:blank",
      title: "Bad Request",
      status: 400,
      detail: `${field ?? "values.host"}: ${rule}`,
      code: "field_invalid",
      ...(field === undefined ? {} : { field }),
    },
    400,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("the mail server gateway's refused values", () => {
  it("names a refused recipient", async () => {
    // given
    fieldInvalid("recipient", "must be one bare mail address.");

    // when
    const actual = gateway.test(VALUES, "ana@acme,com");

    // then
    await expect(actual).rejects.toBeInstanceOf(MailValueRefused);
    await expect(actual).rejects.toMatchObject({ field: "recipient" });
  });

  it("names a refused value of a test by its field", async () => {
    // given
    fieldInvalid("values.fromAddress", "must be one bare mail address.");

    // when
    const actual = gateway.test(VALUES, "ana@acme.com");

    // then
    await expect(actual).rejects.toMatchObject({ field: "fromAddress" });
  });

  it("names a refused value of a save by its field", async () => {
    // given
    fieldInvalid(
      "values.password",
      "the deployment sets this password for the current server.",
    );

    // when
    const actual = gateway.save(VALUES, PROOF);

    // then
    await expect(actual).rejects.toBeInstanceOf(MailValueRefused);
    await expect(actual).rejects.toMatchObject({ field: "password" });
  });

  it("reads the field from the problem's field alone, never from its detail", async () => {
    // given
    fieldInvalid(undefined, "Host is a server name with no spaces.");

    // when
    const actual = gateway.test(VALUES, "ana@acme.com");

    // then
    await expect(actual).rejects.toBeInstanceOf(ApiError);
    await expect(actual).rejects.not.toBeInstanceOf(MailValueRefused);
  });

  it("lets a refusal naming no field of the form fail as it came", async () => {
    // given
    fieldInvalid("baseRevision", "required.");

    // when
    const actual = gateway.save(VALUES, PROOF);

    // then
    await expect(actual).rejects.toBeInstanceOf(ApiError);
    await expect(actual).rejects.toMatchObject({ code: "field_invalid" });
  });

  it.each<[string, object]>([
    ["a setting's value", { ...CONFIGURED, host: { lockedBy: null } }],
    ["the last failure", { ...CONFIGURED, lastFailure: undefined }],
    ["the last change", { ...CONFIGURED, lastChange: undefined }],
  ])(
    "fails loudly on a view missing %s, rather than reading it as unset",
    async (_missing, view) => {
      // given
      answer(view);

      // when
      const actual = gateway.read();

      // then
      await expect(actual).rejects.toThrow();
    },
  );
});
