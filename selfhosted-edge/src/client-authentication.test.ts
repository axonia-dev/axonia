import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";
import {
  JWT_BEARER,
  managedIdentityAssertion,
  tokenFileAssertion,
} from "./client-authentication.ts";

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
    const content = "  \t\n \t  \n";
    const path = tokenFile(content);

    const failure = await tokenFileAssertion(path)().then(
      () => null,
      (error: unknown) => error,
    );

    expect(failure).toBeInstanceOf(Error);
    const { message } = failure as Error;
    expect(message).toContain(path);
    expect(message).not.toContain(content);
    expect(message).not.toContain("\t");
  });

  it("rejects a missing file", async () => {
    const path = join(mkdtempSync(join(tmpdir(), "selfhosted-edge-token-")), "absent");

    await expect(tokenFileAssertion(path)()).rejects.toThrow(/ENOENT/);
  });
});

describe("the managed identity assertion", () => {
  const identity = {
    endpoint: "http://169.254.0.1:8081/msi/token",
    header: "secret-header",
    clientId: "client-1",
  };
  const NOW = 1_800_000_000_000;
  const tokenResponse = (token: string, expiresInSeconds?: number | string): Response =>
    Response.json({
      access_token: token,
      ...(expiresInSeconds === undefined
        ? {}
        : {
            expires_on:
              typeof expiresInSeconds === "string"
                ? expiresInSeconds
                : String(NOW / 1000 + expiresInSeconds),
          }),
    });
  const minutes = (count: number): number => count * 60_000;

  it("asks the identity endpoint for a token-exchange token for the given identity", async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(tokenResponse("mi-token", 3600));

    const assertion = await managedIdentityAssertion(identity, fetchFn, () => NOW)();

    expect(assertion).toEqual({ client_assertion_type: JWT_BEARER, client_assertion: "mi-token" });
    expect(fetchFn).toHaveBeenCalledTimes(1);
    const [url, init] = fetchFn.mock.calls[0]!;
    expect(url).toBe(
      `${identity.endpoint}?${new URLSearchParams({
        resource: "api://AzureADTokenExchange",
        "api-version": "2019-08-01",
        client_id: "client-1",
      }).toString()}`,
    );
    expect(init?.method).toBe("GET");
    expect(init?.headers).toEqual({ "X-IDENTITY-HEADER": "secret-header" });
    expect(init?.redirect).toBe("manual");
    expect(init?.signal).toBeInstanceOf(AbortSignal);
  });

  it("reuses the token until five minutes before it expires", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockImplementation(async () => tokenResponse("mi-token", 3600));
    let now = NOW;
    const authenticate = managedIdentityAssertion(identity, fetchFn, () => now);

    await authenticate();
    now = NOW + minutes(54);
    await authenticate();
    expect(fetchFn).toHaveBeenCalledTimes(1);

    now = NOW + minutes(56);
    await authenticate();
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });

  it("asks once for several calls made together", async () => {
    let answer: (response: Response) => void = () => {};
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockReturnValue(new Promise((resolve) => (answer = resolve)));
    const authenticate = managedIdentityAssertion(identity, fetchFn, () => NOW);

    const calls = [authenticate(), authenticate(), authenticate()];
    answer(tokenResponse("mi-token", 3600));

    const assertions = await Promise.all(calls);
    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(assertions.map((assertion) => assertion.client_assertion)).toEqual([
      "mi-token",
      "mi-token",
      "mi-token",
    ]);
  });

  it("forgets a failure", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response("identity-secret-body", { status: 500 }))
      .mockResolvedValueOnce(tokenResponse("mi-token", 3600));
    const authenticate = managedIdentityAssertion(identity, fetchFn, () => NOW);

    const failure = await authenticate().then(
      () => null,
      (error: unknown) => error,
    );

    expect((failure as Error).message).toContain("500");
    expect((failure as Error).message).not.toContain("identity-secret-body");
    expect((failure as Error).message).not.toContain(identity.header);
    expect((await authenticate()).client_assertion).toBe("mi-token");
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });

  it("rejects a redirect naming its status only", async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 302 }));

    await expect(managedIdentityAssertion(identity, fetchFn, () => NOW)()).rejects.toThrow(/302/);
  });

  it("rejects a 200 whose body is not JSON, or has no access_token, without naming the body", async () => {
    for (const body of ["not-json-secret", JSON.stringify({ other: "secret-value" })]) {
      const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(new Response(body, { status: 200 }));

      const failure = await managedIdentityAssertion(identity, fetchFn, () => NOW)().then(
        () => null,
        (error: unknown) => error,
      );

      expect(failure).toBeInstanceOf(Error);
      expect((failure as Error).message).toContain("200");
      expect((failure as Error).message).not.toContain("secret");
    }
  });

  it("does not reuse a token whose expires_on is missing or not a number", async () => {
    for (const expiresOn of [undefined, "soon"]) {
      const fetchFn = vi
        .fn<typeof fetch>()
        .mockImplementation(async () => tokenResponse("mi-token", expiresOn));
      const authenticate = managedIdentityAssertion(identity, fetchFn, () => NOW);

      await authenticate();
      await authenticate();

      expect(fetchFn).toHaveBeenCalledTimes(2);
    }
  });
});
