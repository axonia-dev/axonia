import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  area,
  DATA,
  declaration,
  ENGINEERING,
  givenSelfView,
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

async function selectCommunity(name: string) {
  await user.click(screen.getByRole("button", { name: /community/i }));
  await user.click(screen.getByRole("option", { name }));
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stubHooks();
});

describe("MyExpertiseView declarations under the toolbar", () => {
  it("narrows the lane to the selected community, the header staying whole-profile", async () => {
    // given
    givenSelfView(
      [TYPESCRIPT, PYTHON],
      [
        declaration("rust", ENGINEERING),
        declaration("go", ENGINEERING),
        declaration("terraform", DATA),
      ],
    );
    renderView();

    // when
    await selectCommunity("Data");

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.queryByText("rust")).not.toBeInTheDocument();
    expect(screen.queryByText("go")).not.toBeInTheDocument();
    expect(screen.getByText("3 Open to questions")).toBeInTheDocument();
  });

  it("offers no community lens on a community's own page", () => {
    // given
    onPage(ENGINEERING);
    givenSelfView([TYPESCRIPT, PYTHON]);

    // when
    renderView();

    // then
    expect(
      screen.getByRole("searchbox", { name: /search expertise areas/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /community/i }),
    ).not.toBeInTheDocument();
  });

  it("offers a community only a declaration comes from in the lens", async () => {
    // given
    givenSelfView([TYPESCRIPT], [declaration("terraform", DATA)]);
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: /community/i }));

    // then
    expect(screen.getByRole("option", { name: "Data" })).toBeInTheDocument();
  });

  it("narrows the open-to-questions markers to the selected community", async () => {
    // given
    givenSelfView(
      [area("typescript", [ENGINEERING, DATA])],
      [declaration("typescript", ENGINEERING)],
    );
    renderView();
    expect(screen.getByText("Open to questions")).toBeInTheDocument();

    // when
    await selectCommunity("Data");

    // then
    expect(screen.getByText("typescript")).toBeInTheDocument();
    expect(screen.queryByText("Open to questions")).not.toBeInTheDocument();
  });

  it("matches the search text against declared tags", async () => {
    // given
    givenSelfView(
      [TYPESCRIPT],
      [declaration("terraform", DATA), declaration("rust", DATA)],
    );
    renderView();

    // when
    await user.type(
      screen.getByRole("searchbox", { name: /search expertise/i }),
      "terra",
    );

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.queryByText("rust")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/no areas match these filters/i),
    ).not.toBeInTheDocument();
  });

  it("drops declaration-only rows under Has talks", async () => {
    // given
    givenSelfView([TYPESCRIPT], [declaration("terraform", DATA)]);
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: /has talks/i }));

    // then
    expect(screen.getByText("typescript")).toBeInTheDocument();
    expect(screen.queryByText("terraform")).not.toBeInTheDocument();
  });

  it("drops declaration-only rows under Has artifacts, and says nothing matches", async () => {
    // given
    givenSelfView([TYPESCRIPT], [declaration("terraform", DATA)]);
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: /has artifacts/i }));

    // then
    expect(screen.queryByText("terraform")).not.toBeInTheDocument();
    expect(
      screen.getByText(/no areas match these filters/i),
    ).toBeInTheDocument();
  });

  it("keeps hidden declared tags under Hidden only", async () => {
    // given
    givenSelfView(
      [TYPESCRIPT],
      [
        declaration("terraform", DATA, { hidden: true }),
        declaration("rust", DATA),
      ],
    );
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: /hidden only/i }));

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.queryByText("rust")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/no areas match these filters/i),
    ).not.toBeInTheDocument();
  });

  it("counts a hidden tag only a declaration carries in the visibility control", () => {
    // given
    givenSelfView(
      [{ ...TYPESCRIPT, hidden: true }],
      [declaration("terraform", DATA, { hidden: true })],
    );

    // when
    renderView();

    // then
    expect(
      screen.getByRole("button", { name: /visible · 2 hidden/i }),
    ).toBeInTheDocument();
  });

  it("keeps the toolbar and the lens once the last evidence is gone while declarations remain", async () => {
    // given
    givenSelfView(
      [TYPESCRIPT, PYTHON],
      [declaration("terraform", DATA), declaration("rust", ENGINEERING)],
    );
    const view = renderView();
    await selectCommunity("Data");

    // when
    givenSelfView(
      [],
      [declaration("terraform", DATA), declaration("rust", ENGINEERING)],
    );
    view.rerender();

    // then
    expect(
      screen.getByRole("searchbox", { name: /search expertise/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.queryByText("rust")).not.toBeInTheDocument();
  });
});
