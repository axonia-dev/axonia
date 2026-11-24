import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSetAvailability } from "@/features/mentoring/api/use-mentoring";
import { ApiError } from "@/lib/api-client";
import {
  delegated,
  mutation,
  page,
  pageRoutes,
  quietHooks,
  refused,
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

const ada = {
  userId: "u-1",
  displayName: "Ada",
  avatarUrl: null,
  areas: ["kafka", "testing"],
  proposable: true,
  proposalState: null,
  proposalsOpenOn: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  quietHooks();
});

describe("MentoringPage", () => {
  it("shows availability off for a member who never set it", () => {
    // given
    page({});

    // when
    renderPage();

    // then
    expect(
      screen.getByRole("switch", { name: "Taking mentees" }),
    ).not.toBeChecked();
    expect(
      screen.getByRole("switch", { name: "Looking for a mentor" }),
    ).not.toBeChecked();
    expect(
      screen.queryByRole("group", { name: "Most mentees at once" }),
    ).not.toBeInTheDocument();
  });

  it("saves availability when a switch is flipped", async () => {
    // given
    const set = mutation();
    vi.mocked(useSetAvailability).mockReturnValue(set as never);
    page({});
    renderPage();

    // when
    await userEvent.click(
      screen.getByRole("switch", { name: "Looking for a mentor" }),
    );

    // then
    expect(set.mutate).toHaveBeenCalledWith(
      { takingMentees: false, menteeLimit: null, lookingForMentor: true },
      expect.anything(),
    );
  });

  it("tells a listed viewer that nobody else is listed", () => {
    // given
    page({
      mine: { takingMentees: true, menteeLimit: null, lookingForMentor: true },
    });

    // when
    renderPage();

    // then
    expect(
      screen.getByText("No other mentor is taking mentees right now."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Nobody else is looking for a mentor right now."),
    ).toBeInTheDocument();
  });

  it("shows the guided start on a first visit and the doors after a switch, focus on its switch", async () => {
    // given
    page({ guidedStart: true });
    const view = renderPage();
    expect(
      screen.getByRole("heading", { name: "Start mentoring in three steps" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Taking mentees" }),
    ).not.toBeInTheDocument();

    // when
    await userEvent.click(
      screen.getByRole("switch", { name: "Looking for a mentor" }),
    );
    page({
      guidedStart: true,
      mine: { takingMentees: false, menteeLimit: null, lookingForMentor: true },
    });
    view.rerender(pageRoutes());

    // then
    expect(
      screen.queryByRole("heading", { name: "Start mentoring in three steps" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Looking for a mentor" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: "Looking for a mentor" }),
    ).toHaveFocus();
  });

  it("tells a non-member, refused with 403, that mentoring is for members", () => {
    // given
    refused(new ApiError(403, "Forbidden"));

    // when
    renderPage();

    // then
    expect(
      screen.getByText("Mentoring is open to members of this community."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("tells a visitor to a switched-off guild that mentoring is off", () => {
    // given
    refused(new ApiError(409, "Conflict", "MENTORING_OFF"));

    // when
    renderPage();

    // then
    expect(
      screen.getByText("Mentoring is off in this community."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("still reports any other failure as a load error", () => {
    // given
    refused(new ApiError(500, "Server error"));

    // when
    renderPage();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load mentoring.",
    );
  });

  it("an archived community offers no availability switches", () => {
    // given
    page({ archived: true });

    // when
    renderPage();

    // then
    expect(
      screen.getByRole("switch", { name: "Taking mentees" }),
    ).toBeDisabled();
    expect(screen.getByText("This community is archived.")).toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: /Who’s helping whom/ }),
    ).toBeVisible();
  });

  it("a delegated session sees one calm line and no directory", () => {
    // given
    delegated(true);
    page({ mentors: [ada] });

    // when
    renderPage();

    // then
    expect(
      screen.getByText("Mentoring needs the person’s own sign-in."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Ada")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("switch", { name: "Taking mentees" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
  });
});
