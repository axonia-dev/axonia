import { describe, expect, it } from "vitest";

import {
  ACCEPTED,
  CONFIGURED,
  viewWith,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  acceptedLine,
  changedAt,
  changeLines,
  lastChangeSummary,
  senderFacts,
  serverFacts,
  warningsOf,
} from "@/selfhosted/features/mail-server/domain/mail-server-copy";

describe("the mail server card's sentences", () => {
  it("state the server and the sender", () => {
    // when
    const actual = [serverFacts(CONFIGURED), senderFacts(CONFIGURED)];

    // then
    expect(actual).toEqual([
      "smtp.acme.com:587, STARTTLS",
      "From Acme Communities <axonia@acme.com>",
    ]);
  });

  it("name a server that sends in the clear as having no encryption", () => {
    // given
    const view = viewWith({ security: { value: "NONE", lockedBy: null } });

    // when
    const actual = serverFacts(view);

    // then
    expect(actual).toBe("smtp.acme.com:587, No encryption");
  });

  it("name a sender with no display name by its address alone", () => {
    // given
    const view = viewWith({ fromName: { value: null, lockedBy: null } });

    // when
    const actual = senderFacts(view);

    // then
    expect(actual).toBe("From axonia@acme.com");
  });

  it("sum the history up by its last change, in the reader's zone", () => {
    // when
    const actual = lastChangeSummary({
      occurredAt: "2026-10-02T21:03:00.000Z",
      actorName: "Ana Ruiz",
    });

    // then
    expect(actual).toBe("History, last changed by Ana Ruiz on 2 Oct 2026");
  });

  it("name an erased actor Deleted member", () => {
    // when
    const actual = lastChangeSummary({
      occurredAt: "2026-10-02T21:03:00.000Z",
      actorName: null,
    });

    // then
    expect(actual).toBe(
      "History, last changed by Deleted member on 2 Oct 2026",
    );
  });

  it("date a history row to the minute", () => {
    // when
    const actual = changedAt("2026-10-02T21:03:00.000Z");

    // then
    expect(actual).toBe("2 Oct 2026 at 14:03");
  });

  it("list an edit with its previous value, then what was set, then what was cleared", () => {
    // when
    const actual = changeLines([
      {
        field: "fromName",
        kind: "set",
        from: "axonia",
        to: "Acme Communities",
      },
      { field: "host", kind: "set", from: null, to: "smtp.acme.com" },
      { field: "password", kind: "replaced", from: null, to: null },
      { field: "username", kind: "cleared", from: "axonia", to: null },
    ]);

    // then
    expect(actual).toEqual([
      "From name: axonia → Acme Communities",
      "Set: host, password",
      "Cleared: username",
    ]);
  });

  it("warn of a change by the deployment and of the last refused delivery", () => {
    // given
    const view = viewWith({
      state: "delivery_failing",
      changedSinceTest: true,
      lastFailure: {
        reason: "authentication_failed",
        at: "2026-10-02T21:02:00.000Z",
      },
    });

    // when
    const actual = warningsOf(view);

    // then
    expect(actual).toEqual([
      "Changed by the deployment since the last test.",
      "Last refusal: Authentication failed, 2 Oct 2026 at 14:02.",
    ]);
  });

  it("warn that no mail leaves while the stored password cannot be read", () => {
    // given
    const view = viewWith({
      state: "secret_unreadable",
      password: { set: true, readable: false, lockedBy: null },
    });

    // when
    const actual = warningsOf(view);

    // then
    expect(actual).toEqual([
      "Mail is not sent until the password is entered again.",
    ]);
  });

  it("name a security by the label the form gives it", () => {
    // when
    const actual = changeLines([
      { field: "security", kind: "set", from: "STARTTLS", to: "NONE" },
    ]);

    // then
    expect(actual).toEqual(["Security: STARTTLS → None"]);
  });

  it("say a refusal for want of an answer without naming a delay no socket keeps", () => {
    // given
    const view = viewWith({
      state: "delivery_failing",
      lastFailure: { reason: "timeout", at: "2026-10-02T21:02:00.000Z" },
    });

    // when
    const actual = warningsOf(view);

    // then
    expect(actual).toEqual([
      "Last refusal: No answer in time, 2 Oct 2026 at 14:02.",
    ]);
  });

  it("state an accepted test by its host and the time the server took it", () => {
    // when
    const actual = acceptedLine(ACCEPTED);

    // then
    expect(actual).toBe("Test mail accepted by smtp.acme.com at 14:02.");
  });
});
