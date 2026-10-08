import type { Page } from "@playwright/test";

import { type ShellData, stubShell } from "../api";
import { signedIn } from "../session";

const EMPTY_PAGE = {
  content: [],
  totalElements: 0,
  totalPages: 1,
  number: 0,
  size: 50,
};

/** Signed in the way every installation signs in: the company's identity provider issues the
 *  token, and it names no organization. The backend supplies the installation's one. */
export async function signedInShell(
  page: Page,
  shell: Partial<ShellData> = {},
): Promise<void> {
  await signedIn(page, { org: undefined });
  await page.route("**/axonia/**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(EMPTY_PAGE),
    }),
  );
  // Registered last so it answers the shell's own calls first.
  await stubShell(page, shell);
}
