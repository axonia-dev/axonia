import { expect, type Page, test } from "@playwright/test";

import { stubShell } from "./api";
import { signedIn } from "./session";

const apiGlob = "**/axonia/**";

test.use({
  viewport: { width: 412, height: 892 },
  isMobile: true,
  hasTouch: true,
});

const CONSENT =
  "/oauth/authorize?response_type=code&client_id=https%3A%2F%2Fclaude.ai%2Foauth%2Fclaude-code-client-metadata" +
  "&redirect_uri=http%3A%2F%2Flocalhost%3A54321%2Fcallback&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM" +
  "&code_challenge_method=S256&scope=read+topic%3Apropose&state=s-1";

async function stubConsent(
  page: Page,
  outcome: Record<string, unknown>,
): Promise<void> {
  await page.route(apiGlob, (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname === "/axonia/me/agent-connections/authorization-requests") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(outcome),
      });
    }
    return route.fallback();
  });
  await signedIn(page);
  await stubShell(page, {
    myOrganizations: [
      { id: "default", name: "Axonia" },
      {
        id: "acme",
        name: "A rather long organization name that must still fit",
      },
    ],
  });
}

function overflowing(page: Page): Promise<string[]> {
  return page.locator("main").evaluate((main) => {
    const edge = main.getBoundingClientRect().left + main.clientWidth;
    return [...main.querySelectorAll("*")]
      .filter((element) => {
        const box = element.getBoundingClientRect();
        return box.width > 0 && box.height > 0 && box.right > edge + 1;
      })
      .map(
        (element) =>
          `<${element.tagName.toLowerCase()} class="${element.getAttribute("class") ?? ""}">`,
      );
  });
}

const READY = {
  outcome: "READY",
  clientName: "Claude Code",
  clientHost: "claude.ai",
  requestedScopes: ["read", "topic:propose"],
};

test("the consent card fits a phone, permissions and buttons included", async ({
  page,
}) => {
  await stubConsent(page, READY);

  await page.goto(CONSENT);

  await expect(
    page.getByRole("heading", { name: "Connect Claude Code" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Allow" })).toBeVisible();
  expect(await overflowing(page)).toEqual([]);
});

// jsdom lays nothing out, so only a real browser sees the Organization row's bordered Select
// stretch its row past the plain-text ones and space the dividers unevenly.
test("the consent card's fact rows share one height", async ({ page }) => {
  await stubConsent(page, READY);

  await page.goto(CONSENT);

  await expect(page.getByRole("button", { name: "Allow" })).toBeVisible();
  const heights = await page
    .locator("dl > div")
    .evaluateAll((rows) =>
      rows.map((row) => row.getBoundingClientRect().height),
    );
  expect(heights).toHaveLength(3);
  expect(new Set(heights).size).toBe(1);
});

test("the refusal card fits a phone", async ({ page }) => {
  await stubConsent(page, { outcome: "REFUSED", reason: "REDIRECT_MISMATCH" });

  await page.goto(CONSENT);

  await expect(
    page.getByRole("heading", { name: "Connection refused" }),
  ).toBeVisible();
  expect(await overflowing(page)).toEqual([]);
});
