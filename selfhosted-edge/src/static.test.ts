import { mkdtempSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { relativeFile } from "./static.ts";
import { type RunningEdge, send, SHELL_HTML, startEdge, staticBuild } from "./test-support.ts";

const ASSET = "/assets/selfhosted-CfJAUbZ1.js";

let edge: RunningEdge;

beforeAll(async () => {
  edge = await startEdge();
});

afterAll(async () => {
  await edge.close();
});

const get = (path: string, headers: Record<string, string> = {}) =>
  send(edge.origin, { path, headers });

describe("a hashed asset", () => {
  it("is cached for a year and carries every header an answer carries", async () => {
    const answer = await get(ASSET);

    expect(answer.status).toBe(200);
    expect(answer.body).toBe("export {};");
    expect(answer.headers).toMatchObject({
      "content-type": "text/javascript; charset=utf-8",
      "cache-control": "public, max-age=31536000, immutable",
      "x-content-type-options": "nosniff",
      vary: "Accept-Encoding",
      "x-robots-tag": "noindex",
      "strict-transport-security": "max-age=31536000",
      "content-security-policy": edge.configuration.contentSecurityPolicy,
    });
  });

  it("is sent compressed with brotli when the browser takes it", async () => {
    const answer = await get(ASSET, { "accept-encoding": "gzip, deflate, br" });

    expect(answer.body).toBe("brotli");
    expect(answer.headers["content-encoding"]).toBe("br");
    expect(answer.headers.etag).toMatch(/-br"$/);
  });

  it("is sent compressed with gzip when brotli is refused", async () => {
    const answer = await get(ASSET, { "accept-encoding": "br;q=0, gzip" });

    expect(answer.body).toBe("gzip");
    expect(answer.headers["content-encoding"]).toBe("gzip");
  });

  it("is sent as written when the build holds no encoding the browser takes", async () => {
    const answer = await get("/favicon.svg", { "accept-encoding": "br" });

    expect(answer.body).toBe("<svg/>");
    expect(answer.headers).not.toHaveProperty("content-encoding");
  });

  it("answers HEAD with its length and no body", async () => {
    const answer = await send(edge.origin, { method: "HEAD", path: ASSET });

    expect(answer.status).toBe(200);
    expect(answer.headers["content-length"]).toBe("10");
    expect(answer.body).toBe("");
  });

  it("is a 404 problem when the build has no such file", async () => {
    const answer = await get("/assets/selfhosted-Missing1.js");

    expect(answer.status).toBe(404);
    expect(answer.headers["content-type"]).toBe("application/problem+json");
    expect(JSON.parse(answer.body)).toMatchObject({ status: 404, title: "Not Found" });
  });

  it("is a 404 with no body for HEAD", async () => {
    const answer = await send(edge.origin, {
      method: "HEAD",
      path: "/assets/selfhosted-Missing1.js",
    });

    expect(answer.status).toBe(404);
    expect(answer.body).toBe("");
  });
});

describe("the app shell", () => {
  it.each(["/", "/index.html"])("is never stored when asked for at %s", async (path) => {
    const answer = await get(path);

    expect(answer.status).toBe(200);
    expect(answer.body).toBe(SHELL_HTML);
    expect(answer.headers["content-type"]).toBe("text/html; charset=utf-8");
    expect(answer.headers["cache-control"]).toBe("no-store");
    expect(answer.headers).not.toHaveProperty("etag");
  });

  it("is what the reload of a deep link gets", async () => {
    const answer = await get("/communities/craft/events", {
      accept: "text/html,application/xhtml+xml",
    });

    expect(answer.status).toBe(200);
    expect(answer.body).toBe(SHELL_HTML);
    expect(answer.headers["cache-control"]).toBe("no-store");
  });

  it.each(["/communities/craft/events", "/assets"])(
    "is not what a script asking for %s gets",
    async (path) => {
      const answer = await get(path, { accept: "*/*" });

      expect(answer.status).toBe(404);
    },
  );

  it("is a 404 problem once the build has lost it", async () => {
    const root = staticBuild();
    const lost = await startEdge({ STATIC_DIR: root });
    rmSync(join(root, "index.html"));

    const answer = await send(lost.origin, {
      path: "/communities",
      headers: { accept: "text/html" },
    });
    await lost.close();

    expect(answer.status).toBe(404);
    expect(answer.headers["content-type"]).toBe("application/problem+json");
  });
});

describe("any other file", () => {
  it("is revalidated, and an unchanged copy is answered 304", async () => {
    const first = await get("/favicon.svg");
    const again = await get("/favicon.svg", { "if-none-match": first.headers.etag ?? "" });

    expect(first.headers["cache-control"]).toBe("no-cache");
    expect(first.headers.etag).toMatch(/^W\/"[0-9a-f]+-[0-9a-f]+"$/);
    expect(again.status).toBe(304);
    expect(again.body).toBe("");
  });

  it("answers If-None-Match: * with 304", async () => {
    expect((await get("/favicon.svg", { "if-none-match": "*" })).status).toBe(304);
  });

  it("names a web manifest's type", async () => {
    expect((await get("/manifest.webmanifest")).headers["content-type"]).toBe(
      "application/manifest+json; charset=utf-8",
    );
  });

  it("refuses any method that writes", async () => {
    const answer = await send(edge.origin, { method: "POST", path: "/favicon.svg", body: "x" });

    expect(answer.status).toBe(405);
    expect(answer.headers.allow).toBe("GET, HEAD");
  });
});

describe("a path that reaches out of the build", () => {
  it.each([
    "/../secret.txt",
    "/%2e%2e/secret.txt",
    "/assets/..%2f..%2fsecret.txt",
    "/..%5csecret.txt",
    "/%2e%2e%5csecret.txt",
    "/favicon.svg%00.png",
    "/%E0%A4%A",
    "/assets//selfhosted-CfJAUbZ1.js",
  ])("never serves %s", async (path) => {
    const fetched = await get(path, { accept: "*/*" });
    const navigated = await get(path, { accept: "text/html" });

    expect(fetched.status).toBe(404);
    expect(fetched.body).not.toContain("never served");
    expect(navigated.body).toBe(SHELL_HTML);
  });
});

describe("a link in the build", () => {
  let linked: RunningEdge;

  beforeAll(async () => {
    const root = staticBuild();
    const outside = mkdtempSync(join(tmpdir(), "selfhosted-edge-outside-"));
    writeFileSync(join(outside, "secret.txt"), "outside the build");
    symlinkSync(join(outside, "secret.txt"), join(root, "out.txt"));
    symlinkSync("../secret.txt", join(root, "up.txt"));
    symlinkSync(outside, join(root, "out"));
    symlinkSync(join(outside, "secret.txt"), join(root, "favicon.svg.br"));
    symlinkSync("favicon.svg", join(root, "alias.svg"));
    linked = await startEdge({ STATIC_DIR: root });
  });

  afterAll(async () => {
    await linked.close();
  });

  it.each(["/out.txt", "/up.txt", "/out/secret.txt"])(
    "never serves %s, which leads out of it",
    async (path) => {
      const answer = await send(linked.origin, { path, headers: { accept: "*/*" } });

      expect(answer.status).toBe(404);
      expect(answer.body).not.toMatch(/outside the build|never served/);
    },
  );

  it("never serves a compressed sibling that leads out of it", async () => {
    const answer = await send(linked.origin, {
      path: "/favicon.svg",
      headers: { "accept-encoding": "br" },
    });

    expect(answer.body).toBe("<svg/>");
    expect(answer.headers).not.toHaveProperty("content-encoding");
  });

  it("is followed to a file inside it", async () => {
    expect((await send(linked.origin, { path: "/alias.svg" })).body).toBe("<svg/>");
  });
});

describe("the file a path names", () => {
  it.each([
    ["/", "index.html"],
    ["/assets/a-b.js", "assets/a-b.js"],
    ["/caf%C3%A9.svg", "café.svg"],
  ])("reads %s as %s", (pathname, file) => {
    expect(relativeFile(pathname)).toBe(file);
  });

  it.each(["/a/../b", "/a/./b", "/a%2Fb", "/a%5Cb", "/a%00", "/assets/", "/a//b", "/%E0%A4%A"])(
    "refuses %s",
    (pathname) => {
      expect(relativeFile(pathname)).toBeNull();
    },
  );
});
