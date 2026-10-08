import { expect, test } from "@playwright/test";

import {
  AGENT,
  callTool,
  EDGE,
  issuerForm,
  MEMBER,
  OWNER,
  pkce,
  signIn,
  subjectsFor,
  typeStartsAt,
} from "./stack";

// One installation, started fresh by deploy/compose.test.yaml, taken through its first hour in order.
test.describe.configure({ mode: "serial" });

const COMMUNITY = "Platform Guild";
const SESSION = "Release train retro";
const TWO_WEEKS = 14 * 24 * 60 * 60 * 1000;

test("the Owner signs in, starts a community and finds it listed, on a reload too", async ({
  page,
}) => {
  await signIn(page, OWNER, "/communities");
  await expect(
    page.getByRole("heading", { level: 1, name: "Communities" }),
  ).toBeVisible();
  await expect(page.getByText("No communities yet")).toBeVisible();

  await page.getByRole("link", { name: "Start a community" }).click();
  await page.getByRole("textbox", { name: "Name" }).fill(COMMUNITY);
  await page.getByLabel("Kind").click();
  await page.getByRole("option", { name: "Community of Practice" }).click();
  await page.getByRole("button", { name: "Create" }).click();

  await expect(page).toHaveURL(`${EDGE}/communities/platform-guild`);
  await page.reload();
  await expect(
    page.getByRole("heading", { level: 1, name: COMMUNITY }),
  ).toBeVisible();
  await page.goto("/communities");
  await expect(
    page.getByRole("link", { name: new RegExp(COMMUNITY) }),
  ).toBeVisible();
});

test("the edge serves the app compressed", async ({ request }) => {
  const answer = await request.get("/", {
    headers: { "Accept-Encoding": "br" },
  });

  expect(answer.status()).toBe(200);
  expect(answer.headers()["content-encoding"]).toBe("br");
});

test("a person outside the owner group sees the community and no way to start one", async ({
  page,
}) => {
  await signIn(page, MEMBER, "/communities");

  // Settings send a person away from a section they may not open only once their permissions are
  // drawn, so landing on General is the member's permission set on screen. The in-app link back
  // keeps that read, and the communities page draws from it on its first render.
  await page.goto("/settings/organization/notifications");
  await expect(page).toHaveURL(`${EDGE}/settings/organization/general`);
  await page.getByRole("link", { name: "Back to the app" }).click();

  await expect(page).toHaveURL(`${EDGE}/communities`);
  await expect(
    page.getByRole("link", { name: new RegExp(COMMUNITY) }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Start a community" }),
  ).toHaveCount(0);
});

test("the Owner sets the mail server up through the card, and a reminder reaches it", async ({
  page,
}) => {
  await signIn(page, OWNER, "/settings/organization/notifications");
  const card = page.getByRole("region", { name: "Mail server" });
  await expect(card.getByText("Not configured")).toBeVisible();

  await card.getByRole("button", { name: "Set up" }).click();
  await card.getByRole("textbox", { name: "Host" }).fill("mailpit");
  await card.getByRole("spinbutton", { name: "Port" }).fill("1025");
  await card.getByRole("radio", { name: "None" }).click();
  await card
    .getByRole("textbox", { name: "From address" })
    .fill("axonia@example.com");
  await card
    .getByRole("textbox", { name: "From name" })
    .fill("Example Company");
  await card.getByRole("textbox", { name: "Test recipient" }).fill(OWNER.email);
  await expect(card.getByRole("button", { name: "Save" })).toBeDisabled();
  await card.getByRole("button", { name: "Send test mail" }).click();
  await expect(
    card.getByText(/^Test mail accepted by mailpit at \d\d:\d\d\.$/),
  ).toBeVisible();
  await expect
    .poll(() => subjectsFor(OWNER.email))
    .toContain("Test mail from axonia");
  await card.getByRole("button", { name: "Save" }).click();
  await expect(card.getByText("Configured", { exact: true })).toBeVisible();
  await expect(card.getByText("mailpit:1025, No encryption")).toBeVisible();

  await page.goto("/communities/platform-guild/schedule");
  await page.getByRole("button", { name: "+ New" }).click();
  await page.getByRole("menuitem", { name: "One-off session" }).click();
  const dialog = page.getByRole("dialog", { name: "New one-off session" });
  await dialog.getByLabel("Title", { exact: true }).fill(SESSION);
  await typeStartsAt(dialog, new Date(Date.now() + TWO_WEEKS));
  await dialog.getByRole("button", { name: "Create" }).click();
  await expect(dialog).toBeHidden();

  await page.goto("/communities/platform-guild/schedule?view=timeline");
  await page.getByRole("link", { name: new RegExp(`^${SESSION}`) }).click();
  await page.getByRole("button", { name: "Reminder scheduled" }).click();
  const released = page.waitForResponse(
    (answer) =>
      answer.url().endsWith("/upcoming-release") &&
      answer.request().method() === "PUT",
  );
  await page.getByRole("menuitemradio", { name: /^Send it now/ }).click();
  expect((await released).status()).toBe(200);
  await expect
    .poll(() => subjectsFor(OWNER.email), { timeout: 60_000 })
    .toContain(`${SESSION}: happening soon`);
});

test("an agent the Owner allows connects with an axa_ token and reads the installation over MCP", async ({
  page,
  request,
}) => {
  await signIn(page, OWNER, "/communities");
  const { verifier, challenge } = pkce();
  const query = new URLSearchParams({
    response_type: "code",
    client_id: AGENT.clientId,
    redirect_uri: AGENT.redirectUri,
    code_challenge: challenge,
    code_challenge_method: "S256",
    resource: `${EDGE}/mcp`,
    scope: "read",
    state: "e2e",
  });
  await page.route("http://127.0.0.1:54321/**", (route) =>
    route.fulfill({ status: 200, body: "connected" }),
  );

  await page.goto(`/oauth/authorize?${query.toString()}`);
  await expect(
    page.getByRole("heading", { level: 1, name: "Connect End-to-end agent" }),
  ).toBeVisible();
  const returned = page.waitForRequest((sent) =>
    sent.url().startsWith(AGENT.redirectUri),
  );
  await page.getByRole("button", { name: "Allow" }).click();
  const code = new URL((await returned).url()).searchParams.get("code") ?? "";
  expect(code).toMatch(/^axc_/);

  const token = await request.post(`${EDGE}/oauth/token`, {
    form: {
      grant_type: "authorization_code",
      code,
      client_id: AGENT.clientId,
      redirect_uri: AGENT.redirectUri,
      code_verifier: verifier,
      resource: `${EDGE}/mcp`,
    },
  });
  expect(token.status()).toBe(200);
  const { access_token: accessToken } = (await token.json()) as {
    access_token: string;
  };
  expect(accessToken).toMatch(/^axa_/);

  expect(await callTool(request, accessToken, "communities.list")).toEqual([
    expect.objectContaining({ slug: "platform-guild", name: COMMUNITY }),
  ]);
});

test("signing out ends the session: the app asks the identity provider again", async ({
  page,
}) => {
  await signIn(page, OWNER, "/communities");

  await page
    .getByRole("button", { name: "Account" })
    .filter({ visible: true })
    .click();
  await page.getByRole("menuitem", { name: "Log out" }).click();
  // The sign-out itself lands on the identity provider; a visit after it settles must too.
  await expect(issuerForm(page)).toBeVisible();
  await page.goto("/communities");

  await expect(issuerForm(page)).toBeVisible();
});
