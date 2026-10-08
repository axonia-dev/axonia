import { describe, expect, it } from "vitest";

import {
  CONFIGURED,
  NOT_CONFIGURED,
  viewWith,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  draftOf,
  draftProblems,
  type MailServerDraft,
  recipientProblem,
  testKey,
  valuesOf,
} from "@/selfhosted/features/mail-server/domain/mail-server-form";

describe("the mail server form", () => {
  it("opens an unconfigured server on the submission port with STARTTLS, asking for a password", () => {
    // when
    const actual = draftOf(NOT_CONFIGURED);

    // then
    expect(actual).toEqual({
      host: "",
      port: "587",
      security: "STARTTLS",
      username: "",
      password: "",
      replaceRequested: true,
      replacingPassword: true,
      fromAddress: "",
      fromName: "",
    });
  });

  it("keeps a stored password it can read until it is replaced", () => {
    // when
    const actual = draftOf(CONFIGURED);

    // then
    expect(actual.replacingPassword).toBe(false);
  });

  it("asks again for a stored password it cannot read", () => {
    // given
    const view = viewWith({
      state: "secret_unreadable",
      password: { set: true, readable: false, lockedBy: null },
    });

    // when
    const actual = draftOf(view);

    // then
    expect(actual.replacingPassword).toBe(true);
  });

  it("never asks for a password the deployment sets", () => {
    // given
    const view = viewWith({
      password: { set: true, readable: true, lockedBy: "AXONIA_MAIL_PASSWORD" },
    });

    // when
    const actual = draftOf(view);

    // then
    expect(actual.replacingPassword).toBe(false);
  });

  it("sends every field, a blank one as null and a kept password as null", () => {
    // given
    const draft = { ...draftOf(CONFIGURED), fromName: "  " };

    // when
    const actual = valuesOf(draft);

    // then
    expect(actual).toEqual({
      host: "smtp.acme.com",
      port: 587,
      security: "STARTTLS",
      username: "axonia",
      password: null,
      fromAddress: "axonia@acme.com",
      fromName: null,
    });
  });

  it("sends a typed password exactly as typed while it replaces the stored one", () => {
    // given
    const draft = {
      ...draftOf(CONFIGURED),
      replacingPassword: true,
      password: " s3cret ",
    };

    // when
    const actual = valuesOf(draft);

    // then
    expect(actual.password).toBe(" s3cret ");
  });

  it.each<[string, Partial<MailServerDraft>, Partial<MailServerDraft>]>([
    ["host", { host: "smtp.other.com" }, { host: "smtp.acme.com" }],
    ["port", { port: "2525" }, { port: "587" }],
    ["security", { security: "TLS" }, { security: "STARTTLS" }],
    ["username", { username: "relay" }, { username: "axonia" }],
    [
      "typed password",
      { replacingPassword: true, password: "s3cret" },
      { replacingPassword: false, password: "" },
    ],
    [
      "from address",
      { fromAddress: "mail@acme.com" },
      { fromAddress: "axonia@acme.com" },
    ],
    ["from name", { fromName: "Acme" }, { fromName: "Acme Communities" }],
  ])(
    "keys a test to the exact values: an edit of the %s changes the key and undoing it restores it",
    (_field, edit, undo) => {
      // given
      const opened = draftOf(CONFIGURED);
      const edited = { ...opened, ...edit };
      const undone = { ...edited, ...undo };

      // when
      const actual = [
        testKey(edited) === testKey(opened),
        testKey(undone) === testKey(opened),
      ];

      // then
      expect(actual).toEqual([false, true]);
    },
  );

  it("names every field that stops a test", () => {
    // given
    const draft = {
      ...draftOf(NOT_CONFIGURED),
      port: "70000",
      username: "axonia",
    };

    // when
    const actual = draftProblems(draft, NOT_CONFIGURED);

    // then
    expect(actual).toEqual({
      host: "Host is required.",
      port: "Port is a number from 1 to 65535.",
      password: "A username needs a password.",
      fromAddress: "From address is required.",
    });
  });

  it("refuses a host with a space and a sender that is not a mail address", () => {
    // given
    const draft = {
      ...draftOf(CONFIGURED),
      host: "smtp acme.com",
      fromAddress: "axonia",
    };

    // when
    const actual = draftProblems(draft, CONFIGURED);

    // then
    expect(actual).toEqual({
      host: "Host is a server name with no spaces or hidden characters.",
      fromAddress: "From address is not a mail address.",
    });
  });

  it("refuses a password with no username", () => {
    // given
    const draft = {
      ...draftOf(CONFIGURED),
      username: "",
      replacingPassword: true,
      password: "s3cret",
    };

    // when
    const actual = draftProblems(draft, CONFIGURED);

    // then
    expect(actual).toEqual({ username: "A password needs a username." });
  });

  it("leaves a field the deployment sets to the deployment", () => {
    // given
    const view = viewWith({
      host: { value: "smtp.acme.com", lockedBy: "AXONIA_MAIL_HOST" },
    });
    const draft = { ...draftOf(view), host: "" };

    // when
    const actual = draftProblems(draft, view);

    // then
    expect(actual).toEqual({});
  });

  it("finds nothing wrong with a relay that needs no authentication, even over an unreadable stored password", () => {
    // given
    const view = viewWith({
      state: "secret_unreadable",
      username: { value: null, lockedBy: null },
      password: { set: true, readable: false, lockedBy: null },
    });

    // when
    const actual = draftProblems(draftOf(view), view);

    // then
    expect(actual).toEqual({});
  });

  it("accepts a recipient that is a mail address and refuses one that is not", () => {
    // when
    const actual = [
      recipientProblem(" ana.ruiz@acme.com "),
      recipientProblem("ana"),
    ];

    // then
    expect(actual).toEqual([undefined, "Recipient is not a mail address."]);
  });
});
