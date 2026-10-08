import { afterEach, describe, expect, it } from "vitest";

import {
  activeOrganizationId,
  autoEnterAttempted,
  clearStoredActiveOrganizationId,
  markAutoEnterAttempted,
  organizationlessScope,
  organizationScope,
  rearmAutoEnterOnOrganizationToken,
  setDelegatedOrganizationId,
  setStoredActiveOrganizationId,
  startupScope,
  storedActiveOrganizationId,
} from "@/auth/active-organization";

function makeToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: "none", typ: "JWT" }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe("activeOrganizationId", () => {
  afterEach(() => {
    setDelegatedOrganizationId(undefined);
  });

  it("reads the org claim from the access-token payload", () => {
    // given
    const token = makeToken({ sub: "u1", org: "acme" });

    // when
    const actual = activeOrganizationId(token);

    // then
    expect(actual).toBe("acme");
  });

  it("returns undefined when the org claim is absent", () => {
    // given
    const token = makeToken({ sub: "u1" });

    // when
    const actual = activeOrganizationId(token);

    // then
    expect(actual).toBeUndefined();
  });

  it("returns undefined for a garbage token", () => {
    // when
    const actual = activeOrganizationId("not-a-jwt");

    // then
    expect(actual).toBeUndefined();
  });

  it("returns undefined when the payload segment is not valid base64", () => {
    // given — a three-segment token whose middle segment cannot be base64-decoded
    const token = "header.!!NOTBASE64!!.signature";

    // when
    const actual = activeOrganizationId(token);

    // then
    expect(actual).toBeUndefined();
  });

  it("returns undefined when the payload decodes to JSON that is not an object", () => {
    // given — base64url of the JSON literal `42` (a number, not an object)
    const token = `header.${btoa("42").replace(/=/g, "")}.signature`;

    // when
    const actual = activeOrganizationId(token);

    // then
    expect(actual).toBeUndefined();
  });

  it("returns undefined when no token is present", () => {
    // when
    const actual = activeOrganizationId(undefined);

    // then
    expect(actual).toBeUndefined();
  });

  it("reads the delegated session's organization, which no bearer can carry as a claim", () => {
    // given - a delegated bearer is opaque, so the frame holding it publishes its organization
    setDelegatedOrganizationId("globex");

    // when
    const actual = activeOrganizationId("axi_bearer");

    // then
    expect(actual).toBe("globex");
  });

  it("reads the token's own claim again once the delegated session's organization is withdrawn", () => {
    // given
    setDelegatedOrganizationId("globex");
    setDelegatedOrganizationId(undefined);

    // when
    const actual = activeOrganizationId(makeToken({ org: "acme" }));

    // then
    expect(actual).toBe("acme");
  });
});

describe("organizationScope", () => {
  it("appends organization:<id> to the base scopes", () => {
    // when
    const actual = organizationScope("globex");

    // then
    expect(actual).toBe("openid profile email organization:globex");
  });
});

describe("stored active organization", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("round-trips the persisted active organization", () => {
    // given
    expect(storedActiveOrganizationId()).toBeUndefined();

    // when
    setStoredActiveOrganizationId("globex");

    // then
    expect(storedActiveOrganizationId()).toBe("globex");
  });
});

describe("clearStoredActiveOrganizationId", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("forgets the persisted active organization", () => {
    // given
    setStoredActiveOrganizationId("globex");
    expect(storedActiveOrganizationId()).toBe("globex");

    // when
    clearStoredActiveOrganizationId();

    // then
    expect(storedActiveOrganizationId()).toBeUndefined();
  });
});

describe("organizationlessScope", () => {
  it("is the base OIDC scope with no organization entry", () => {
    // when
    const actual = organizationlessScope();

    // then
    expect(actual).toBe("openid profile email");
    expect(actual).not.toContain("organization:");
  });
});

describe("startupScope", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("requests no organization at all when none is persisted", () => {
    // then
    expect(startupScope()).toBe("openid profile email");
  });

  it("scopes to the persisted organization once one is chosen", () => {
    // given
    setStoredActiveOrganizationId("acme");

    // then
    expect(startupScope()).toBe("openid profile email organization:acme");
  });
});

describe("rearmAutoEnterOnOrganizationToken", () => {
  afterEach(() => {
    sessionStorage.clear();
    // Forgets the token the previous case left as last seen, so a reused one still counts as new.
    rearmAutoEnterOnOrganizationToken(undefined);
  });

  it("re-arms the entry once a new token names an organization, so a later org-less spell enters again", () => {
    // given
    markAutoEnterAttempted();

    // when
    rearmAutoEnterOnOrganizationToken(makeToken({ sub: "u", org: "acme" }));

    // then
    expect(autoEnterAttempted()).toBe(false);
  });

  it("keeps the loop guard when the token comes back without an organization", () => {
    // given
    markAutoEnterAttempted();

    // when
    rearmAutoEnterOnOrganizationToken(makeToken({ sub: "u", n: 1 }));

    // then
    expect(autoEnterAttempted()).toBe(true);
  });

  it("keeps a guard claimed under the token already held, which is not an entry that worked", () => {
    // given
    const held = makeToken({ sub: "u", org: "offline-org" });
    rearmAutoEnterOnOrganizationToken(held);
    markAutoEnterAttempted();

    // when
    rearmAutoEnterOnOrganizationToken(held);

    // then
    expect(autoEnterAttempted()).toBe(true);
  });
});
