import type { ReactNode } from "react";
import { Route } from "react-router-dom";

import { ACCOUNT_SETTINGS } from "@/components/patterns/settings-nav";
import type { AccountPage, EditionSlots } from "@/edition";
import { AccountPageRoute } from "@/routes/settings-routes.account-gates";

/** The pages an edition adds to the organization settings, registered beside the open core's. */
export function contributedSectionRoutes(
  orgBase: string,
  sections: EditionSlots["settingsSections"],
): ReactNode {
  return sections.flatMap((section) =>
    Object.entries(section.pages).map(([path, Page]) => (
      <Route key={path} path={`${orgBase}/${path}`} element={<Page />} />
    )),
  );
}

/** The pages an edition adds to the account settings, each behind its own offer. */
export function accountPageRoutes(
  base: string,
  pages: ReadonlyArray<AccountPage>,
): ReactNode {
  return pages.map((page) => (
    <Route
      key={page.item.path}
      path={`${base}/${ACCOUNT_SETTINGS}/${page.item.path}`}
      element={<AccountPageRoute base={base} page={page} />}
    />
  ));
}
