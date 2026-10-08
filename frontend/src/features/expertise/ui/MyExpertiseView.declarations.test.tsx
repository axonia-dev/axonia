import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import {
  area,
  DATA,
  declaration,
  ENGINEERING,
  givenSelfView,
  onPage,
  removal,
  renderView,
  stubHooks,
} from "@/features/expertise/ui/my-expertise-view-fixture";

vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: vi.fn(),
}));
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
vi.mock("@/features/expertise/ui/AddDeclarationField", () => ({
  AddDeclarationField: ({ slug }: { slug: string }) => (
    <p>add field for {slug}</p>
  ),
}));
vi.mock("@/features/expertise/ui/ShapeSection", () => ({
  ShapeSection: () => null,
}));

const TYPESCRIPT = area("typescript", [ENGINEERING]);
const PYTHON = area("python", [DATA]);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stubHooks();
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("MyExpertiseView with declarations", () => {
  it("shows declared areas even with no evidence, with no empty state", () => {
    // given
    givenSelfView([], [declaration("terraform", DATA)]);

    // when
    renderView();

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(
      screen.queryByText("No expertise areas yet"),
    ).not.toBeInTheDocument();
  });

  it("offers the add field on a community's page, even before any evidence", () => {
    // given
    onPage(DATA);
    givenSelfView([], [], [DATA]);

    // when
    renderView();

    // then
    expect(screen.getByText("add field for data")).toBeInTheDocument();
    expect(screen.queryByText(/No expertise areas/)).not.toBeInTheDocument();
  });

  it("keeps the empty state on the global page with nothing at all", () => {
    // given
    givenSelfView([]);

    // when
    renderView();

    // then
    expect(screen.getByText("No expertise areas yet")).toBeInTheDocument();
    expect(screen.queryByText(/add field/)).not.toBeInTheDocument();
  });

  it("offers adding on the global page to a member of a community, with no empty state", () => {
    // given
    givenSelfView([], [], [DATA]);

    // when
    renderView();

    // then
    expect(screen.getByText("add field for data")).toBeInTheDocument();
    expect(
      screen.queryByText("No expertise areas yet"),
    ).not.toBeInTheDocument();
  });

  it("keeps the empty state on the global page in a delegated session", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);
    givenSelfView([], [], [DATA]);

    // when
    renderView();

    // then
    expect(screen.getByText("No expertise areas yet")).toBeInTheDocument();
    expect(screen.queryByText(/add field/)).not.toBeInTheDocument();
  });

  it("offers no add field on a community's page the member cannot declare in, only the empty state", () => {
    // given
    onPage(DATA);
    givenSelfView([], [], []);

    // when
    renderView();

    // then
    expect(
      screen.getByText("No expertise areas in this community yet"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Areas appear after a talk, a contribution, an offered topic, or a declaration in this community.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/add field/)).not.toBeInTheDocument();
  });

  it("keeps the empty state on a community's page in a delegated session", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);
    onPage(DATA);
    givenSelfView([], [], [DATA]);

    // when
    renderView();

    // then
    expect(
      screen.getByText("No expertise areas in this community yet"),
    ).toBeInTheDocument();
  });

  it("scopes the lane to the page's community while the header counts the whole profile", () => {
    // given
    onPage(DATA);
    givenSelfView(
      [TYPESCRIPT],
      [declaration("rust", ENGINEERING), declaration("terraform", DATA)],
    );

    // when
    renderView();

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.queryByText("rust")).not.toBeInTheDocument();
    expect(screen.getByText("2 Open to questions")).toBeInTheDocument();
  });

  it("removes the declaration of an evidenced area from the community's page", async () => {
    // given
    const mutate = vi.fn();
    vi.mocked(useRemoveDeclaration).mockReturnValue(removal(mutate));
    onPage(ENGINEERING);
    givenSelfView([TYPESCRIPT], [declaration("typescript", ENGINEERING)]);
    renderView();

    // when
    await user.click(
      screen.getByRole("button", {
        name: "Stop being open to questions on typescript",
      }),
    );

    // then
    expect(mutate).toHaveBeenCalledWith({
      slug: "engineering",
      tag: "typescript",
    });
  });

  it("holds a marker's removal while that declaration is being removed", () => {
    // given
    vi.mocked(useRemoveDeclaration).mockReturnValue({
      mutate: vi.fn(),
      isRemoving: (ref: { slug: string; tag: string }) =>
        ref.slug === "engineering" && ref.tag === "typescript",
    } as never);
    onPage(ENGINEERING);
    givenSelfView([TYPESCRIPT], [declaration("typescript", ENGINEERING)]);

    // when
    renderView();

    // then
    expect(
      screen.getByRole("button", {
        name: "Stop being open to questions on typescript",
      }),
    ).toHaveAttribute("data-pending");
  });

  it("marks an evidenced declaration without a removal in a delegated session", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);
    onPage(ENGINEERING);
    givenSelfView([TYPESCRIPT], [declaration("typescript", ENGINEERING)]);

    // when
    renderView();

    // then
    expect(screen.getAllByText("Open to questions").length).toBeGreaterThan(0);
    expect(
      screen.queryByRole("button", { name: /Stop being open to questions/ }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/add field/)).not.toBeInTheDocument();
  });

  it("marks an evidenced declaration without a removal on the global page", () => {
    // given
    givenSelfView([TYPESCRIPT], [declaration("typescript", ENGINEERING)]);

    // when
    renderView();

    // then
    expect(screen.getAllByText("Open to questions")).toHaveLength(1);
    expect(
      screen.queryByRole("button", { name: /Stop being open to questions/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Declared, not yet shown/),
    ).not.toBeInTheDocument();
  });

  it("keeps adding on a community's page, which has no lens to point elsewhere", () => {
    // given
    onPage(ENGINEERING);
    givenSelfView([TYPESCRIPT, PYTHON], [], [ENGINEERING]);

    // when
    renderView();

    // then
    expect(screen.getByText("add field for engineering")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /community/i }),
    ).not.toBeInTheDocument();
  });

  it("resets the lens once the toolbar goes, so every community is offered again", async () => {
    // given
    givenSelfView([TYPESCRIPT, PYTHON], [], [ENGINEERING, DATA]);
    const view = renderView();
    await user.click(screen.getByRole("button", { name: /community/i }));
    await user.click(screen.getByRole("option", { name: "Data" }));
    expect(screen.getByText("add field for data")).toBeInTheDocument();

    // when
    givenSelfView([], [], [ENGINEERING, DATA]);
    view.rerender();

    // then
    expect(screen.getByText("add field for engineering")).toBeInTheDocument();
  });
});
