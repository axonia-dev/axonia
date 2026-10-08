import { expect, test } from "@playwright/test";

import { stubShell } from "./api";
import { signedIn } from "./session";

const apiGlob = "**/axonia/**";

test("root redirects into the global shell, light-first, with a working theme toggle", async ({
  page,
}) => {
  // given - a signed-in session and an empty communities directory, so the
  // redirect target renders without a real edge or backend behind it.
  await signedIn(page);
  await page.route(apiGlob, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content: [],
        totalElements: 0,
        totalPages: 1,
        number: 0,
        size: 50,
      }),
    }),
  );
  // Registered last so it answers the shell's own calls first; everything else
  // falls through to the directory stub above.
  await stubShell(page);

  // when - open the root
  await page.goto("/");

  // then — it redirects to the communities directory inside the global shell
  await expect(
    page.getByRole("heading", { name: /communities/i }),
  ).toBeVisible();
  await expect(page.getByRole("img", { name: "Axonia" })).toBeVisible();

  // Light-first: no dark-theme class on the root by default.
  await expect(page.locator("html")).not.toHaveClass(/theme-dark/);

  // Toggling the theme applies it. Inside the authenticated shell the control is a
  // menu item on the account menu; the standalone ThemeToggle button lives only on
  // the public surfaces (marketing, lobby, hub preview).
  await page.getByRole("button", { name: "Account" }).click();
  await page.getByRole("menuitem", { name: /dark theme/i }).click();
  await expect(page.locator("html")).toHaveClass(/theme-dark/);
});
