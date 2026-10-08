import { spawn } from "node:child_process";
import { once } from "node:events";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { environment, send } from "./test-support.ts";

// The process Node runs, the shared modules of edge/src included, with no transform but stripping:
// a specifier without its extension, or syntax stripping cannot erase, fails here and only here.
const MAIN = fileURLToPath(new URL("./main.ts", import.meta.url));

const start = (settings: Record<string, string | undefined>) =>
  spawn(process.execPath, [MAIN], {
    env: { PATH: process.env["PATH"], ...settings },
    stdio: ["ignore", "pipe", "pipe"],
  });

describe("the edge process", () => {
  it("listens, answers, and stops cleanly on SIGTERM", async () => {
    const edge = start(environment({ PORT: "0" }));
    const [line] = (await once(edge.stdout, "data")) as [Buffer];
    const { event, port } = JSON.parse(line.toString()) as { event: string; port: number };

    const health = await send(`http://127.0.0.1:${port}`, { path: "/healthz" });
    edge.kill("SIGTERM");
    const [code] = (await once(edge, "close")) as [number | null];

    expect(event).toBe("listening");
    expect(health.status).toBe(200);
    expect(code).toBe(0);
  }, 20_000);

  it("refuses to start without a secret, naming the setting and printing no secret", async () => {
    const settings = environment({ OIDC_CLIENT_SECRET: undefined });
    const edge = start(settings);
    let stderr = "";
    edge.stderr.on("data", (chunk: Buffer) => {
      stderr += chunk.toString();
    });

    const [code] = (await once(edge, "close")) as [number | null];

    expect(code).toBe(1);
    expect(stderr).toContain("OIDC_CLIENT_SECRET is required.");
    expect(stderr).not.toContain(settings["SESSION_COOKIE_KEY"]);
  }, 20_000);
});
