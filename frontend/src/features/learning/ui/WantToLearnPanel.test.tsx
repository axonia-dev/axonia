import { render as renderInDom, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useRemoveIntent } from "@/features/learning/api/use-learning";
import type { LearningPage } from "@/features/learning/domain/learning";
import { WantToLearnPanel } from "@/features/learning/ui/WantToLearnPanel";

vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: vi.fn(),
}));
vi.mock("@/features/learning/api/use-learning", () => ({
  useRemoveIntent: vi.fn(),
}));
vi.mock("@/features/learning/ui/AddIntentField", () => ({
  AddIntentField: ({ slug, held }: { slug: string; held: string[] }) => (
    <p>
      add field for {slug} holding [{held.join(",")}]
    </p>
  ),
}));

const PAGE: LearningPage = {
  member: true,
  archived: false,
  limit: 10,
  mine: [
    {
      tag: "kafka",
      declaredAt: "2026-03-02T09:00:00Z",
      open: [
        { userId: "lea", displayName: "Lea Moser", avatarUrl: null },
        { userId: "tom", displayName: null, avatarUrl: "/tom.png" },
      ],
      openCount: 7,
    },
    {
      tag: "rust",
      declaredAt: "2026-09-12T09:00:00Z",
      open: [],
      openCount: 0,
    },
  ],
  wanted: [],
};

let mutate: ReturnType<typeof vi.fn>;

function render(ui: ReactElement) {
  return renderInDom(<MemoryRouter>{ui}</MemoryRouter>);
}

function removing(...tags: string[]) {
  vi.mocked(useRemoveIntent).mockReturnValue({
    mutate,
    isRemoving: (tag: string) => tags.includes(tag),
  } as never);
}

beforeEach(() => {
  vi.clearAllMocks();
  mutate = vi.fn();
  removing();
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("WantToLearnPanel", () => {
  it("removeStillWorks", async () => {
    // given
    render(<WantToLearnPanel slug="platform" page={PAGE} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove rust" }));

    // then
    expect(vi.mocked(useRemoveIntent)).toHaveBeenCalledWith("platform");
    expect(mutate).toHaveBeenCalledWith("rust");
  });

  it("marks only the area being removed as pending", () => {
    // given
    removing("kafka");

    // when
    render(<WantToLearnPanel slug="platform" page={PAGE} />);

    // then
    expect(
      screen.getByRole("button", { name: "Remove kafka" }),
    ).toHaveAttribute("data-pending");
    expect(
      screen.getByRole("button", { name: "Remove rust" }),
    ).not.toHaveAttribute("data-pending");
  });

  it("marks every area whose removal is in flight", () => {
    // given
    removing("kafka", "rust");

    // when
    render(<WantToLearnPanel slug="platform" page={PAGE} />);

    // then
    expect(
      screen.getByRole("button", { name: "Remove kafka" }),
    ).toHaveAttribute("data-pending");
    expect(screen.getByRole("button", { name: "Remove rust" })).toHaveAttribute(
      "data-pending",
    );
  });

  it("offers the add field with the areas already held", () => {
    // when
    render(<WantToLearnPanel slug="platform" page={PAGE} />);

    // then
    expect(
      screen.getByText("add field for platform holding [kafka,rust]"),
    ).toBeInTheDocument();
  });

  it("offers only the add field to a member with no area yet", () => {
    // when
    render(<WantToLearnPanel slug="platform" page={{ ...PAGE, mine: [] }} />);

    // then
    expect(screen.getByText("0 of 10")).toBeInTheDocument();
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
    expect(
      screen.getByText("add field for platform holding []"),
    ).toBeInTheDocument();
  });

  it("rowShowsOpenFacesAndCount", () => {
    // when
    render(<WantToLearnPanel slug="platform" page={PAGE} />);

    // then
    const row = screen.getByRole("listitem", { name: "kafka" });
    expect(within(row).getByText("7 open to questions")).toBeInTheDocument();
    expect(within(row).getByText("since Mar 2026")).toBeInTheDocument();
    expect(row.querySelectorAll("[data-open='true']")).toHaveLength(2);
    expect(within(row).queryByTestId("open-dot")).not.toBeInTheDocument();
  });

  it("rowWithNobodyOpenSaysSo", () => {
    // when
    render(<WantToLearnPanel slug="platform" page={PAGE} />);

    // then
    const row = screen.getByRole("listitem", { name: "rust" });
    expect(within(row).getByText("Nobody open yet")).toHaveClass("italic");
    expect(within(row).queryByRole("link")).not.toBeInTheDocument();
  });

  it("facesLinkToTheOpenSearch", () => {
    // when
    render(<WantToLearnPanel slug="platform" page={PAGE} />);

    // then
    expect(
      screen.getByRole("link", {
        name: "See who is open to questions on kafka",
      }),
    ).toHaveAttribute(
      "href",
      "/communities/platform/expertise?term=kafka&who=open",
    );
  });
});
