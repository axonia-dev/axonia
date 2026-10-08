import { expect, type Locator, test } from "@playwright/test";

import { overflowing } from "../overflow";
import {
  CONFIGURED,
  LOCKED_LONG_HOST,
  LONG_HOST,
  NOTIFICATIONS,
  OWNER,
  STORED_PASSWORD,
  stubMailServer,
} from "./mail-server-stub";
import { signedInShell } from "./shell";

test("an Owner sets up the mail server, tests it, and saves exactly what was tested", async ({
  page,
}) => {
  // given
  await signedInShell(page, { permissions: OWNER });
  const writes = await stubMailServer(page);
  await page.goto(NOTIFICATIONS);
  const card = page.getByRole("region", { name: "Mail server" });
  await expect(card.getByText("Not configured")).toBeVisible();
  await card.getByRole("button", { name: "Set up" }).click();
  await card.getByRole("textbox", { name: "Host" }).fill("mailpit");
  await card.getByRole("spinbutton", { name: "Port" }).fill("1025");
  await card.getByRole("radio", { name: "None" }).click();
  await card
    .getByRole("textbox", { name: "From address" })
    .fill("axonia@acme.example");
  await expect(card.getByRole("button", { name: "Save" })).toBeDisabled();
  await card.getByRole("button", { name: "Send test mail" }).click();
  await expect(
    card.getByText(/^Test mail accepted by mailpit at \d\d:\d\d\.$/),
  ).toBeVisible();

  // when
  await card.getByRole("button", { name: "Save" }).click();

  // then
  await expect(card.getByText("Configured", { exact: true })).toBeVisible();
  await expect(card.getByText("mailpit:1025, No encryption")).toBeVisible();
  expect(writes.map((request) => request.method())).toEqual(["POST", "PUT"]);
  expect(writes[0]?.postDataJSON()).toMatchObject({
    recipient: "dev@example.test",
  });
  expect(writes[1]?.postDataJSON()).toEqual({
    values: {
      host: "mailpit",
      port: 1025,
      security: "NONE",
      username: null,
      password: null,
      fromAddress: "axonia@acme.example",
      fromName: null,
    },
    testToken: "v1.token",
    baseRevision: 0,
  });
});

test("tells a holder of communication:write alone, on the organization Notifications page, that mail is not configured, with no card", async ({
  page,
}) => {
  // given
  await signedInShell(page, { permissions: ["communication:write"] });
  await stubMailServer(page);

  // when
  await page.goto(NOTIFICATIONS);

  // then
  await expect(page.getByText("Mail not configured.")).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Mail not configured." }),
  ).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Mail server" })).toHaveCount(
    0,
  );
  await expect(
    page.getByText("Quarterly roundup", { exact: true }),
  ).toBeVisible();
});

test("opens the Notifications page to a holder of mail_server:read alone, with nothing to change", async ({
  page,
}) => {
  // given
  await signedInShell(page, { permissions: ["mail_server:read"] });
  await stubMailServer(page, CONFIGURED);

  // when
  await page.goto(NOTIFICATIONS);

  // then
  const card = page.getByRole("region", { name: "Mail server" });
  await expect(card.getByText("mailpit:1025, No encryption")).toBeVisible();
  await expect(card.getByRole("button", { name: "Edit" })).toHaveCount(0);
  await expect(
    page.getByText("Quarterly roundup", { exact: true }),
  ).toHaveCount(0);
});

test("keeps Send test mail level with the recipient's input while the recipient is marked", async ({
  page,
}) => {
  // given
  await signedInShell(page, { permissions: OWNER });
  await stubMailServer(page, CONFIGURED);
  await page.goto(NOTIFICATIONS);
  const card = page.getByRole("region", { name: "Mail server" });
  await card.getByRole("button", { name: "Edit" }).click();
  await card.getByRole("textbox", { name: "Test recipient" }).fill("ana");

  // when
  await card.getByRole("button", { name: "Send test mail" }).click();
  await expect(
    card.getByText("Recipient is not a mail address."),
  ).toBeVisible();

  // then
  const input = await card
    .getByRole("textbox", { name: "Test recipient" })
    .boundingBox();
  const button = await card
    .getByRole("button", { name: "Send test mail" })
    .boundingBox();
  expect(button?.y).toBeCloseTo(input?.y ?? Number.NaN, 0);
  expect(button?.height).toBeCloseTo(input?.height ?? Number.NaN, 0);
});

