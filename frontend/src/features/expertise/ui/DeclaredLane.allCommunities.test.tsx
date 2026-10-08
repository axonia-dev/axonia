import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import {
  useDeclareArea,
  useRemoveDeclaration,
} from "@/features/expertise/api/use-declarations";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import { useUnhideTag } from "@/features/expertise/api/use-expertise-prefs";
import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import type { DeclarableCommunity } from "@/features/expertise/domain/expertise";
import { DeclaredLane } from "@/features/expertise/ui/DeclaredLane";
import { ApiError } from "@/lib/api-client";

vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-declarations", () => ({
  useDeclareArea: vi.fn(),
  useRemoveDeclaration: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useUnhideTag: vi.fn(),
}));

const DATA: DeclarableCommunity = { id: 2, slug: "data", name: "Data" };
const PLATFORM: DeclarableCommunity = {
  id: 1,
  slug: "platform",
  name: "Platform",
};

let user: ReturnType<typeof userEvent.setup>;
let mutate: ReturnType<typeof vi.fn>;

function declaring(state: { isError: boolean; error: unknown }) {
  vi.mocked(useDeclareArea).mockReturnValue({
    mutate,
    reset: vi.fn(),
    isPending: false,
    ...state,
  } as never);
}

function lane(communityId: number, declarable: DeclarableCommunity[]) {
  return (
    <DeclaredLane
      declarations={[]}
      communityId={communityId}
      declarableCommunities={declarable}
    />
  );
}

function renderLane(communityId: number, declarable: DeclarableCommunity[]) {
  return render(lane(communityId, declarable));
}

function picker() {
  return screen.getByRole("button", { name: /Community to declare in/ });
}

async function choose(name: string) {
  await user.click(picker());
  await user.click(screen.getByRole("option", { name }));
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mutate = vi.fn();
  declaring({ isError: false, error: null });
  vi.mocked(useExpertiseAreas).mockReturnValue({ data: [] } as never);
  vi.mocked(useRemoveDeclaration).mockReturnValue({
    mutate: vi.fn(),
    isRemoving: () => false,
  } as never);
  vi.mocked(useUnhideTag).mockReturnValue({ mutate: vi.fn() } as never);
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("DeclaredLane across all communities", () => {
  it("offers the field with the first declarable community chosen", () => {
    // when
    renderLane(ALL_COMMUNITIES, [DATA, PLATFORM]);

    // then
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(picker()).toHaveTextContent("Data");
    expect(screen.getByRole("combobox")).toBeInTheDocument();
    expect(useDeclareArea).toHaveBeenLastCalledWith("data");
    expect(useExpertiseAreas).toHaveBeenLastCalledWith("data");
  });

  it("declares in the chosen community", async () => {
    // given
    renderLane(ALL_COMMUNITIES, [DATA, PLATFORM]);
    await choose("Platform");
    await user.type(screen.getByRole("combobox"), "terraform");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(useDeclareArea).toHaveBeenLastCalledWith("platform");
    expect(useExpertiseAreas).toHaveBeenLastCalledWith("platform");
    expect(mutate.mock.calls[0]?.[0]).toBe("terraform");
  });

  it("narrows the choice to the lens's community", async () => {
    // given
    renderLane(PLATFORM.id, [DATA, PLATFORM]);
    expect(picker()).toHaveTextContent("Platform");
    expect(useDeclareArea).toHaveBeenLastCalledWith("platform");

    // when
    await user.click(picker());

    // then
    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual([
      "Platform",
    ]);
  });

  it("offers no field while the lens shows a community with no declaring", () => {
    // when
    const { container } = renderLane(3, [DATA, PLATFORM]);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing with no declarable community and no declaration", () => {
    // when
    const { container } = renderLane(ALL_COMMUNITIES, []);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("offers neither the field nor the choice in a delegated session", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);

    // when
    const { container } = renderLane(ALL_COMMUNITIES, [DATA, PLATFORM]);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("keeps the typed text when the community changes", async () => {
    // given
    renderLane(ALL_COMMUNITIES, [DATA, PLATFORM]);
    await user.type(screen.getByRole("combobox"), "terraform");

    // when
    await choose("Platform");

    // then
    expect(screen.getByRole("combobox")).toHaveValue("terraform");
  });

  it("shows a refusal only in the community it was sent to", async () => {
    // given
    const refusal = "At most 20 areas per community.";
    const view = renderLane(ALL_COMMUNITIES, [DATA, PLATFORM]);
    await user.type(screen.getByRole("combobox"), "kafka");
    await user.click(screen.getByRole("button", { name: "Add" }));
    await choose("Platform");

    // when
    declaring({
      isError: true,
      error: new ApiError(409, "refused", "DECLARATION_LIMIT"),
    });
    view.rerender(lane(ALL_COMMUNITIES, [DATA, PLATFORM]));

    // then
    expect(screen.queryByText(refusal)).not.toBeInTheDocument();
    await choose("Data");
    expect(screen.getByText(refusal)).toBeInTheDocument();
  });

  it("follows the lens once it shows another declarable community", async () => {
    // given
    const view = renderLane(ALL_COMMUNITIES, [DATA, PLATFORM]);
    await choose("Platform");

    // when
    view.rerender(lane(DATA.id, [DATA, PLATFORM]));

    // then
    expect(picker()).toHaveTextContent("Data");
    expect(useDeclareArea).toHaveBeenLastCalledWith("data");
  });
});
