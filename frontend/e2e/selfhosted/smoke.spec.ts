import { expect, type Page, test } from "@playwright/test";

import { overflowing } from "../overflow";
import { anonymous } from "../session";
import { signedInShell } from "./shell";

/** A member of the installation's one Enterprise organization, which lets agents connect: every
 *  row of the account rail the Self-hosted edition shows. */
async function everyAccountRow(page: Page): Promise<void> {
  await signedInShell(page);
  await page.route("**/axonia/me/agent-access", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ available: true }),
    }),
  );
}

test("opens on the member's communities, from a token that names no organization", async ({
  page,
}) => {
  // given
  await signedInShell(page);

  // when
  await page.goto("/");

  // then
  await expect(page).toHaveURL(/\/communities$/);
  await expect(
    page.getByRole("heading", { name: /communities/i }),
  ).toBeVisible();
});

test("sends a visitor with no session to sign in, having no front door of its own", async ({
  page,
}) => {
  // given
  await anonymous(page);
  await page.route("**/auth/login**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "<p>sign-in stand-in</p>",
    }),
  );

  // when
  await page.goto("/");

  // then
  await expect(page.getByText("sign-in stand-in")).toBeVisible();
  expect(new URL(page.url()).pathname).toBe("/auth/login");
});

test.describe("at phone width", () => {
  test.use({
    viewport: { width: 412, height: 892 },
    isMobile: true,
    hasTouch: true,
  });

  test("lays the account settings out without a sideways scroll", async ({
    page,
  }) => {
    // given
    await everyAccountRow(page);

    // when
    await page.goto("/settings/account");
    const rows = ["Profile", "API keys", "Notifications"];
    for (const row of rows) {
      await expect(page.getByRole("link", { name: row })).toBeVisible();
    }
    await expect(page.getByRole("main").getByRole("link")).toHaveCount(
      rows.length,
    );

    // then
    expect(await overflowing(page)).toEqual([]);
  });
});
