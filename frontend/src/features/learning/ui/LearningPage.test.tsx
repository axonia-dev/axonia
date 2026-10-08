import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useLearningPage } from "@/features/learning/api/use-learning";
import type { HeldArea } from "@/features/learning/domain/learning";
import {
  delegated,
  page,
  quietHooks,
  renderPage,
} from "@/test/learning-page-view";

vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));
vi.mock("@/features/learning/api/use-learning", () => ({
  useLearningPage: vi.fn(),
  useLearners: vi.fn(),
  useAddIntent: vi.fn(),
  useRemoveIntent: vi.fn(),
}));
function tenAreas(): HeldArea[] {
  return Array.from({ length: 10 }, (_, at) => ({
    tag: `area-${at}`,
    declaredAt: "2026-03-02T09:00:00Z",
  }));
}

beforeEach(() => {
  vi.clearAllMocks();
  quietHooks();
});

describe("LearningPage", () => {
  it("lists the member's areas with their month and a count against the cap", () => {
    // given
    page({
      member: true,
      mine: [{ tag: "kubernetes", declaredAt: "2026-03-02T09:00:00Z" }],
    });

    // when
    renderPage();

    // then
    expect(screen.getByText("1 of 10")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Remove kubernetes" }),
    ).toBeInTheDocument();
    expect(screen.getByText("since Mar 2026")).toBeInTheDocument();
  });

  it("hides the add field at the cap", () => {
    // given
    page({ member: true, mine: tenAreas() });

    // when
    renderPage();

    // then
    expect(
      screen.queryByRole("combobox", { name: "Add an area to learn" }),
    ).not.toBeInTheDocument();
  });

  it("shows no panel of their own to a non-member", () => {
    // given
    page({ member: false });

    // when
    renderPage();

    // then
    expect(
      screen.queryByRole("heading", { name: "Want to learn" }),
    ).not.toBeInTheDocument();
  });

  it("keeps removal but not adding in an archived community", () => {
    // given
    page({
      member: true,
      archived: true,
      mine: [{ tag: "kafka", declaredAt: "2026-03-02T09:00:00Z" }],
    });

    // when
    renderPage();

    // then
    expect(
      screen.queryByRole("combobox", { name: "Add an area to learn" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove kafka" })).toBeEnabled();
  });

  it("disables every control in a delegated session", () => {
    // given
    delegated(true);
    page({
      member: true,
      mine: [{ tag: "kafka", declaredAt: "2026-03-02T09:00:00Z" }],
    });

    // when
    renderPage();

    // then
    expect(screen.getByRole("button", { name: "Remove kafka" })).toBeDisabled();
    expect(
      screen.queryByRole("combobox", { name: "Add an area to learn" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText("Areas to learn need the person’s own sign-in."),
    ).toBeInTheDocument();
  });

  it("says so while the page loads", () => {
    // given
    vi.mocked(useLearningPage).mockReturnValue({
      isLoading: true,
      isError: false,
      data: undefined,
    } as never);

    // when
    renderPage();

    // then
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("says so when the page cannot load", () => {
    // given
    vi.mocked(useLearningPage).mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
    } as never);

    // when
    renderPage();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load learning.",
    );
  });
});
