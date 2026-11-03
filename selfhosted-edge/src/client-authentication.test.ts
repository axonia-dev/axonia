import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { JWT_BEARER, tokenFileAssertion } from "./client-authentication.ts";

const tokenFile = (content: string): string => {
  const path = join(mkdtempSync(join(tmpdir(), "selfhosted-edge-token-")), "token");
  writeFileSync(path, content);
  return path;
};

describe("the token file assertion", () => {
  it("sends the file's token as the assertion, trimmed", async () => {
    const authenticate = tokenFileAssertion(tokenFile("a.b.c\n"));

    await expect(authenticate()).resolves.toEqual({
      client_assertion_type: JWT_BEARER,
      client_assertion: "a.b.c",
    });
  });

  it("reads the file again on every call", async () => {
    const path = tokenFile("first");
    const authenticate = tokenFileAssertion(path);
    await authenticate();

    writeFileSync(path, "second");

    expect((await authenticate()).client_assertion).toBe("second");
  });

  it("rejects an empty file without naming its content", async () => {
    const path = tokenFile("\n");

    const failure = await tokenFileAssertion(path)().then(
      () => null,
      (error: unknown) => error,
    );

    expect(failure).toBeInstanceOf(Error);
    expect((failure as Error).message).toContain(path);
  });

  it("rejects a missing file", async () => {
    const path = join(mkdtempSync(join(tmpdir(), "selfhosted-edge-token-")), "absent");

    await expect(tokenFileAssertion(path)()).rejects.toThrow(/ENOENT/);
  });
});
