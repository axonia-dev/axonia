import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { shownPairingOf } from "@/features/mentoring/test-data";
import {
  MENTORING_PATH,
  page,
  quietHooks,
  renderPage,
} from "@/test/mentoring-page-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useMentoringPage: vi.fn(),
  useSetAvailability: vi.fn(),
  usePropose: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  quietHooks();
});

describe("the Mentoring page tabs", () => {
  it("opens on Find with the lists and no pairing rows", () => {
    // given
    page({ pairings: [shownPairingOf()] });

    // when
    renderPage();

    // then
    expect(screen.getByRole("tab", { name: "Find" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("heading", { name: "Mentees" })).toBeVisible();
    expect(screen.queryByText("Ana Keller")).not.toBeInTheDocument();
  });

  it("counts shown pairings on the second tab", () => {
    // given
    page({
      pairings: [
        shownPairingOf(),
        shownPairingOf({
          mentor: { userId: "u-6", displayName: "Carl Brun", avatarUrl: null },
        }),
      ],
    });

    // when
    renderPage();

    // then
    expect(
      screen.getByRole("tab", { name: "Who’s helping whom 2" }),
    ).toBeVisible();
  });

  it.each([
    ["the doors", false],
    ["the guided start", true],
  ])("shows the pairs on their tab, under %s", async (_, guided) => {
    // given
    const user = userEvent.setup({ delay: null });
    page({ guidedStart: guided, pairings: [shownPairingOf()] });
    renderPage();

    // when
    await user.click(screen.getByRole("tab", { name: /Who’s helping whom/ }));

    // then
    expect(screen.getByText("Ana Keller")).toBeVisible();
    expect(
      screen.queryByRole("heading", { name: "Mentees" }),
    ).not.toBeInTheDocument();
  });

  it("opens the pairs tab from ?view=pairs", () => {
    // given
    page({ pairings: [shownPairingOf()] });

    // when
    renderPage(`${MENTORING_PATH}?view=pairs`);

    // then
    expect(
      screen.getByRole("tab", { name: /Who’s helping whom/ }),
    ).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Ana Keller")).toBeVisible();
  });

  it("opens Find for an unknown view", () => {
    // given
    page({ pairings: [shownPairingOf()] });

    // when
    renderPage(`${MENTORING_PATH}?view=garbage`);

    // then
    expect(screen.getByRole("tab", { name: "Find" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("shows the empty pairs tab with no count", () => {
    // given
    page({ pairings: [] });

    // when
    renderPage(`${MENTORING_PATH}?view=pairs`);

    // then
    expect(
      screen.getByRole("tab", { name: "Who’s helping whom" }),
    ).toBeVisible();
    expect(screen.getByText("No ongoing pairing.")).toBeVisible();
  });
});
