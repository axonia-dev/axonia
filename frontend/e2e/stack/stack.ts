import { createHash, randomBytes } from "node:crypto";

import {
  type APIRequestContext,
  expect,
  type Locator,
  type Page,
} from "@playwright/test";

/** deploy/compose.test.yaml's addresses: the edge, and Mailpit's API. */
export const EDGE = "http://localhost:18083";
const MAILPIT = "http://127.0.0.1:18086";

/** The agent of deploy/test/cimd/client.json, returning to a loopback port of its choosing. */
export const AGENT = {
  clientId: "https://cimd.test/client.json",
  redirectUri: "http://127.0.0.1:54321/callback",
};

export interface Person {
  username: string;
  email: string;
  name: string;
  groups: string[];
}

export const OWNER: Person = {
  username: "olivia",
  email: "olivia@example.com",
  name: "Olivia Owner",
  groups: ["axonia-owners"],
};

export const MEMBER: Person = {
  username: "sam",
  email: "sam@example.com",
  name: "Sam Member",
  groups: ["engineering"],
};

/** mock-oauth2-server's form: the token it issues carries exactly the claims typed into it. */
export const issuerForm = (page: Page): Locator =>
  page.locator('input[name="username"]');

export async function signIn(
  page: Page,
  person: Person,
  path: string,
): Promise<void> {
  await page.goto(path);
  await issuerForm(page).fill(person.username);
  await page.locator('textarea[name="claims"]').fill(
    JSON.stringify({
      email: person.email,
      name: person.name,
      groups: person.groups,
    }),
  );
  await page.getByRole("button", { name: "Sign-in" }).click();
  await expect(page).toHaveURL(new URL(path, EDGE).href);
}

interface MailpitMessage {
  Subject: string;
  To: { Address: string }[];
}

/** The subjects of every mail Mailpit holds for this address. */
export async function subjectsFor(address: string): Promise<string[]> {
  const answer = await fetch(`${MAILPIT}/api/v1/messages?limit=500`);
  const { messages } = (await answer.json()) as { messages: MailpitMessage[] };
  return messages
    .filter((message) => message.To.some((to) => to.Address === address))
    .map((message) => message.Subject);
}

export function pkce(): { verifier: string; challenge: string } {
  const verifier = randomBytes(32).toString("base64url");
  return {
    verifier,
    challenge: createHash("sha256").update(verifier).digest("base64url"),
  };
}

const pad = (value: number): string => String(value).padStart(2, "0");

/** Types a date and 10:00 AM into a "Starts at" group, segment by segment, as a keyboard does. */
export async function typeStartsAt(scope: Locator, day: Date): Promise<void> {
  const group = scope.getByRole("group", { name: "Starts at" });
  await group.getByRole("spinbutton", { name: /month/i }).click();
  await scope
    .page()
    .keyboard.type(
      `${pad(day.getUTCMonth() + 1)}${pad(day.getUTCDate())}${day.getUTCFullYear()}`,
    );
  await group.getByRole("spinbutton", { name: /hour/i }).click();
  await scope.page().keyboard.type("1000a");
}

interface ToolResult {
  result?: { content?: { text?: string }[] };
}

/** Initializes an MCP session with this bearer and calls one tool with no arguments. */
export async function callTool(
  request: APIRequestContext,
  bearer: string,
  tool: string,
): Promise<unknown> {
  const headers = {
    Authorization: `Bearer ${bearer}`,
    Accept: "application/json, text/event-stream",
  };
  const initialized = await request.post(`${EDGE}/mcp`, {
    headers,
    data: {
      jsonrpc: "2.0",
      id: 1,
      method: "initialize",
      params: {
        protocolVersion: "2025-06-18",
        capabilities: {},
        clientInfo: { name: "e2e", version: "1" },
      },
    },
  });
  expect(initialized.status()).toBe(200);
  const session = {
    ...headers,
    "Mcp-Session-Id": initialized.headers()["mcp-session-id"] ?? "",
  };
  const notified = await request.post(`${EDGE}/mcp`, {
    headers: session,
    data: { jsonrpc: "2.0", method: "notifications/initialized" },
  });
  expect(notified.status()).toBe(202);
  const called = await request.post(`${EDGE}/mcp`, {
    headers: session,
    data: {
      jsonrpc: "2.0",
      id: 2,
      method: "tools/call",
      params: { name: tool, arguments: {} },
    },
  });
  const event =
    (await called.text())
      .split("\n")
      .find((line) => line.startsWith("data:")) ?? "data:{}";
  const answer = JSON.parse(event.slice("data:".length)) as ToolResult;
  return JSON.parse(answer.result?.content?.[0]?.text ?? "null") as unknown;
}
