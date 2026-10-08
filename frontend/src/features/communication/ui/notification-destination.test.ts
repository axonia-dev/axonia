import { describe, expect, it } from "vitest";

import { destinationOf } from "./notification-destination";

const ORIGIN = "https://axonia.example.com";

describe("destinationOf", () => {
  it("reduces an action URL on this origin to a path, so opening it stays inside the app", () => {
    expect(
      destinationOf(
        `${ORIGIN}/communities/frontend-guild/sessions/123`,
        ORIGIN,
      ),
    ).toEqual({
      kind: "internal",
      to: "/communities/frontend-guild/sessions/123",
    });
  });

  it("keeps a query string and a fragment, which name a tab or an anchor on the page", () => {
    expect(
      destinationOf(`${ORIGIN}/communities/acme?tab=library#notes`, ORIGIN),
    ).toEqual({ kind: "internal", to: "/communities/acme?tab=library#notes" });
  });

  it("treats a relative action URL as a path on this origin", () => {
    expect(destinationOf("/communities/acme", ORIGIN)).toEqual({
      kind: "internal",
      to: "/communities/acme",
    });
  });

  it("leaves another origin as an outbound link rather than a route that does not exist", () => {
    expect(destinationOf("https://example.com/elsewhere", ORIGIN)).toEqual({
      kind: "external",
      href: "https://example.com/elsewhere",
    });
  });

  it("has no destination when the entry carries none", () => {
    expect(destinationOf(null, ORIGIN)).toEqual({ kind: "none" });
    expect(destinationOf(undefined, ORIGIN)).toEqual({ kind: "none" });
    expect(destinationOf("   ", ORIGIN)).toEqual({ kind: "none" });
  });

  it("has no destination for an unparsable value", () => {
    expect(destinationOf("http://", ORIGIN)).toEqual({ kind: "none" });
  });

  it("reads a protocol-relative URL as the origin it really points at", () => {
    expect(destinationOf("//example.com/elsewhere", ORIGIN)).toEqual({
      kind: "external",
      href: "https://example.com/elsewhere",
    });
  });

  it("refuses a scheme a link must never carry", () => {
    expect(destinationOf("javascript:alert(1)", ORIGIN)).toEqual({
      kind: "none",
    });
    expect(destinationOf("mailto:hello@example.com", ORIGIN)).toEqual({
      kind: "none",
    });
  });
});
