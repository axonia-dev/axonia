import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import { useUnhideTag } from "@/features/expertise/api/use-expertise-prefs";
import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import type { SelfDeclaration } from "@/features/expertise/domain/expertise";
import { DeclaredLane } from "@/features/expertise/ui/DeclaredLane";

vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-declarations", () => ({
  useRemoveDeclaration: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useUnhideTag: vi.fn(),
}));
vi.mock("@/features/expertise/ui/AddDeclarationField", () => ({
  AddDeclarationField: ({ slug }: { slug: string }) => (
    <p>add field for {slug}</p>
  ),
}));

const mockUseRemoveDeclaration = vi.mocked(useRemoveDeclaration);

const TERRAFORM: SelfDeclaration = {
  tag: "terraform",
  communityId: 1,
  communityName: "Platform",
  communitySlug: "platform",
  hidden: false,
  declaredAt: "2026-09-12T12:00:00Z",
};

const PLATFORM = { id: 1, slug: "platform", name: "Platform" };

const SPARK: SelfDeclaration = {
  tag: "spark",
  communityId: 2,
  communityName: "Data",
  communitySlug: "data",
  hidden: true,
  declaredAt: "2026-09-01T12:00:00Z",
};

let user: ReturnType<typeof userEvent.setup>;
let mutate: ReturnType<typeof vi.fn>;
let unhide: ReturnType<typeof vi.fn>;

function removing(tag: string | null) {
  mockUseRemoveDeclaration.mockReturnValue({
    mutate,
    isRemoving: (ref: { tag: string }) => ref.tag === tag,
  } as never);
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mutate = vi.fn();
  unhide = vi.fn();
  removing(null);
  vi.mocked(useUnhideTag).mockReturnValue({ mutate: unhide } as never);
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("DeclaredLane", () => {
  it("lists every community's declarations with the community named", () => {
    // when
    render(
      <DeclaredLane
        declarations={[TERRAFORM, SPARK]}
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // then
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(screen.getByText("· 2")).toBeInTheDocument();
    expect(
      screen.getByText("Declared, not yet shown in activity."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Platform · Declared 12 Sep 2026"),
    ).toBeInTheDocument();
    expect(screen.getByText("Data · Declared 1 Sep 2026")).toBeInTheDocument();
    expect(screen.queryByText(/add field/)).not.toBeInTheDocument();
  });

  it("carries the add field on a community's page, without naming the community", () => {
    // when
    render(
      <DeclaredLane
        declarations={[TERRAFORM]}
        slug="platform"
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[PLATFORM]}
      />,
    );

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.getByText("Declared 12 Sep 2026")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Remove terraform" }),
    ).toBeInTheDocument();
    expect(screen.getByText("add field for platform")).toBeInTheDocument();
  });

  it("removes a declaration in its own community, named in the control", async () => {
    // given
    render(
      <DeclaredLane
        declarations={[TERRAFORM, SPARK]}
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // when
    await user.click(
      screen.getByRole("button", { name: "Remove spark from Data" }),
    );

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({ slug: "data", tag: "spark" });
  });

  it("lists declarations with no add field and no removal in a delegated session", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);

    // when
    render(
      <DeclaredLane
        declarations={[TERRAFORM]}
        slug="platform"
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[PLATFORM]}
      />,
    );

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByText(/add field/)).not.toBeInTheDocument();
  });

  it("holds only the Remove of the declaration being removed", async () => {
    // given
    removing("spark");
    render(
      <DeclaredLane
        declarations={[TERRAFORM, SPARK]}
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // when
    await user.click(
      screen.getByRole("button", { name: "Remove terraform from Platform" }),
    );

    // then
    expect(
      screen.getByRole("button", { name: "Remove spark from Data" }),
    ).toHaveAttribute("data-pending");
    expect(mutate).toHaveBeenCalledWith({ slug: "platform", tag: "terraform" });
  });

  it("says a hidden declaration is hidden", () => {
    // when
    render(
      <DeclaredLane
        declarations={[SPARK]}
        slug="data"
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // then
    expect(screen.getByText("· hidden from others")).toBeInTheDocument();
  });

  it("unhides a hidden declaration from its row", async () => {
    // given
    render(
      <DeclaredLane
        declarations={[TERRAFORM, SPARK]}
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // when
    await user.click(
      screen.getByRole("button", { name: "Show spark in Data" }),
    );

    // then
    expect(unhide).toHaveBeenCalledWith("spark");
    expect(
      screen.queryByRole("button", { name: /(Show|Hide) terraform/ }),
    ).not.toBeInTheDocument();
  });

  it("renders nothing with no declaration and no add field", () => {
    // when
    const { container } = render(
      <DeclaredLane
        declarations={[]}
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("keeps only a heading and the add field on a community's page with no declaration", () => {
    // when
    render(
      <DeclaredLane
        declarations={[]}
        slug="platform"
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[PLATFORM]}
      />,
    );

    // then
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(screen.getByText("add field for platform")).toBeInTheDocument();
    expect(screen.queryByText(/· \d/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Declared/)).not.toBeInTheDocument();
  });

  it("withholds adding on a community's page the member cannot declare in", () => {
    // when
    const { container } = render(
      <DeclaredLane
        declarations={[]}
        slug="platform"
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("lists a community's declarations with no add field where the member cannot declare", () => {
    // when
    render(
      <DeclaredLane
        declarations={[TERRAFORM]}
        slug="platform"
        communityId={ALL_COMMUNITIES}
        declarableCommunities={[]}
      />,
    );

    // then
    expect(screen.getByText("terraform")).toBeInTheDocument();
    expect(screen.queryByText(/add field/)).not.toBeInTheDocument();
  });
});
