import { render, screen } from "@testing-library/react";
import { Building2, Crown, Gauge, UserRound } from "lucide-react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import {
  organizationSettingsGroups,
  type SettingsGroup,
  visibleGroups,
} from "@/components/patterns/settings-nav";
import { accountSettingsGroups } from "@/components/patterns/settings-nav.account";
import { SettingsOverview } from "@/components/patterns/SettingsOverview";

function renderOrganizationOverview(base = "/settings/organization") {
  const groups = visibleGroups(organizationSettingsGroups("ENTERPRISE", []), {
    can: () => true,
    delegated: false,
  });
  return render(
    <MemoryRouter>
      <SettingsOverview
        title="Organization settings"
        base={base}
        groups={groups}
      />
    </MemoryRouter>,
  );
}

function renderOverview(
  groups: ReadonlyArray<SettingsGroup>,
  base = "/settings/account",
) {
  return render(
    <MemoryRouter>
      <SettingsOverview title="Account settings" base={base} groups={groups} />
    </MemoryRouter>,
  );
}

const PROFILE_ITEM = { path: "profile", label: "Profile", icon: UserRound };
const PLAN_ITEM = { path: "plan", label: "Plan", icon: Crown };

describe("SettingsOverview", () => {
  it("renders every section as a link built from the base", () => {
    renderOrganizationOverview();
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(8);
    expect(screen.getByRole("link", { name: /Users/ })).toHaveAttribute(
      "href",
      "/settings/organization/users",
    );
  });

  it("draws a heading only for header-chrome groups", () => {
    renderOrganizationOverview();
    const headings = screen
      .getAllByRole("heading", { level: 2 })
      .map((heading) => heading.textContent);
    expect(headings).toEqual(["People & access"]);
  });

  it("lists each section by label alone, with no description line", () => {
    // when
    renderOrganizationOverview();

    // then
    const link = screen.getByRole("link", { name: /Data export/ });
    expect(link).toHaveTextContent("Data export");
    expect(link).not.toHaveTextContent(
      "Everything this organization would take with it.",
    );
  });

  it("composes a hub-nested base into every href", () => {
    renderOrganizationOverview("/communities/acme/settings/organization");
    expect(screen.getByRole("link", { name: /Users/ })).toHaveAttribute(
      "href",
      "/communities/acme/settings/organization/users",
    );
  });

  it("omits the group heading for a single-group catalogue but still links every row", () => {
    const groups = visibleGroups(
      accountSettingsGroups({ dataRights: true, agentAccess: true }, []),
      {
        can: () => true,
        delegated: false,
      },
    );
    render(
      <MemoryRouter>
        <SettingsOverview
          title="Account settings"
          base="/settings/account"
          groups={groups}
        />
      </MemoryRouter>,
    );
    expect(screen.queryByRole("heading", { level: 2 })).not.toBeInTheDocument();
    expect(screen.getAllByRole("link")).toHaveLength(4);
  });

  it("draws no heading for a group whose chrome is none", () => {
    render(
      <MemoryRouter>
        <SettingsOverview
          title="Organization settings"
          base="/settings/organization"
          groups={[
            {
              label: "Organization",
              chrome: "none",
              items: [
                {
                  path: "general",
                  label: "General",
                  icon: Building2,
                },
              ],
            },
            {
              label: "Billing",
              items: [
                {
                  path: "usage",
                  label: "Usage",
                  icon: Gauge,
                },
              ],
            },
          ]}
        />
      </MemoryRouter>,
    );
    const headings = screen
      .getAllByRole("heading", { level: 2 })
      .map((heading) => heading.textContent);
    expect(headings).toEqual(["Billing"]);
  });

  it("separates a divider group from the one above it, as the rail does", () => {
    renderOverview([
      { label: "Account", items: [PROFILE_ITEM] },
      { label: "Axonia", chrome: "divider", items: [PLAN_ITEM] },
    ]);

    expect(screen.getByTestId("settings-group-divider")).toBeInTheDocument();
    expect(screen.queryByText("Axonia")).not.toBeInTheDocument();
  });
});
