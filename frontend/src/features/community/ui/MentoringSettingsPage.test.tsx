import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Fragment } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { DelegatedSessionProvider } from "@/auth/delegated-session";
import type { ManagementAccess } from "@/features/community/api/use-management-access";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMentoringSettings,
  useUpdateMentoringSettings,
} from "@/features/community/api/use-mentoring-settings";
import { MentoringSettingsPage } from "@/features/community/ui/MentoringSettingsPage";

vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/community/api/use-mentoring-settings", () => ({
  useMentoringSettings: vi.fn(),
  useUpdateMentoringSettings: vi.fn(),
}));
vi.mock("@/features/community/ui/community-chrome", () => ({
  useCommunityCrumbs: vi.fn(),
}));

function access(overrides?: Partial<ManagementAccess>): ManagementAccess {
  return {
    editCommunity: true,
    lookAndFeel: true,
    members: true,
    contributionPolicy: true,
    archive: true,
    managementPolicy: true,
    curation: true,
    health: true,
    invite: true,
    communication: true,
    credit: true,
    any: true,
    member: true,
    isLoading: false,
    ...overrides,
  };
}

const mutate = vi.fn();

function renderPage(enabled = false, delegated = false) {
  vi.mocked(useMentoringSettings).mockReturnValue({
    data: { enabled },
    isPending: false,
  } as never);
  const Session = delegated ? DelegatedSessionProvider : Fragment;
  render(
    <Session>
      <MemoryRouter
        initialEntries={["/communities/backend-guild/settings/mentoring"]}
      >
        <Routes>
          <Route
            path="/communities/:slug/settings/mentoring"
            element={<MentoringSettingsPage />}
          />
        </Routes>
      </MemoryRouter>
    </Session>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useManagementAccess).mockReturnValue(access());
  vi.mocked(useUpdateMentoringSettings).mockReturnValue({
    mutate,
    isPending: false,
    isError: false,
    error: null,
  } as never);
});

describe("MentoringSettingsPage", () => {
  it("shows the switch to an organizer and saves it", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderPage(false);

    // when
    await user.click(screen.getByRole("switch", { name: "Mentoring on" }));

    // then
    expect(mutate).toHaveBeenCalledWith(true);
  });

  it("shows the switch on when mentoring is enabled", () => {
    // when
    renderPage(true);

    // then
    expect(screen.getByRole("switch", { name: "Mentoring on" })).toBeChecked();
  });

  it("explains that pairings carry on when switched off", () => {
    // when
    renderPage();

    // then
    expect(
      screen.getByText(/Pairings already started carry on\./),
    ).toBeInTheDocument();
  });

  it("shows No access to a member", () => {
    // given
    vi.mocked(useManagementAccess).mockReturnValue(
      access({ managementPolicy: false }),
    );

    // when
    renderPage();

    // then
    expect(
      screen.getByText("No access to mentoring settings."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("says when the settings could not load", () => {
    // given
    vi.mocked(useMentoringSettings).mockReturnValue({
      data: undefined,
      isPending: false,
    } as never);

    // when
    render(
      <MemoryRouter initialEntries={["/communities/g/settings/mentoring"]}>
        <Routes>
          <Route
            path="/communities/:slug/settings/mentoring"
            element={<MentoringSettingsPage />}
          />
        </Routes>
      </MemoryRouter>,
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the mentoring settings.",
    );
  });

  it("shows loading while the settings are pending", () => {
    // given
    vi.mocked(useMentoringSettings).mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);

    // when
    render(
      <MemoryRouter initialEntries={["/communities/g/settings/mentoring"]}>
        <Routes>
          <Route
            path="/communities/:slug/settings/mentoring"
            element={<MentoringSettingsPage />}
          />
        </Routes>
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("shows the refusal when the save fails", () => {
    // given
    vi.mocked(useUpdateMentoringSettings).mockReturnValue({
      mutate,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    } as never);

    // when
    renderPage();

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("shows one calm line instead of the switch to a delegated session", () => {
    // when
    renderPage(true, true);

    // then
    expect(
      screen.getByText("Mentoring needs the person’s own sign-in."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("switch", { name: "Mentoring on" }),
    ).not.toBeInTheDocument();
  });
});
