import { describe, expect, it } from "vitest";
import { isMediaPath, withPolicies } from "./policies.ts";
import { MEDIA_POLICY } from "./shared.ts";

const POLICIES = { contentSecurityPolicy: "default-src 'none'", strictTransport: true };

describe("the headers every answer carries", () => {
  it("replace the answer's own policy, and ask not to be indexed", () => {
    expect(
      withPolicies(
        { "content-security-policy": "default-src *", "x-request-id": "r1" },
        "/axonia/me",
        POLICIES,
      ),
    ).toEqual({
      "content-security-policy": "default-src 'none'",
      "x-request-id": "r1",
      "x-robots-tag": "noindex",
      "strict-transport-security": "max-age=31536000",
    });
  });

  it("keep the backend's own policy on an uploaded image", () => {
    expect(
      withPolicies({ "content-security-policy": "sandbox" }, "/public/media/a.png", POLICIES)[
        "content-security-policy"
      ],
    ).toBe("sandbox");
  });

  it("give an uploaded image the image policy when the backend sent none", () => {
    expect(withPolicies({}, "/public/media/a.png", POLICIES)["content-security-policy"]).toBe(
      MEDIA_POLICY,
    );
  });

  it("ask for no strict transport over cleartext", () => {
    expect(withPolicies({}, "/", { ...POLICIES, strictTransport: false })).not.toHaveProperty(
      "strict-transport-security",
    );
  });

  it.each(["/public/mediation", "/axonia/public/media/a.png", "/public/media"])(
    "do not take %s for an uploaded image",
    (pathname) => {
      expect(isMediaPath(pathname)).toBe(false);
    },
  );
});
