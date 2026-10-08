import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSelfView } from "@/features/expertise/api/use-self-view";
import {
  DATA,
  onPage,
  renderView,
  stubHooks,
} from "@/features/expertise/ui/my-expertise-view-fixture";

vi.mock("@/features/expertise/api/use-self-view", () => ({
  useSelfView: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useSetOptOut: vi.fn(),
  useHideTag: vi.fn(),
  useUnhideTag: vi.fn(),
  useDismissSignal: vi.fn(),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useParams: vi.fn(() => ({})),
}));
vi.mock("@/features/expertise/api/use-declarations", () => ({
  useRemoveDeclaration: vi.fn(),
}));
vi.mock("@/features/expertise/ui/ShapeSection", () => ({
  ShapeSection: () => null,
}));

const refetch = vi.fn();

function givenFailedRead() {
  vi.mocked(useSelfView).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: true,
    errorUpdateCount: 1,
    isFetching: false,
    refetch,
  } as never);
}

beforeEach(() => {
  vi.clearAllMocks();
  stubHooks();
});

describe("MyExpertiseView reading the self view", () => {
  it("says the read failed on a community's page instead of rendering nothing", () => {
    // given
    onPage(DATA);
    givenFailedRead();

    // when
    renderView();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load these expertise areas.",
    );
  });

  it("says the read failed on the page across communities", () => {
    // given
    givenFailedRead();

    // when
    renderView();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load these expertise areas.",
    );
  });

  it("keeps the page heading and claims nothing about the areas", () => {
    // given
    givenFailedRead();

    // when
    renderView();

    // then
    expect(screen.getByText("My expertise")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1 })).toBeInTheDocument();
    expect(
      screen.queryByText("Not yet represented in the expertise graph."),
    ).not.toBeInTheDocument();
  });

  it("asks again when told to", async () => {
    // given
    givenFailedRead();
    renderView();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("keeps showing a view it already read when a later refetch fails", () => {
    // given
    vi.mocked(useSelfView).mockReturnValue({
      data: {
        optedOut: false,
        areas: [],
        declarations: [],
        declarableCommunities: [],
      },
      isLoading: false,
      isError: true,
    } as never);

    // when
    renderView();

    // then
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("No expertise areas yet")).toBeInTheDocument();
  });
});
