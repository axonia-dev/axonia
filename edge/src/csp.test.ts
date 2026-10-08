import { describe, expect, it } from "vitest";
import { contentSecurityPolicy, type CspInput, MEDIA_POLICY } from "./csp";

const directives = (policy: string): Map<string, string> =>
  new Map(
    policy.split(";").map((part) => {
      const [name, ...values] = part.trim().split(/\s+/);
      return [name ?? "", values.join(" ")];
    }),
  );

const INPUT: CspInput = {
  themeScriptHash: "sha256-abc",
  identityOrigin: "https://login.example.com",
  mediaOrigin: null,
  paymentHosts: { library: "https://cdn.payments.example", services: "https://*.payments.example" },
};

const policy = (overrides: Partial<CspInput> = {}): string =>
  contentSecurityPolicy({ ...INPUT, ...overrides });

describe("content security policy", () => {
  it("denies everything by default", () => {
    expect(directives(policy()).get("default-src")).toBe("'none'");
  });

  it("allows scripts from this origin, the pinned inline script, and the checkout library", () => {
    expect(directives(policy()).get("script-src")).toBe(
      "'self' 'sha256-abc' https://cdn.payments.example",
    );
  });

  // The Self-hosted edition's document has no inline script, so its policy names no hash: a hash
  // for a script that does not exist reads as a requirement to the next person editing this.
  it("admits no inline script at all for a build whose documents carry none", () => {
    expect(directives(policy({ themeScriptHash: null })).get("script-src")).toBe(
      "'self' https://cdn.payments.example",
    );
  });

  // The library host is pinned rather than wildcarded: a checkout requires its library be loaded from
  // one origin, and a wildcard here would admit any subdomain the provider ever stands up.
  it("does not wildcard the script host", () => {
    expect(directives(policy()).get("script-src")).not.toContain("*");
  });

  it("never permits unsafe-inline for scripts", () => {
    const scriptSrc = directives(policy()).get("script-src");
    expect(scriptSrc).toBeDefined();
    expect(scriptSrc).not.toContain("'unsafe-inline'");
  });

  it("names no media host when images are served from this origin", () => {
    expect(directives(policy()).get("img-src")).toBe("'self' blob: https://*.payments.example");
  });

  it("names the media host images are served from when it is another", () => {
    expect(directives(policy({ mediaOrigin: "https://media.example.com" })).get("img-src")).toBe(
      "'self' blob: https://media.example.com https://*.payments.example",
    );
  });

  // The library injects its own stylesheet into this page from its host. Blocking it loses styling
  // with no error a reader or a gate would see.
  it("allows stylesheets from this origin plus inline styles, for both React's style prop and its runtime-injected <style> element, and the checkout library's own sheet", () => {
    expect(directives(policy()).get("style-src")).toBe(
      "'self' 'unsafe-inline' https://cdn.payments.example",
    );
  });

  it("allows fonts only from this origin", () => {
    expect(directives(policy()).get("font-src")).toBe("'self'");
  });

  it("allows the web app manifest only from this origin", () => {
    expect(directives(policy()).get("manifest-src")).toBe("'self'");
  });

  it("allows form submissions only to this origin and the identity provider", () => {
    expect(directives(policy()).get("form-action")).toBe("'self' https://login.example.com");
  });

  it("allows the OIDC round trip to reach the identity provider, and the checkout to reach its own services", () => {
    expect(directives(policy()).get("connect-src")).toBe(
      "'self' https://login.example.com https://*.payments.example",
    );
  });

  // Under default-src 'none' an undeclared frame-src blocks a checkout iframe outright.
  it("allows the checkout to be framed, and nothing else", () => {
    expect(directives(policy()).get("frame-src")).toBe("https://*.payments.example");
  });

  it("forbids being framed", () => {
    expect(directives(policy()).get("frame-ancestors")).toBe("'none'");
  });

  it("pins the base URI and forbids plugins", () => {
    const parsed = directives(policy());
    expect(parsed.get("base-uri")).toBe("'none'");
    expect(parsed.get("object-src")).toBe("'none'");
  });
});

describe("a policy with no payments", () => {
  const NO_PAYMENTS = policy({ paymentHosts: null });

  it("names no payment host anywhere", () => {
    expect(NO_PAYMENTS).not.toContain("payments.example");
  });

  it("lets the browser reach the given identity provider, and only it", () => {
    const parsed = directives(NO_PAYMENTS);

    expect(parsed.get("connect-src")).toBe("'self' https://login.example.com");
    expect(parsed.get("form-action")).toBe("'self' https://login.example.com");
  });

  it("names no host for images beyond its own", () => {
    expect(directives(NO_PAYMENTS).get("img-src")).toBe("'self' blob:");
  });

  // Nothing is framed without a checkout, so the directive is left out and frame-src falls back to
  // default-src 'none' rather than naming a host nothing uses.
  it("frames nothing", () => {
    expect(directives(NO_PAYMENTS).has("frame-src")).toBe(false);
    expect(directives(NO_PAYMENTS).get("default-src")).toBe("'none'");
  });
});

describe("the image policy", () => {
  it("is the one the backend serves an uploaded image under", () => {
    expect(MEDIA_POLICY).toBe("default-src 'none'; sandbox");
  });
});
