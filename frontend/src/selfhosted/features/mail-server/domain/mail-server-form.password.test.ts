import { describe, expect, it } from "vitest";

import {
  type MailServerView,
  MailValueRefused,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  CONFIGURED,
  viewWith,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  canKeepStored,
  draftOf,
  draftProblems,
  edited,
  type MailServerDraft,
  refusalProblems,
  valuesOf,
} from "@/selfhosted/features/mail-server/domain/mail-server-form";

const DEPLOYMENT_PASSWORD = viewWith({
  password: { set: true, readable: true, lockedBy: "AXONIA_MAIL_PASSWORD" },
});

function edit(
  patch: Partial<MailServerDraft>,
  view: MailServerView = CONFIGURED,
) {
  return edited(draftOf(view), patch, view);
}

describe("a stored password and the server it was stored for", () => {
  it.each<[string, Partial<MailServerDraft>]>([
    ["host", { host: "smtp.other.com" }],
    ["port", { port: "2525" }],
    ["username", { username: "relay" }],
    ["security, weakened to none", { security: "NONE" }],
  ])("asks for the password again when the %s moves", (_moved, patch) => {
    // when
    const actual = edit(patch);

    // then
    expect(actual).toMatchObject({
      replaceRequested: false,
      replacingPassword: true,
    });
  });

  it("keeps the stored password across a move between encryptions", () => {
    // when
    const actual = edit({ security: "TLS" });

    // then
    expect(actual.replacingPassword).toBe(false);
  });

  it("keeps the stored password for the same server written with spaces around it", () => {
    // when
    const actual = edit({ host: " smtp.acme.com " });

    // then
    expect(actual.replacingPassword).toBe(false);
  });

  it("draws the stored password again on moving back, forgetting what was typed", () => {
    // given
    const moved = edit({ host: "smtp.other.com" });
    const typed = edited(moved, { password: "s3cret" }, CONFIGURED);

    // when
    const actual = edited(typed, { host: "smtp.acme.com" }, CONFIGURED);

    // then
    expect(actual).toMatchObject({ replacingPassword: false, password: "" });
  });

  it("keeps a password Replace asked for, whatever the server", () => {
    // given
    const replacing = edit({ replaceRequested: true });
    const moved = edited(replacing, { host: "smtp.other.com" }, CONFIGURED);
    const typed = edited(moved, { password: "s3cret" }, CONFIGURED);

    // when
    const actual = edited(typed, { host: "smtp.acme.com" }, CONFIGURED);

    // then
    expect(actual).toMatchObject({
      replacingPassword: true,
      password: "s3cret",
    });
  });

  it("never asks for a password the deployment sets", () => {
    // when
    const actual = edit({ host: "smtp.other.com" }, DEPLOYMENT_PASSWORD);

    // then
    expect(actual.replacingPassword).toBe(false);
  });

  it("asks for no password once the username is gone, as none is sent", () => {
    // when
    const actual = edit({ username: "" });

    // then
    expect(actual.replacingPassword).toBe(false);
  });

  it("stops a test until the password for another server is typed", () => {
    // given
    const moved = edit({ host: "smtp.other.com" });

    // when
    const actual = draftProblems(moved, CONFIGURED);

    // then
    expect(actual).toEqual({
      password: "Enter the password again for another server.",
    });
  });

  it("asks for a username beside a password the deployment sets", () => {
    // given
    const draft = edit({ username: "" }, DEPLOYMENT_PASSWORD);

    // when
    const actual = draftProblems(draft, DEPLOYMENT_PASSWORD);

    // then
    expect(actual).toEqual({ username: "A password needs a username." });
  });
});

describe("Keep stored, the way back from Replace", () => {
  const NONE_STORED = viewWith({
    password: { set: false, readable: true, lockedBy: null },
  });
  const UNREADABLE = viewWith({
    password: { set: true, readable: false, lockedBy: null },
  });

  it("is offered once Replace is asked for the server the password was stored for", () => {
    // when
    const actual = canKeepStored(edit({ replaceRequested: true }), CONFIGURED);

    // then
    expect(actual).toBe(true);
  });

  it.each<[string, Partial<MailServerDraft>, MailServerView]>([
    ["before Replace is asked for", {}, CONFIGURED],
    ["when no password is stored", { replaceRequested: true }, NONE_STORED],
    [
      "when the stored one cannot be read",
      { replaceRequested: true },
      UNREADABLE,
    ],
    [
      "when the deployment sets it",
      { replaceRequested: true },
      DEPLOYMENT_PASSWORD,
    ],
    [
      "for another server",
      { replaceRequested: true, host: "smtp.other.com" },
      CONFIGURED,
    ],
  ])("is not offered %s", (_case, patch, view) => {
    // when
    const actual = canKeepStored(edit(patch, view), view);

    // then
    expect(actual).toBe(false);
  });
});

describe("a value the installation refused", () => {
  it("is marked on its field in the card's words", () => {
    // given
    const refused = new MailValueRefused("fromAddress");

    // when
    const actual = refusalProblems(
      refused,
      valuesOf(draftOf(CONFIGURED)),
      CONFIGURED,
    );

    // then
    expect(actual).toEqual({
      fromAddress: "From address is not a mail address.",
    });
  });

  it("says on a password the deployment sets that it is the deployment's", () => {
    // given
    const refused = new MailValueRefused("password");

    // when
    const actual = refusalProblems(
      refused,
      valuesOf(draftOf(DEPLOYMENT_PASSWORD)),
      DEPLOYMENT_PASSWORD,
    );

    // then
    expect(actual).toEqual({
      password: "The deployment sets this password for the current server.",
    });
  });

  it("asks again for a refused password the form can change", () => {
    // given
    const refused = new MailValueRefused("password");

    // when
    const actual = refusalProblems(
      refused,
      valuesOf(draftOf(CONFIGURED)),
      CONFIGURED,
    );

    // then
    expect(actual).toEqual({ password: "Enter the password again." });
  });

  it("says on any other value the deployment sets that the deployment set it", () => {
    // given
    const view = viewWith({
      fromAddress: {
        value: "axonia@acme,com",
        lockedBy: "AXONIA_MAIL_FROM_ADDRESS",
      },
    });

    // when
    const actual = refusalProblems(
      new MailValueRefused("fromAddress"),
      valuesOf(draftOf(view)),
      view,
    );

    // then
    expect(actual).toEqual({
      fromAddress: "Set by the deployment and refused.",
    });
  });

  it("marks no field of the form for the test's recipient", () => {
    // given
    const refused = new MailValueRefused("recipient");

    // when
    const actual = refusalProblems(
      refused,
      valuesOf(draftOf(CONFIGURED)),
      CONFIGURED,
    );

    // then
    expect(actual).toEqual({});
  });
});
