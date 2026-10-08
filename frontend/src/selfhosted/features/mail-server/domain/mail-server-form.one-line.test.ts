import { describe, expect, it } from "vitest";

import { MailValueRefused } from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  CONFIGURED,
  viewWith,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  draftOf,
  draftProblems,
  edited,
  type MailServerDraft,
  refusalProblems,
  valuesOf,
} from "@/selfhosted/features/mail-server/domain/mail-server-form";

const NO_USERNAME = viewWith({
  username: { value: null, lockedBy: null },
  password: { set: false, readable: true, lockedBy: null },
});

function draftWith(patch: Partial<MailServerDraft>) {
  return edited(draftOf(CONFIGURED), patch, CONFIGURED);
}

function refused(field: "username" | "password", draft: MailServerDraft) {
  return refusalProblems(
    new MailValueRefused(field),
    valuesOf(draft),
    CONFIGURED,
  );
}

describe("a username the installation refused", () => {
  it("asks for a username when none was sent", () => {
    // when
    const actual = refusalProblems(
      new MailValueRefused("username"),
      valuesOf(draftOf(NO_USERNAME)),
      NO_USERNAME,
    );

    // then
    expect(actual).toEqual({ username: "A password needs a username." });
  });

  it("says a username that was sent holds a hidden character", () => {
    // when
    const actual = refused("username", draftWith({ username: "axo\u0007nia" }));

    // then
    expect(actual).toEqual({
      username: "Username holds a tab or another hidden character.",
    });
  });
});

describe("a password the installation refused", () => {
  it("asks for the password again when none was sent", () => {
    // when
    const actual = refused("password", draftOf(CONFIGURED));

    // then
    expect(actual).toEqual({ password: "Enter the password again." });
  });

  it("says a password that was sent holds a hidden character", () => {
    // when
    const actual = refused(
      "password",
      draftWith({ replaceRequested: true, password: "s3c\tret" }),
    );

    // then
    expect(actual).toEqual({
      password: "Password holds a tab or another hidden character.",
    });
  });
});

describe("a value the installation keeps to one line", () => {
  it.each<[string, Partial<MailServerDraft>, object]>([
    [
      "a username",
      { username: "axo\tnia" },
      { username: "Username holds a tab or another hidden character." },
    ],
    [
      "a typed password",
      { replaceRequested: true, password: "s3c\nret" },
      { password: "Password holds a tab or another hidden character." },
    ],
    [
      "a sender name",
      { fromName: "Acme\u0000Communities" },
      { fromName: "From name holds a tab or another hidden character." },
    ],
  ])(
    "stops a test while %s holds a control character",
    (_value, patch, problem) => {
      // given
      const view = viewWith({ fromName: { value: null, lockedBy: null } });
      const draft = edited(draftOf(view), patch, view);

      // when
      const actual = draftProblems(draft, view);

      // then
      expect(actual).toMatchObject(problem);
    },
  );

  it("stops a test while the host holds a control character, which is no space", () => {
    // given
    const draft = draftWith({ host: "smtp\u0007acme.com" });

    // when
    const actual = draftProblems(draft, CONFIGURED);

    // then
    expect(actual).toMatchObject({
      host: "Host is a server name with no spaces or hidden characters.",
    });
  });

  it("lets a tab at a username's end through, as the ends are trimmed before sending", () => {
    // given
    const draft = draftWith({ username: " axonia\t", password: "" });

    // when
    const actual = draftProblems(draft, CONFIGURED);

    // then
    expect(actual).toEqual({});
  });
});