test("keeps the password's box still and as tall as Replace and Keep stored as they swap", async ({
  page,
}) => {
  // given
  await signedInShell(page, { permissions: OWNER });
  await stubMailServer(page, STORED_PASSWORD);
  await page.goto(NOTIFICATIONS);
  const card = page.getByRole("region", { name: "Mail server" });
  await card.getByRole("button", { name: "Edit" }).click();
  const stored = await card
    .getByRole("group", { name: "Password" })
    .getByText("Stored", { exact: true })
    .locator("..")
    .boundingBox();
  const replace = await card
    .getByRole("button", { name: "Replace" })
    .boundingBox();

  // when
  await card.getByRole("button", { name: "Replace" }).click();

  // then
  const input = await card
    .getByLabel("Password", { exact: true })
    .boundingBox();
  const keep = await card
    .getByRole("button", { name: "Keep stored" })
    .boundingBox();
  expect(input?.width).toBeCloseTo(stored?.width ?? Number.NaN, 0);
  expect(replace?.height).toBeCloseTo(stored?.height ?? Number.NaN, 0);
  expect(keep?.height).toBeCloseTo(input?.height ?? Number.NaN, 0);
});

/** A swap button draws its own word, level with itself, and holds the other word's width unseen. */
async function expectOnlyItsOwnWord(card: Locator, own: string, other: string) {
  const button = card.getByRole("button", { name: own });
  const word = button.getByText(own, { exact: true });
  await expect(button.getByText(other, { exact: true })).toBeHidden();
  await expect(word).toBeVisible();
  const box = await button.boundingBox();
  const drawn = await word.boundingBox();
  expect((drawn?.y ?? Number.NaN) + (drawn?.height ?? 0) / 2).toBeCloseTo(
    (box?.y ?? Number.NaN) + (box?.height ?? 0) / 2,
    0,
  );
}

test("draws Replace's own word alone, level with the button, beside the stored password", async ({
  page,
}) => {
  // given
  await signedInShell(page, { permissions: OWNER });
  await stubMailServer(page, STORED_PASSWORD);
  await page.goto(NOTIFICATIONS);
  const card = page.getByRole("region", { name: "Mail server" });

  // when
  await card.getByRole("button", { name: "Edit" }).click();

  // then
  await expectOnlyItsOwnWord(card, "Replace", "Keep stored");
});

test("draws Keep stored's own word alone, level with the button, once Replace is pressed", async ({
  page,
}) => {
  // given
  await signedInShell(page, { permissions: OWNER });
  await stubMailServer(page, STORED_PASSWORD);
  await page.goto(NOTIFICATIONS);
  const card = page.getByRole("region", { name: "Mail server" });
  await card.getByRole("button", { name: "Edit" }).click();

  // when
  await card.getByRole("button", { name: "Replace" }).click();

  // then
  await expectOnlyItsOwnWord(card, "Keep stored", "Replace");
});

test.describe("at phone width", () => {
  test.use({
    viewport: { width: 412, height: 892 },
    isMobile: true,
    hasTouch: true,
  });

  test("states a long relay name at rest without a sideways scroll", async ({
    page,
  }) => {
    // given
    await signedInShell(page, { permissions: OWNER });
    await stubMailServer(page, LOCKED_LONG_HOST);

    // when
    await page.goto(NOTIFICATIONS);
    await expect(
      page.getByText(`${LONG_HOST}:1025, No encryption`),
    ).toBeVisible();

    // then
    expect(await overflowing(page)).toEqual([]);
  });

  test("lays the open form out without a sideways scroll", async ({ page }) => {
    // given
    await signedInShell(page, { permissions: OWNER });
    await stubMailServer(page, LOCKED_LONG_HOST);
    await page.goto(NOTIFICATIONS);

    // when
    await page.getByRole("button", { name: "Edit" }).click();
    await expect(page.getByText("AXONIA_MAIL_HOST")).toBeVisible();

    // then
    expect(await overflowing(page)).toEqual([]);
  });
});
