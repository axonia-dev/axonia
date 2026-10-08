/// <reference types="node" />
import { describe, expect, it } from "vitest";

import { isCloudModule, selfHostedDocumentUrl } from "../../selfhosted-build";

const ROOT = "/repo/frontend";

describe("isCloudModule", () => {
  it.each([
    ["/repo/frontend/src/cloud/edition.tsx", true],
    [
      "/repo/frontend/src/cloud/features/example/ui/ExamplePage.tsx?v=1a2b",
      true,
    ],
    ["/repo/frontend/src/cloudy.ts", false],
    ["/repo/frontend/src/features/admin/ui/UsersPage.tsx", false],
    ["/repo/frontend/node_modules/react/index.js", false],
    ["\0virtual:module", false],
  ])("answers %s with %s", (id, expected) => {
    expect(isCloudModule(ROOT, id)).toBe(expected);
  });
});

// The dev server's SPA fallback hands every deep link to /index.html, which in the open edition
// is the Cloud's landing page; the open document is served in its place.
describe("selfHostedDocumentUrl", () => {
  it.each([
    ["/index.html", "/selfhosted.html"],
    ["/index.html?html-proxy", "/selfhosted.html?html-proxy"],
    ["/communities", "/communities"],
    ["/src/selfhosted/main.tsx", "/src/selfhosted/main.tsx"],
  ])("serves %s as %s", (url, expected) => {
    expect(selfHostedDocumentUrl(url)).toBe(expected);
  });
});
